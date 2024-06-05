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

import io.airlift.log.Logger;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.wrapper.MultiReadHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.trino.gateway.ha.handler.ProxyUtils.buildUriWithNewBackend;
import static io.trino.gateway.ha.handler.ProxyUtils.extractQueryIdIfPresent;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.OAUTH_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.PROXY_TARGET_HEADER;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.TRINO_UI_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.USER_HEADER;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.V1_INFO_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.V1_NODE_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.V1_QUERY_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.V1_STATEMENT_PATH;
import static java.util.Objects.requireNonNull;

public class RoutingTargetHandler
{
    private static final Logger log = Logger.get(RoutingTargetHandler.class);
    private final RoutingManager routingManager;
    private final RoutingGroupSelector routingGroupSelector;
    private final List<String> extraWhitelistPaths;
    private final boolean cookiesEnabled;

    public RoutingTargetHandler(
            RoutingManager routingManager,
            RoutingGroupSelector routingGroupSelector,
            List<String> extraWhitelistPaths)
    {
        this.routingManager = requireNonNull(routingManager);
        this.routingGroupSelector = requireNonNull(routingGroupSelector);
        this.extraWhitelistPaths = requireNonNull(extraWhitelistPaths);
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
    }

    public String getRoutingDestination(HttpServletRequest request)
    {
        Optional<String> previousBackend = getPreviousBackend(request);
        String clusterHost = previousBackend.orElseGet(() -> getBackendFromRoutingGroup(request));
        // set target clusterHost so that we could save queryId to cluster mapping later.
        ((MultiReadHttpServletRequest) request).addHeader(PROXY_TARGET_HEADER, clusterHost);
        logRewrite(clusterHost, request);

        return buildUriWithNewBackend(clusterHost, request);
    }

    public boolean isPathWhiteListed(String path)
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

    private String getBackendFromRoutingGroup(HttpServletRequest request)
    {
        String routingGroup = routingGroupSelector.findRoutingGroup(request);
        String user = request.getHeader(USER_HEADER);
        if (!isNullOrEmpty(routingGroup)) {
            // This falls back on adhoc backend if there is no cluster found for the routing group.
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
}
