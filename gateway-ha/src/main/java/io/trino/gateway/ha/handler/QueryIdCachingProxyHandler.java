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
package io.trino.gateway.ha.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.OAuth2GatewayCookie;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.trino.gateway.ha.handler.ProxyUtils.buildUriWithNewBackend;
import static io.trino.gateway.ha.handler.ProxyUtils.getQueryDetailsFromRequest;

public class QueryIdCachingProxyHandler
        extends ProxyHandler
{
    public static final String PROXY_TARGET_HEADER = "proxytarget";
    public static final String V1_STATEMENT_PATH = "/v1/statement";
    public static final String V1_QUERY_PATH = "/v1/query";
    public static final String V1_INFO_PATH = "/v1/info";
    public static final String V1_NODE_PATH = "/v1/node";
    public static final String UI_API_STATS_PATH = "/ui/api/stats";
    public static final String UI_LOGIN_PATH = "/ui/login";
    public static final String UI_API_QUEUED_LIST_PATH = "/ui/api/query?state=QUEUED";
    public static final String TRINO_UI_PATH = "/ui";
    public static final String OAUTH_PATH = "/oauth2";
    public static final String AUTHORIZATION = "Authorization";
    public static final String USER_HEADER = "X-Trino-User";
    public static final String HOST_HEADER = "Host";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.get(QueryIdCachingProxyHandler.class);

    private final RoutingManager routingManager;
    private final QueryHistoryManager queryHistoryManager;
    private final RoutingTargetHandler routingTargetHandler;

    private final ProxyHandlerStats proxyHandlerStats;
    private final String applicationEndpoint;
    private final boolean cookiesEnabled;

    public QueryIdCachingProxyHandler(
            QueryHistoryManager queryHistoryManager,
            RoutingManager routingManager,
            RoutingTargetHandler routingTargetHandler,
            int serverApplicationPort,
            ProxyHandlerStats proxyHandlerStats)
    {
        this.proxyHandlerStats = proxyHandlerStats;
        this.routingManager = routingManager;
        this.queryHistoryManager = queryHistoryManager;
        this.routingTargetHandler = routingTargetHandler;
        this.applicationEndpoint = "http://localhost:" + serverApplicationPort;
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
    }

    static void setForwardedHostHeaderOnProxyRequest(HttpServletRequest request,
            Request proxyRequest)
    {
        if (request.getHeader(PROXY_TARGET_HEADER) != null) {
            try {
                URI backendUri = new URI(request.getHeader(PROXY_TARGET_HEADER));
                StringBuilder hostName = new StringBuilder();
                hostName.append(backendUri.getHost());
                if (backendUri.getPort() != -1) {
                    hostName.append(":").append(backendUri.getPort());
                }
                String overrideHostName = hostName.toString();
                log.debug("Incoming Request Host header : [%s], proxy request host header : [%s]",
                        request.getHeader(HOST_HEADER), overrideHostName);

                proxyRequest.headers(headers -> headers.add(HOST_HEADER, overrideHostName));
            }
            catch (URISyntaxException e) {
                log.warn(e.toString());
            }
        }
        else {
            log.warn("Proxy Target not set on request, unable to decipher HOST header");
        }
    }

    @Override
    public void preConnectionHook(HttpServletRequest request, Request proxyRequest)
    {
        if (request.getMethod().equals(HttpMethod.POST)
                && request.getRequestURI().startsWith(V1_STATEMENT_PATH)) {
            proxyHandlerStats.recordRequest();
            try {
                String requestBody = CharStreams.toString(request.getReader());
                log.info(
                        "Processing request endpoint: [%s], payload: [%s]",
                        request.getRequestURI(),
                        requestBody);
                debugLogHeaders(request);
            }
            catch (Exception e) {
                log.warn(e, "Error fetching the request payload");
            }
        }

        if (routingTargetHandler.isPathWhiteListed(request.getRequestURI())) {
            setForwardedHostHeaderOnProxyRequest(request, proxyRequest);
        }
    }

    @Override
    public List<Cookie> generateDeleteCookieList(HttpServletRequest clientRequest)
    {
        if (!cookiesEnabled || clientRequest.getCookies() == null) {
            return ImmutableList.of();
        }

        return Arrays.stream(clientRequest.getCookies())
                .filter(c -> c.getName().startsWith(GatewayCookie.PREFIX))
                .map(GatewayCookie::fromCookie)
                .filter(c -> !c.isValid() || c.matchesDeletePath(clientRequest.getRequestURI()))
                .map(GatewayCookie::toCookie)
                .peek(c -> {
                    c.setValue("delete");
                    c.setMaxAge(0);
                })
                .toList();
    }

    @Override
    public String rewriteTarget(HttpServletRequest request)
    {
        if (!routingTargetHandler.isPathWhiteListed(request.getRequestURI())) {
            return buildUriWithNewBackend(applicationEndpoint, request);
        }
        return routingTargetHandler.getRoutingDestination(request);
    }

    @Override
    protected void postConnectionHook(
            HttpServletRequest request,
            HttpServletResponse response,
            byte[] buffer,
            int offset,
            int length,
            Callback callback)
    {
        try {
            if (request.getRequestURI().startsWith(V1_STATEMENT_PATH) && request.getMethod().equals(HttpMethod.POST)) {
                recordBackendForQueryId(request, response, buffer);
            }
            else if (cookiesEnabled && request.getRequestURI().startsWith(OAuth2GatewayCookie.OAUTH2_PATH)
                    && !(request.getCookies() != null
                    && Arrays.stream(request.getCookies()).anyMatch(c -> c.getName().equals(OAuth2GatewayCookie.NAME)))) {
                GatewayCookie oauth2Cookie = new OAuth2GatewayCookie(request.getHeader(PROXY_TARGET_HEADER));
                response.addCookie(oauth2Cookie.toCookie());
            }
        }
        catch (Exception e) {
            log.error(e, "Error in proxying falling back to super call");
        }
        super.postConnectionHook(request, response, buffer, offset, length, callback);
    }

    private void recordBackendForQueryId(HttpServletRequest request, HttpServletResponse response, byte[] buffer)
            throws IOException
    {
        String output;
        boolean isGZipEncoding = isGZipEncoding(response);
        if (isGZipEncoding) {
            output = plainTextFromGz(buffer);
        }
        else {
            output = new String(buffer);
        }
        log.debug("For Request [%s] got Response output [%s]", request.getRequestURI(), output);

        QueryHistoryManager.QueryDetail queryDetail = getQueryDetailsFromRequest(request);

        if (queryDetail.getBackendUrl() == null) {
            log.error("Server response to request %s does not contain proxytarget header", request.getRequestURI());
        }
        log.debug("Extracting proxy destination : [%s] for request : [%s]", queryDetail.getBackendUrl(), request.getRequestURI());

        if (response.getStatus() == HttpStatus.OK_200) {
            HashMap<String, String> results = OBJECT_MAPPER.readValue(output, HashMap.class);
            queryDetail.setQueryId(results.get("id"));

            if (!isNullOrEmpty(queryDetail.getQueryId())) {
                routingManager.setBackendForQueryId(queryDetail.getQueryId(), queryDetail.getBackendUrl());
                log.debug("QueryId [%s] mapped with proxy [%s]", queryDetail.getQueryId(), queryDetail.getBackendUrl());
            }
        }
        else {
            log.error("Non OK HTTP Status code with response [%s] , Status code [%s]", output, response.getStatus());
        }
        // Save history in Trino Gateway.
        queryHistoryManager.submitQueryDetail(queryDetail);
    }
}
