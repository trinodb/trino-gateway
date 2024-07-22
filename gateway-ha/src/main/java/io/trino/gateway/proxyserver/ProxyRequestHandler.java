/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.proxyserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.OAuth2GatewayCookie;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.HttpHeaders.HOST;
import static com.google.common.net.HttpHeaders.VIA;
import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.google.common.net.HttpHeaders.X_FORWARDED_HOST;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PORT;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.jaxrs.AsyncResponseHandler.bindAsyncResponse;
import static io.trino.gateway.ha.handler.ProxyUtils.AUTHORIZATION;
import static io.trino.gateway.ha.handler.ProxyUtils.QUERY_TEXT_LENGTH_FOR_HISTORY;
import static io.trino.gateway.ha.handler.ProxyUtils.SOURCE_HEADER;
import static io.trino.gateway.ha.handler.ProxyUtils.getQueryUser;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.USER_HEADER;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.V1_STATEMENT_PATH;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class ProxyRequestHandler
{
    private static final Logger log = Logger.get(ProxyRequestHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Duration asyncTimeout;
    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("proxy-%s"));
    private final HttpClient httpClient;
    private final RoutingManager routingManager;
    private final QueryHistoryManager queryHistoryManager;
    private final boolean cookiesEnabled;

    @Inject
    public ProxyRequestHandler(
            @ForProxy HttpClient httpClient,
            RoutingManager routingManager,
            QueryHistoryManager queryHistoryManager,
            HaGatewayConfiguration haGatewayConfiguration)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.routingManager = requireNonNull(routingManager, "routingManager is null");
        this.queryHistoryManager = requireNonNull(queryHistoryManager, "queryHistoryManager is null");
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
        asyncTimeout = haGatewayConfiguration.getRouting().getAsyncTimeout();
    }

    @PreDestroy
    public void shutdown()
    {
        executor.shutdownNow();
    }

    public void deleteRequest(
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            URI remoteUri)
    {
        Request.Builder request = prepareDelete();
        performRequest(remoteUri, servletRequest, asyncResponse, request);
    }

    public void getRequest(
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            URI remoteUri)
    {
        Request.Builder request = prepareGet();
        performRequest(remoteUri, servletRequest, asyncResponse, request);
    }

    public void postRequest(
            String statement,
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            URI remoteUri)
    {
        Request.Builder request = preparePost()
                .setBodyGenerator(createStaticBodyGenerator(statement, UTF_8));
        performRequest(remoteUri, servletRequest, asyncResponse, request);
    }

    private void performRequest(
            URI remoteUri,
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            Request.Builder requestBuilder)
    {
        requestBuilder.setUri(remoteUri);

        for (String name : list(servletRequest.getHeaderNames())) {
            for (String value : list(servletRequest.getHeaders(name))) {
                // TODO: decide what should and shouldn't be forwarded
                if (!name.equalsIgnoreCase("Accept-Encoding") && !name.equalsIgnoreCase("Host")) {
                    requestBuilder.addHeader(name, value);
                }
            }
        }
        requestBuilder.addHeader(VIA, format("%s TrinoGateway", servletRequest.getProtocol()));
        addXForwardedHeaders(servletRequest, requestBuilder);

        ImmutableList<NewCookie> oauth2GatewayCookie = getOAuth2GatewayCookie(remoteUri, servletRequest);

        Request request = requestBuilder
                .setPreserveAuthorizationOnRedirect(true)
                .setFollowRedirects(false)
                .build();

        FluentFuture<ProxyResponse> future = executeHttp(request);

        if (request.getUri().getPath().startsWith(V1_STATEMENT_PATH) && request.getMethod().equals(HttpMethod.POST)) {
            future = future.transform(response -> recordBackendForQueryId(request, response), executor);
        }

        setupAsyncResponse(
                asyncResponse,
                future.transform(response -> buildResponse(response, oauth2GatewayCookie), executor)
                        .catching(ProxyException.class, e -> handleProxyException(request, e), directExecutor()));
    }

    private ImmutableList<NewCookie> getOAuth2GatewayCookie(URI remoteUri, HttpServletRequest servletRequest)
    {
        if (cookiesEnabled) {
            if (remoteUri.getPath().startsWith(OAuth2GatewayCookie.OAUTH2_PATH)
                    && !(servletRequest.getCookies() != null
                    && Arrays.stream(servletRequest.getCookies()).anyMatch(c -> c.getName().equals(OAuth2GatewayCookie.NAME)))) {
                GatewayCookie oauth2Cookie = new OAuth2GatewayCookie(getRemoteTarget(remoteUri));
                return ImmutableList.of(oauth2Cookie.toNewCookie());
            }
            else if (servletRequest.getCookies() != null) {
                return Arrays.stream(servletRequest.getCookies())
                        .filter(c -> c.getName().startsWith(GatewayCookie.PREFIX))
                        .map(GatewayCookie::fromCookie)
                        .filter(c -> !c.isValid() || c.matchesDeletePath(remoteUri.getPath()))
                        .map(GatewayCookie::toNewCookie)
                        .map(newCookie -> new NewCookie.Builder(newCookie).value("delete").maxAge(0).build())
                        .collect(toImmutableList());
            }
        }
        return ImmutableList.of();
    }

    private static String getRemoteTarget(URI remoteUri)
    {
        return format("%s://%s", remoteUri.getScheme(), remoteUri.getAuthority());
    }

    private Response buildResponse(ProxyResponse response, ImmutableList<NewCookie> cookie)
    {
        Response.ResponseBuilder builder = Response.status(response.statusCode()).entity(response.body());
        response.headers().forEach((headerName, value) -> builder.header(headerName.toString(), value));
        cookie.forEach(builder::cookie);
        return builder.build();
    }

    private void setupAsyncResponse(AsyncResponse asyncResponse, ListenableFuture<Response> future)
    {
        bindAsyncResponse(asyncResponse, future, executor)
                .withTimeout(asyncTimeout, () -> Response
                        .status(BAD_GATEWAY)
                        .type(TEXT_PLAIN_TYPE)
                        .entity("Request to remote Trino server timed out after" + asyncTimeout)
                        .build());
    }

    private FluentFuture<ProxyResponse> executeHttp(Request request)
    {
        return FluentFuture.from(httpClient.executeAsync(request, new ProxyResponseHandler()));
    }

    private static <T> T handleProxyException(Request request, ProxyException e)
    {
        log.warn(e, "Proxy request failed: %s %s", request.getMethod(), request.getUri());
        throw badRequest(e.getMessage());
    }

    private static WebApplicationException badRequest(String message)
    {
        throw new WebApplicationException(
                Response.status(Response.Status.BAD_GATEWAY)
                        .type(TEXT_PLAIN_TYPE)
                        .entity(message)
                        .build());
    }

    private ProxyResponse recordBackendForQueryId(Request request, ProxyResponse response)
    {
        log.debug("For Request [%s] got Response [%s]", request.getUri(), response.body());

        QueryHistoryManager.QueryDetail queryDetail = getQueryDetailsFromRequest(request);

        log.debug("Extracting proxy destination : [%s] for request : [%s]", queryDetail.getBackendUrl(), request.getUri());

        if (response.statusCode() == OK.getStatusCode()) {
            try {
                HashMap<String, String> results = OBJECT_MAPPER.readValue(response.body(), HashMap.class);
                queryDetail.setQueryId(results.get("id"));
                routingManager.setBackendForQueryId(queryDetail.getQueryId(), queryDetail.getBackendUrl());
                log.debug("QueryId [%s] mapped with proxy [%s]", queryDetail.getQueryId(), queryDetail.getBackendUrl());
            }
            catch (IOException e) {
                log.error("Failed to get QueryId from response [%s] , Status code [%s]", response.body(), response.statusCode());
            }
        }
        else {
            log.error("Non OK HTTP Status code with response [%s] , Status code [%s]", response.body(), response.statusCode());
        }
        queryHistoryManager.submitQueryDetail(queryDetail);
        return response;
    }

    public static QueryHistoryManager.QueryDetail getQueryDetailsFromRequest(Request request)
    {
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setBackendUrl(getRemoteTarget(request.getUri()));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryDetail.setUser(getQueryUser(request.getHeader(USER_HEADER), request.getHeader(AUTHORIZATION)));
        queryDetail.setSource(request.getHeader(SOURCE_HEADER));

        String queryText = new String(((StaticBodyGenerator) request.getBodyGenerator()).getBody(), UTF_8);
        queryDetail.setQueryText(
                queryText.length() > QUERY_TEXT_LENGTH_FOR_HISTORY
                        ? queryText.substring(0, QUERY_TEXT_LENGTH_FOR_HISTORY) + "..."
                        : queryText);
        return queryDetail;
    }

    private void addXForwardedHeaders(HttpServletRequest servletRequest, Request.Builder requestBuilder)
    {
        requestBuilder.addHeader(X_FORWARDED_FOR, servletRequest.getRemoteAddr());
        requestBuilder.addHeader(X_FORWARDED_PROTO, servletRequest.getScheme());
        requestBuilder.addHeader(X_FORWARDED_PORT, String.valueOf(servletRequest.getServerPort()));
        String hostHeader = servletRequest.getHeader(HOST);
        if (hostHeader != null) {
            requestBuilder.addHeader(X_FORWARDED_HOST, hostHeader);
        }
    }
}
