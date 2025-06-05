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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyResponseConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingDestination;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.OAuth2GatewayCookie;
import io.trino.gateway.ha.router.TrinoRequestUser;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.google.common.collect.ImmutableList.toImmutableList;
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
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.jaxrs.AsyncResponseHandler.bindAsyncResponse;
import static io.trino.gateway.proxyserver.ProxyResponseTransformer.getRemoteTarget;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class ProxyRequestHandler
{
    private static final Logger log = Logger.get(ProxyRequestHandler.class);

    private final Duration asyncTimeout;
    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("proxy-%s"));
    private final HttpClient httpClient;
    private final boolean cookiesEnabled;
    private final boolean addXForwardedHeaders;
    private final List<String> statementPaths;
    private final boolean includeClusterInfoInResponse;
    private final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;
    private final ProxyResponseConfiguration proxyResponseConfiguration;
    private final ProxyResponseTransformer proxyResponseTransformer;

    @Inject
    public ProxyRequestHandler(
            @ForProxy HttpClient httpClient,
            ProxyResponseTransformer proxyResponseTransformer,
            HaGatewayConfiguration haGatewayConfiguration)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.proxyResponseTransformer = requireNonNull(proxyResponseTransformer, "proxyResponseTransformer is null");
        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(haGatewayConfiguration.getRequestAnalyzerConfig());
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
        asyncTimeout = haGatewayConfiguration.getRouting().getAsyncTimeout();
        addXForwardedHeaders = haGatewayConfiguration.getRouting().isAddXForwardedHeaders();
        statementPaths = haGatewayConfiguration.getStatementPaths();
        this.includeClusterInfoInResponse = haGatewayConfiguration.isIncludeClusterHostInResponse();
        proxyResponseConfiguration = haGatewayConfiguration.getProxyResponseConfiguration();
    }

    @PreDestroy
    public void shutdown()
    {
        executor.shutdownNow();
    }

    public void deleteRequest(
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            RoutingDestination routingDestination)
    {
        Request.Builder request = prepareDelete();
        performRequest(routingDestination, servletRequest, asyncResponse, request);
    }

    public void getRequest(
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            RoutingDestination routingDestination)
    {
        Request.Builder request = prepareGet();
        performRequest(routingDestination, servletRequest, asyncResponse, request);
    }

    public void postRequest(
            String statement,
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            RoutingDestination routingDestination)
    {
        Request.Builder request = preparePost()
                .setBodyGenerator(createStaticBodyGenerator(statement, UTF_8));
        performRequest(routingDestination, servletRequest, asyncResponse, request);
    }

    public void putRequest(
            String statement,
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            RoutingDestination routingDestination)
    {
        Request.Builder request = preparePut()
                .setBodyGenerator(createStaticBodyGenerator(statement, UTF_8));
        performRequest(routingDestination, servletRequest, asyncResponse, request);
    }

    private void performRequest(
            RoutingDestination routingDestination,
            HttpServletRequest servletRequest,
            AsyncResponse asyncResponse,
            Request.Builder requestBuilder)
    {
        URI remoteUri = routingDestination.clusterUri();
        requestBuilder.setUri(remoteUri);

        for (String name : list(servletRequest.getHeaderNames())) {
            for (String value : list(servletRequest.getHeaders(name))) {
                // TODO: decide what should and shouldn't be forwarded
                if (!name.equalsIgnoreCase("Accept-Encoding")
                        && !name.equalsIgnoreCase("Host")
                        && (addXForwardedHeaders || !name.startsWith("X-Forwarded"))) {
                    requestBuilder.addHeader(name, value);
                }
            }
        }
        requestBuilder.addHeader(VIA, format("%s TrinoGateway", servletRequest.getProtocol()));
        if (addXForwardedHeaders) {
            addXForwardedHeaders(servletRequest, requestBuilder);
        }

        ImmutableList.Builder<NewCookie> cookieBuilder = ImmutableList.builder();
        cookieBuilder.addAll(getOAuth2GatewayCookie(remoteUri, servletRequest));

        Request request = requestBuilder
                .setFollowRedirects(false)
                .build();

        FluentFuture<ProxyResponse> future = executeHttp(request);

        if (statementPaths.stream().anyMatch(request.getUri().getPath()::startsWith) && request.getMethod().equals(HttpMethod.POST)) {
            String username = trinoRequestUserProvider.getInstance(servletRequest).getUser().orElse(null);
            future = future.transform(response -> proxyResponseTransformer.transform(request, response, username, routingDestination), executor);
            if (includeClusterInfoInResponse) {
                cookieBuilder.add(new NewCookie.Builder("trinoClusterHost").value(remoteUri.getHost()).build());
            }
        }

        setupAsyncResponse(
                asyncResponse,
                future.transform(response -> buildResponse(response, cookieBuilder.build()), executor)
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
        return FluentFuture.from(httpClient.executeAsync(request, new ProxyResponseHandler(proxyResponseConfiguration)));
    }

    private static Response handleProxyException(Request request, ProxyException e)
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

    private void addXForwardedHeaders(HttpServletRequest servletRequest, Request.Builder requestBuilder)
    {
        requestBuilder.addHeader(X_FORWARDED_FOR, servletRequest.getRemoteAddr());
        requestBuilder.addHeader(X_FORWARDED_PROTO, servletRequest.getScheme());
        requestBuilder.addHeader(X_FORWARDED_PORT, String.valueOf(servletRequest.getServerPort()));
        String serverName = servletRequest.getServerName();
        if (serverName != null) {
            requestBuilder.addHeader(X_FORWARDED_HOST, serverName);
        }
    }
}
