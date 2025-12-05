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

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingDestination;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static io.trino.gateway.ha.handler.ProxyUtils.buildUriWithNewCluster;
import static io.trino.gateway.ha.handler.ProxyUtils.extractQueryIdIfPresent;
import static java.util.Objects.requireNonNull;

public class RoutingTargetHandler
{
    private static final Logger log = Logger.get(RoutingTargetHandler.class);
    private final RoutingManager routingManager;
    private final RoutingGroupSelector routingGroupSelector;
    private final String defaultRoutingGroup;
    private final List<String> statementPaths;
    private final boolean requestAnalyserClientsUseV2Format;
    private final int requestAnalyserMaxBodySize;
    private final boolean cookiesEnabled;

    @Inject
    public RoutingTargetHandler(
            RoutingManager routingManager,
            RoutingGroupSelector routingGroupSelector,
            HaGatewayConfiguration haGatewayConfiguration)
    {
        this.routingManager = requireNonNull(routingManager);
        this.routingGroupSelector = requireNonNull(routingGroupSelector);
        this.defaultRoutingGroup = haGatewayConfiguration.getRouting().getDefaultRoutingGroup();
        statementPaths = requireNonNull(haGatewayConfiguration.getStatementPaths());
        requestAnalyserClientsUseV2Format = haGatewayConfiguration.getRequestAnalyzerConfig().isClientsUseV2Format();
        requestAnalyserMaxBodySize = haGatewayConfiguration.getRequestAnalyzerConfig().getMaxBodySize();
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
    }

    public RoutingTargetResponse resolveRouting(HttpServletRequest request)
    {
        Optional<String> queryId = extractQueryIdIfPresent(request, statementPaths, requestAnalyserClientsUseV2Format, requestAnalyserMaxBodySize);
        Optional<String> previousCluster = getPreviousCluster(queryId, request);

        RoutingTargetResponse routingTargetResponse = previousCluster.map(cluster -> {
            String routingGroup = queryId.map(routingManager::findRoutingGroupForQueryId)
                    .orElse(defaultRoutingGroup);
            String externalUrl = queryId.map(routingManager::findExternalUrlForQueryId)
                    .orElse(cluster);
            return new RoutingTargetResponse(
                    new RoutingDestination(routingGroup, cluster, buildUriWithNewCluster(cluster, request), externalUrl),
                    request);
        }).orElse(getRoutingTargetResponse(request));

        logRewrite(routingTargetResponse.routingDestination().clusterHost(), request);
        return routingTargetResponse;
    }

    private RoutingTargetResponse getRoutingTargetResponse(HttpServletRequest request)
    {
        RoutingSelectorResponse routingDestination = routingGroupSelector.findRoutingDestination(request);
        String user = request.getHeader(USER_HEADER);

        // When no cluster is found:
        //   - If strictRouting is false, fall back to the default routing group backend.
        //   - If strictRouting is true, return a 404 response.
        String routingGroup = !isNullOrEmpty(routingDestination.routingGroup())
                ? routingDestination.routingGroup()
                : defaultRoutingGroup;
        boolean strictRouting = Optional.ofNullable(routingDestination.strictRouting()).orElse(false);
        ProxyBackendConfiguration backendConfiguration = routingManager.provideBackendConfiguration(routingGroup, user, strictRouting);
        String clusterHost = backendConfiguration.getProxyTo();
        String externalUrl = backendConfiguration.getExternalUrl();
        // Apply headers from RoutingDestination if there are any
        HttpServletRequest modifiedRequest = request;
        if (!routingDestination.externalHeaders().isEmpty()) {
            modifiedRequest = new HeaderModifyingRequestWrapper(request, routingDestination.externalHeaders());
        }
        return new RoutingTargetResponse(
                new RoutingDestination(routingGroup, clusterHost, buildUriWithNewCluster(clusterHost, request), externalUrl),
                modifiedRequest);
    }

    /**
     * A wrapper for HttpServletRequest that allows modifying multiple headers.
     */
    private static class HeaderModifyingRequestWrapper
            extends HttpServletRequestWrapper
    {
        private final Map<String, String> customHeaders;

        public HeaderModifyingRequestWrapper(HttpServletRequest request, Map<String, String> customHeaders)
        {
            super(request);
            this.customHeaders = customHeaders;
        }

        @Override
        public String getHeader(String name)
        {
            if (customHeaders.containsKey(name)) {
                return customHeaders.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            if (customHeaders.containsKey(name)) {
                return Collections.enumeration(Collections.singletonList(customHeaders.get(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return Collections.enumeration(
                    Stream.concat(Collections.list(super.getHeaderNames()).stream(), customHeaders.keySet().stream())
                            .distinct()
                            .toList());
        }
    }

    private Optional<String> getPreviousCluster(Optional<String> queryId, HttpServletRequest request)
    {
        if (queryId.isPresent()) {
            return queryId.map(routingManager::findBackendForQueryId);
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
                buildUriWithNewCluster(newBackend, request));
    }
}
