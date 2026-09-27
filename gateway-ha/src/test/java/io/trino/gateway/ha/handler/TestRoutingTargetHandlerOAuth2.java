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

import com.google.common.hash.Hashing;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.OAuth2RoutingUtils;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingTargetHandlerOAuth2
{
    private static final UUID POLL_AUTH_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String POLL_PIN_KEY = OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/" + POLL_AUTH_ID).orElseThrow();

    private static final UUID INITIATE_AUTH_ID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final String INITIATE_AUTH_ID_HASH = sha256Hex(INITIATE_AUTH_ID.toString());
    private static final String INITIATE_PIN_KEY =
            OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/" + INITIATE_AUTH_ID_HASH).orElseThrow();

    private static final UUID CALLBACK_AUTH_ID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final String CALLBACK_AUTH_ID_HASH = sha256Hex(CALLBACK_AUTH_ID.toString());
    private static final String CALLBACK_PIN_KEY = OAuth2RoutingUtils.pinKeyForAuthIdHash(CALLBACK_AUTH_ID_HASH);

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
        when(store.findBackend(POLL_PIN_KEY)).thenReturn(Optional.of("http://dead:8080"));
        // No longer active and healthy (deactivated, unhealthy, or removed from the fleet).
        when(routingManager.isBackendActiveAndHealthy("http://dead:8080")).thenReturn(false);

        HttpServletRequest request = oauthRequest("/oauth2/token/" + POLL_AUTH_ID);

        assertThatThrownBy(() -> handler(routingManager, store).resolveRouting(request))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> {
                    // Trino token-poll failure contract: 200 with a JSON error body.
                    assertThat(e.getResponse().getStatus()).isEqualTo(200);
                    assertThat((String) e.getResponse().getEntity()).contains("\"error\"");
                });

        // The stale pin is dropped so the client's next attempt re-authenticates.
        verify(store).removeBackend(POLL_PIN_KEY);
    }

    @Test
    void testForcesReAuthWithUnauthorizedForInitiateLegWhenPinnedBackendUnavailable()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        when(store.findBackend(INITIATE_PIN_KEY)).thenReturn(Optional.of("http://dead:8080"));
        // No longer active and healthy (deactivated, unhealthy, or removed from the fleet).
        when(routingManager.isBackendActiveAndHealthy("http://dead:8080")).thenReturn(false);

        HttpServletRequest request = oauthRequest("/oauth2/token/initiate/" + INITIATE_AUTH_ID_HASH);

        assertThatThrownBy(() -> handler(routingManager, store).resolveRouting(request))
                .isInstanceOfSatisfying(WebApplicationException.class, e ->
                        // The browser initiate leg has no token-poll contract to satisfy: a plain 401
                        // ends it, unlike the driver poll leg's 200-with-error-body contract.
                        assertThat(e.getResponse().getStatus()).isEqualTo(401));

        // The stale pin is dropped so the client's next attempt re-authenticates.
        verify(store).removeBackend(INITIATE_PIN_KEY);
    }

    @Test
    void testRoutesToPinnedBackendWhenActiveAndHealthy()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        when(store.findBackend(POLL_PIN_KEY)).thenReturn(Optional.of("http://live:8080"));
        // Active and healthy -> route the in-flight handshake there.
        when(routingManager.isBackendActiveAndHealthy("http://live:8080")).thenReturn(true);

        HttpServletRequest request = oauthRequest("/oauth2/token/" + POLL_AUTH_ID);

        RoutingTargetResponse response = handler(routingManager, store).resolveRouting(request);

        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://live:8080");
        verify(store, never()).removeBackend(POLL_PIN_KEY);
    }

    @Test
    void testRoutesCallbackToMintingBackendViaState()
    {
        RoutingManager routingManager = mock(RoutingManager.class);
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);
        // The callback carries no id in its path; it is pinned by the authIdHash inside the state JWT,
        // reusing the pin recorded for the initiate leg.
        when(store.findBackend(CALLBACK_PIN_KEY)).thenReturn(Optional.of("http://minting:8080"));
        when(routingManager.isBackendActiveAndHealthy("http://minting:8080")).thenReturn(true);

        HttpServletRequest request = oauthRequest("/oauth2/callback");
        when(request.getQueryString()).thenReturn("code=abc&state=" + stateJwt(CALLBACK_AUTH_ID_HASH));

        RoutingTargetResponse response = handler(routingManager, store).resolveRouting(request);

        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://minting:8080");
        verify(store, never()).removeBackend(CALLBACK_PIN_KEY);
    }

    @Test
    void testRewriteLogTargetRedactsHandshakeIds()
    {
        // The reroute log line must not leak the raw authId/authIdHash through the rewrite target
        HttpServletRequest pollRequest = oauthRequest("/oauth2/token/" + POLL_AUTH_ID);
        assertThat(RoutingTargetHandler.redactedRewriteTarget("http://live:8080", pollRequest))
                .isEqualTo("http://live:8080/oauth2/token/<redacted>")
                .doesNotContain(POLL_AUTH_ID.toString());

        HttpServletRequest initiateRequest = oauthRequest("/oauth2/token/initiate/" + INITIATE_AUTH_ID_HASH);
        assertThat(RoutingTargetHandler.redactedRewriteTarget("http://live:8080", initiateRequest))
                .isEqualTo("http://live:8080/oauth2/token/initiate/<redacted>")
                .doesNotContain(INITIATE_AUTH_ID_HASH);

        HttpServletRequest callbackRequest = oauthRequest("/oauth2/callback");
        String state = stateJwt(CALLBACK_AUTH_ID_HASH);
        when(callbackRequest.getQueryString()).thenReturn("code=abc&state=" + state);
        assertThat(RoutingTargetHandler.redactedRewriteTarget("http://live:8080", callbackRequest))
                .isEqualTo("http://live:8080/oauth2/callback?<redacted>")
                .doesNotContain(state);
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

    private static String sha256Hex(String value)
    {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }
}
