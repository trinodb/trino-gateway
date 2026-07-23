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

import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingTargetHandlerOAuth2
{
    @BeforeAll
    void initCookieSingleton()
    {
        // RoutingTargetHandler reads the (disabled-by-default) cookie singleton in its constructor.
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(new GatewayCookieConfiguration());
    }

    private static RoutingTargetHandler handler(RoutingManager routingManager, OAuth2RoutingStore store)
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.getRouting().setOauth2RoutingEnabled(true);
        return new RoutingTargetHandler(routingManager, store, mock(RoutingGroupSelector.class), config);
    }

    private static HttpServletRequest oauthRequest(String path)
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    @Test
    void testForcesReAuthAndDropsPinWhenPinnedBackendUnavailable()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        when(store.findBackend("authABC")).thenReturn(Optional.of("http://dead:8080"));
        // No longer active and healthy (deactivated, unhealthy, or removed from the fleet).
        when(routingManager.isBackendActiveAndHealthy("http://dead:8080")).thenReturn(false);

        HttpServletRequest request = oauthRequest("/oauth2/token/authABC");

        assertThatThrownBy(() -> handler(routingManager, store).resolveRouting(request))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> {
                    // Trino token-poll failure contract: 200 with a JSON error body.
                    assertThat(e.getResponse().getStatus()).isEqualTo(200);
                    assertThat((String) e.getResponse().getEntity()).contains("\"error\"");
                });

        // The stale pin is dropped so the client's next attempt re-authenticates.
        verify(store).removeBackend("authABC");
    }

    @Test
    void testRoutesToPinnedBackendWhenActiveAndHealthy()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        when(store.findBackend("authXYZ")).thenReturn(Optional.of("http://live:8080"));
        // Active and healthy -> route the in-flight handshake there.
        when(routingManager.isBackendActiveAndHealthy("http://live:8080")).thenReturn(true);

        HttpServletRequest request = oauthRequest("/oauth2/token/authXYZ");

        RoutingTargetResponse response = handler(routingManager, store).resolveRouting(request);

        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://live:8080");
        verify(store, never()).removeBackend("authXYZ");
    }

    @Test
    void testRoutesCallbackToMintingBackendViaState()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        // The callback carries no id in its path; it is pinned by the authIdHash inside the state JWT,
        // reusing the pin recorded for the initiate leg.
        when(store.findBackend("hashHHH")).thenReturn(Optional.of("http://minting:8080"));
        when(routingManager.isBackendActiveAndHealthy("http://minting:8080")).thenReturn(true);

        HttpServletRequest request = oauthRequest("/oauth2/callback");
        when(request.getQueryString()).thenReturn("code=abc&state=" + stateJwt("hashHHH"));

        RoutingTargetResponse response = handler(routingManager, store).resolveRouting(request);

        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://minting:8080");
        verify(store, never()).removeBackend("hashHHH");
    }

    private static String stateJwt(String handlerState)
    {
        String header = base64Url("{\"alg\":\"HS256\"}");
        String payload = base64Url("{\"aud\":\"trino_oauth_ui\",\"handler_state\":\"" + handlerState + "\"}");
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
