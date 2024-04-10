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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.OAuth2GatewayCookie;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyHandler;
import io.trino.gateway.proxyserver.wrapper.MultiReadHttpServletRequest;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;

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
    public static final String SOURCE_HEADER = "X-Trino-Source";
    public static final String HOST_HEADER = "Host";
    private static final int QUERY_TEXT_LENGTH_FOR_HISTORY = 200;
    /**
     * This regular expression matches query ids as they appear in the path of a URL. The query id must be preceded
     * by a "/". A query id is defined as three groups of digits separated by underscores, with a final group
     * consisting of any alphanumeric characters.
     */
    private static final Pattern QUERY_ID_PATH_PATTERN = Pattern.compile(".*/(\\d+_\\d+_\\d+_\\w+).*");
    /**
     * This regular expression matches query ids as they appear in the query parameters of a URL. The query id is
     * defined as in QUERY_TEXT_LENGTH_FOR_HISTORY. The query id must either be at the beginning of the query parameter
     * string, or be preceded by %2F (a URL-encoded "/"), or  "query_id=", with or without the underscore and any
     * capitalization.
     */
    private static final Pattern QUERY_ID_PARAM_PATTERN = Pattern.compile(".*(?:%2F|(?i)query_?id(?-i)=|^)(\\d+_\\d+_\\d+_\\w+).*");

    private static final Pattern EXTRACT_BETWEEN_SINGLE_QUOTES = Pattern.compile("'([^\\s']+)'");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.get(QueryIdCachingProxyHandler.class);

    private final RoutingManager routingManager;
    private final RoutingGroupSelector routingGroupSelector;
    private final QueryHistoryManager queryHistoryManager;

    private final ProxyHandlerStats proxyHandlerStats;
    private final List<String> extraWhitelistPaths;
    private final String applicationEndpoint;
    private final boolean cookiesEnabled;

    public QueryIdCachingProxyHandler(
            QueryHistoryManager queryHistoryManager,
            RoutingManager routingManager,
            RoutingGroupSelector routingGroupSelector,
            int serverApplicationPort,
            ProxyHandlerStats proxyHandlerStats,
            List<String> extraWhitelistPaths)
    {
        this.proxyHandlerStats = proxyHandlerStats;
        this.routingManager = routingManager;
        this.routingGroupSelector = routingGroupSelector;
        this.queryHistoryManager = queryHistoryManager;
        this.extraWhitelistPaths = extraWhitelistPaths;
        this.applicationEndpoint = "http://localhost:" + serverApplicationPort;
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
    }

    protected static String extractQueryIdIfPresent(String path, String queryParams)
    {
        if (path == null) {
            return null;
        }
        String queryId = null;

        log.debug("trying to extract query id from  path [%s] or queryString [%s]", path, queryParams);
        if (path.startsWith(V1_STATEMENT_PATH) || path.startsWith(V1_QUERY_PATH)) {
            String[] tokens = path.split("/");
            if (tokens.length >= 4) {
                if (path.contains("queued")
                        || path.contains("scheduled")
                        || path.contains("executing")
                        || path.contains("partialCancel")) {
                    queryId = tokens[4];
                }
                else {
                    queryId = tokens[3];
                }
            }
        }
        else if (path.startsWith(TRINO_UI_PATH)) {
            Matcher matcher = QUERY_ID_PATH_PATTERN.matcher(path);
            if (matcher.matches()) {
                queryId = matcher.group(1);
            }
        }
        if (!isNullOrEmpty(queryParams)) {
            Matcher matcher = QUERY_ID_PARAM_PATTERN.matcher(queryParams);
            if (matcher.matches()) {
                queryId = matcher.group(1);
            }
        }
        log.debug("query id in url [%s]", queryId);
        return queryId;
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

    static String getQueryUser(HttpServletRequest request)
    {
        String trinoUser = request.getHeader(USER_HEADER);

        if (!isNullOrEmpty(trinoUser)) {
            log.info("user from %s", USER_HEADER);
            return trinoUser;
        }

        log.info("user from basic auth");
        String user = "";
        String header = request.getHeader(AUTHORIZATION);
        if (header == null) {
            log.error("didn't find any basic auth header");
            return user;
        }

        int space = header.indexOf(' ');
        if ((space < 0) || !header.substring(0, space).equalsIgnoreCase("basic")) {
            log.error("basic auth format is incorrect");
            return user;
        }

        String headerInfo = header.substring(space + 1).trim();
        if (isNullOrEmpty(headerInfo)) {
            log.error("The encoded value of basic auth doesn't exist");
            return user;
        }

        String info = new String(Base64.getDecoder().decode(headerInfo));
        List<String> parts = Splitter.on(':').limit(2).splitToList(info);
        if (parts.size() < 1) {
            log.error("no user inside the basic auth text");
            return user;
        }
        return parts.get(0);
    }

    protected String extractQueryIdIfPresent(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        String queryParams = request.getQueryString();
        try {
            String queryText = CharStreams.toString(request.getReader());
            if (!isNullOrEmpty(queryText)
                    && queryText.toLowerCase().contains("system.runtime.kill_query")) {
                // extract and return the queryId
                String[] parts = queryText.split(",");
                for (String part : parts) {
                    if (part.contains("query_id")) {
                        Matcher m = EXTRACT_BETWEEN_SINGLE_QUOTES.matcher(part);
                        if (m.find()) {
                            String queryQuoted = m.group();
                            if (!isNullOrEmpty(queryQuoted) && queryQuoted.length() > 0) {
                                return queryQuoted.substring(1, queryQuoted.length() - 1);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            log.error(e, "Error extracting query payload from request");
        }

        return extractQueryIdIfPresent(path, queryParams);
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

        if (isPathWhiteListed(request.getRequestURI())) {
            setForwardedHostHeaderOnProxyRequest(request, proxyRequest);
        }
    }

    private boolean isPathWhiteListed(String path)
    {
        return path.startsWith(V1_STATEMENT_PATH)
                || path.startsWith(V1_QUERY_PATH)
                || path.startsWith(TRINO_UI_PATH)
                || path.startsWith(V1_INFO_PATH)
                || path.startsWith(V1_NODE_PATH)
                || path.startsWith(UI_API_STATS_PATH)
                || path.startsWith(OAUTH_PATH)
                || extraWhitelistPaths.stream().anyMatch(s -> path.startsWith(s));
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
        if (!isPathWhiteListed(request.getRequestURI())) {
            return buildUriWithNewBackend(applicationEndpoint, request);
        }

        Optional<String> previousBackend = getPreviousBackend(request);
        String clusterHost = previousBackend.orElseGet(() -> getBackendFromRoutingGroup(request));
        // set target clusterHost so that we could save queryId to cluster mapping later.
        ((MultiReadHttpServletRequest) request).addHeader(PROXY_TARGET_HEADER, clusterHost);
        logRewrite(clusterHost, request);

        return buildUriWithNewBackend(clusterHost, request);
    }

    private void logRewrite(String newBackend, HttpServletRequest request)
    {
        log.info("Rerouting [%s://%s:%s%s%s]--> [%s]",
                request.getScheme(),
                request.getRemoteHost(),
                request.getServerPort(),
                request.getRequestURI(),
                (request.getQueryString() != null ? "?" + request.getQueryString() : ""),
                buildUriWithNewBackend(newBackend, request));
    }

    private String buildUriWithNewBackend(String backendHost, HttpServletRequest request)
    {
        return backendHost + request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
    }

    private String getBackendFromRoutingGroup(HttpServletRequest request)
    {
        String routingGroup = routingGroupSelector.findRoutingGroup(request);
        String user = request.getHeader(USER_HEADER);
        if (!isNullOrEmpty(routingGroup)) {
            // This falls back on adhoc backend if there are no cluster found for the routing group.
            return routingManager.provideBackendForRoutingGroup(routingGroup, user);
        }
        return routingManager.provideAdhocBackend(user);
    }

    private Optional<String> getPreviousBackend(HttpServletRequest request)
    {
        String queryId = extractQueryIdIfPresent(request);
        if (!isNullOrEmpty(queryId)) {
            return Optional.of(routingManager.findBackendForQueryId(queryId));
        }
        if (cookiesEnabled && request.getCookies() != null) {
            List<GatewayCookie> cookies = Arrays.stream(request.getCookies())
                    .filter(c -> c.getName().startsWith(GatewayCookie.PREFIX))
                    .map(GatewayCookie::fromCookie)
                    .filter(GatewayCookie::isValid)
                    .filter(c -> !isNullOrEmpty(c.getBackend()))
                    .filter(c -> c.matchesRoutingPath(request.getRequestURI()))
                    .sorted()
                    .toList();
            if (!cookies.isEmpty()) {
                return Optional.of(cookies.getFirst().getBackend());
            }
        }

        return Optional.empty();
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

    void recordBackendForQueryId(HttpServletRequest request, HttpServletResponse response, byte[] buffer)
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
        log.debug("Extracting Proxy destination : [%s] for request : [%s]", queryDetail.getBackendUrl(), request.getRequestURI());

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

    private QueryHistoryManager.QueryDetail getQueryDetailsFromRequest(HttpServletRequest request)
            throws IOException
    {
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setBackendUrl(request.getHeader(PROXY_TARGET_HEADER));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryDetail.setUser(getQueryUser(request));
        queryDetail.setSource(request.getHeader(SOURCE_HEADER));
        String queryText = CharStreams.toString(request.getReader());
        queryDetail.setQueryText(
                queryText.length() > QUERY_TEXT_LENGTH_FOR_HISTORY
                        ? queryText.substring(0, QUERY_TEXT_LENGTH_FOR_HISTORY) + "..."
                        : queryText);
        return queryDetail;
    }
}
