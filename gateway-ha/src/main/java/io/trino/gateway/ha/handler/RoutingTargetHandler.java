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
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.OAuth2RoutingUtils;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.ws.rs.WebApplicationException;

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
    private final OAuth2RoutingStore oauth2RoutingStore;
    private final RoutingGroupSelector routingGroupSelector;
    private final String defaultRoutingGroup;
    private final List<String> statementPaths;
    private final boolean requestAnalyserClientsUseV2Format;
    private final int requestAnalyserMaxBodySize;
    private final boolean cookiesEnabled;
    private final boolean oauth2RoutingEnabled;

    @Inject
    public RoutingTargetHandler(
            RoutingManager routingManager,
            OAuth2RoutingStore oauth2RoutingStore,
            RoutingGroupSelector routingGroupSelector,
            HaGatewayConfiguration haGatewayConfiguration)
    {
        this.routingManager = requireNonNull(routingManager);
        this.oauth2RoutingStore = requireNonNull(oauth2RoutingStore);
        this.routingGroupSelector = requireNonNull(routingGroupSelector);
        this.defaultRoutingGroup = haGatewayConfiguration.getRouting().getDefaultRoutingGroup();
        statementPaths = requireNonNull(haGatewayConfiguration.getStatementPaths());
        requestAnalyserClientsUseV2Format = haGatewayConfiguration.getRequestAnalyzerConfig().isClientsUseV2Format();
        requestAnalyserMaxBodySize = haGatewayConfiguration.getRequestAnalyzerConfig().getMaxBodySize();
        cookiesEnabled = GatewayCookieConfigurationPropertiesProvider.getInstance().isEnabled();
        oauth2RoutingEnabled = haGatewayConfiguration.getRouting().isOauth2RoutingEnabled();
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
        }).orElseGet(() -> getRoutingTargetResponse(request));

        logRewrite(routingTargetResponse.routingDestination().clusterHost(), request);
        return routingTargetResponse;
    }

    private RoutingTargetResponse getRoutingTargetResponse(HttpServletRequest request)
    {
        RoutingSelectorResponse routingDestination = routingGroupSelector.findRoutingDestination(request);
        String user = request.getHeader(USER_HEADER);

        // This falls back on default routing group backend if there is no cluster found for the routing group.
        String routingGroup = !isNullOrEmpty(routingDestination.routingGroup())
                ? routingDestination.routingGroup()
                : defaultRoutingGroup;
        ProxyBackendConfiguration backendConfiguration = routingManager.provideBackendConfiguration(routingGroup, user);
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

        private HeaderModifyingRequestWrapper(HttpServletRequest request, Map<String, String> customHeaders)
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
                return Collections.enumeration(List.of(customHeaders.get(name)));
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
        if (oauth2RoutingEnabled) {
            Optional<String> oauthBackend = getOAuth2StickyBackend(request);
            if (oauthBackend.isPresent()) {
                return oauthBackend;
            }
        }
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

    /**
     * Pins an in-flight Trino OAuth2 token-exchange request to the coordinator that minted its
     * {@code authId}. Covers all three gateway legs of the handshake — the driver poll, the browser
     * initiate, and the browser callback (keyed by the {@code authIdHash} inside its {@code state}).
     * Returns:
     * <ul>
     *   <li>a present backend — route the request there (the sticky coordinator is active and healthy);</li>
     *   <li>empty — not a pinnable token-exchange request, no pin recorded yet on this gateway (fall
     *       through to normal routing; the 401 challenge handler records the pin), or the pinned
     *       coordinator merely looks unhealthy from this instance's own local view (also falls through
     *       to normal routing, without touching the shared pin — see below).</li>
     * </ul>
     * If a pin exists but its coordinator is truly gone — deactivated or removed from the fleet, a
     * fact every gateway instance agrees on via the shared backend configuration (see
     * {@link RoutingManager#isBackendActive}) — the pin is dropped and the client is forced to
     * re-authenticate, since only the minting coordinator holds the exchange state and the handshake
     * cannot be recovered elsewhere. A pin is never dropped merely because <em>this</em> instance's own
     * health check currently reports the coordinator unhealthy/pending/unknown: that view is local and
     * can be stale or simply wrong for one pod, and incorrectly deleting a shared pin over it would
     * break the handshake for every other pod too.
     */
    private Optional<String> getOAuth2StickyBackend(HttpServletRequest request)
    {
        String pinKey = oauth2PinKey(request).orElse(null);
        if (pinKey == null) {
            return Optional.empty();
        }
        String pinnedBackend = oauth2RoutingStore.findBackend(pinKey).orElse(null);
        if (pinnedBackend == null) {
            return Optional.empty();
        }
        if (routingManager.isBackendActiveAndHealthy(pinnedBackend)) {
            return Optional.of(pinnedBackend);
        }
        if (!routingManager.isBackendActive(pinnedBackend)) {
            // Deactivated or removed from the fleet: a deliberate, shared signal every instance agrees
            // on, so the handshake truly cannot be recovered. Safe to drop the pin and force re-auth.
            oauth2RoutingStore.removeBackend(pinKey);
            log.warn("OAuth2 pinned backend is no longer configured/active for [%s]; forcing re-auth",
                    OAuth2RoutingUtils.redactForLog(request.getRequestURI(), request.getQueryString()));
            throw new WebApplicationException(OAuth2RoutingUtils.forceReAuthResponse(request.getRequestURI()));
        }
        // Configured/active, just not healthy per this instance's own local view: skip the pin (fall
        // through to normal routing) without deleting it, since another pod may see it as healthy, or
        // this view may simply be stale.
        return Optional.empty();
    }

    /**
     * The pin-store lookup key for an in-flight token-exchange request: from the path for the driver
     * poll ({@code /oauth2/token/{authId}}) and the browser initiate
     * ({@code /oauth2/token/initiate/{authIdHash}}), or from the {@code state} parameter for the
     * browser callback ({@code /oauth2/callback}). Empty for anything else.
     */
    private Optional<String> oauth2PinKey(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        Optional<String> callbackKey = OAuth2RoutingUtils.pinKeyFromCallback(path, request.getQueryString());
        if (callbackKey.isPresent()) {
            return callbackKey;
        }
        return OAuth2RoutingUtils.pinKeyFromRequestPath(path);
    }

    private void logRewrite(String newBackend, HttpServletRequest request)
    {
        log.info("Rerouting [%s://%s:%s%s]--> [%s]",
                request.getScheme(),
                request.getRemoteHost(),
                request.getServerPort(),
                OAuth2RoutingUtils.redactForLog(request.getRequestURI(), request.getQueryString()),
                redactedRewriteTarget(newBackend, request));
    }

    /**
     * The rewrite target as written to the reroute log line: {@code newBackend} plus the same
     * redacted path/query {@link #logRewrite} already uses for the incoming request. Unlike
     * {@link ProxyUtils#buildUriWithNewCluster}, which is used for the actual proxied request and
     * must carry the real {@code authId}/{@code authIdHash}/{@code state}, this must never do so:
     * logging the raw target would let a log reader lift the handshake id straight off the reroute
     * line. Package-private so it can be unit-tested directly without capturing log output.
     */
    static String redactedRewriteTarget(String newBackend, HttpServletRequest request)
    {
        return newBackend + OAuth2RoutingUtils.redactForLog(request.getRequestURI(), request.getQueryString());
    }
}
