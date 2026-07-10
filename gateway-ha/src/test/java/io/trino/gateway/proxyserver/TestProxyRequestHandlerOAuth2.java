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

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@TestInstance(Lifecycle.PER_CLASS)
final class TestProxyRequestHandlerOAuth2
{
    // A single 401 challenge advertises both the poll-loop authId (x_token_server) and the browser
    // initiate authIdHash (x_redirect_server).
    private static final String TOKEN_EXCHANGE_CHALLENGE =
            "x_redirect_server=\"https://coord-a:8443/oauth2/token/initiate/HASH9\", "
                    + "x_token_server=\"https://coord-a:8443/oauth2/token/AUTH9\"";
    // A run-of-the-mill bearer challenge that is not a token-exchange handshake.
    private static final String NON_TOKEN_EXCHANGE_CHALLENGE = "realm=\"trino\", error=\"invalid_token\"";

    private static final URI REMOTE_URI = URI.create("http://coord-a:8080/oauth2/token/AUTH9");

    private final List<ProxyRequestHandler> handlers = new ArrayList<>();

    @BeforeAll
    void initCookieSingleton()
    {
        // ProxyRequestHandler reads the (disabled-by-default) cookie singleton in its constructor.
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(new GatewayCookieConfiguration());
    }

    @AfterAll
    void cleanup()
    {
        handlers.forEach(ProxyRequestHandler::shutdown);
    }

    @Test
    void testRecordsBothIdsOnTokenExchangeChallenge()
    {
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);

        handler(true, store).recordOAuth2Challenge(REMOTE_URI, response(401, TOKEN_EXCHANGE_CHALLENGE));

        // Both ids are pinned to the challenge's coordinator (scheme://authority of the remote URI).
        verify(store).setBackends(Set.of("AUTH9", "HASH9"), "http://coord-a:8080");
    }

    @Test
    void testWritesNothingOnNon401()
    {
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);

        handler(true, store).recordOAuth2Challenge(REMOTE_URI, response(200, TOKEN_EXCHANGE_CHALLENGE));

        // Only a 401 carries a challenge to record; any other status must pin nothing.
        verifyNoInteractions(store);
    }

    @Test
    void testWritesNothingWhenChallengeIsNotTokenExchange()
    {
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);

        handler(true, store).recordOAuth2Challenge(REMOTE_URI, response(401, NON_TOKEN_EXCHANGE_CHALLENGE));

        // A 401 without a token-exchange challenge (no x_token_server / x_redirect_server) pins nothing.
        verifyNoInteractions(store);
    }

    @Test
    void testInertWhenOAuth2RoutingDisabled()
    {
        OAuth2RoutingStore store = mock(OAuth2RoutingStore.class);

        handler(false, store).recordOAuth2Challenge(REMOTE_URI, response(401, TOKEN_EXCHANGE_CHALLENGE));

        // The whole write path is inert on a dark launch (oauth2RoutingEnabled=false).
        verifyNoInteractions(store);
    }

    private ProxyRequestHandler handler(boolean oauth2RoutingEnabled, OAuth2RoutingStore store)
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.getRouting().setOauth2RoutingEnabled(oauth2RoutingEnabled);
        ProxyRequestHandler handler = new ProxyRequestHandler(
                mock(HttpClient.class),
                mock(RoutingManager.class),
                mock(QueryHistoryManager.class),
                store,
                config);
        handlers.add(handler);
        return handler;
    }

    private static ProxyResponse response(int statusCode, String wwwAuthenticate)
    {
        return new ProxyResponse(
                statusCode,
                ImmutableListMultimap.of(HeaderName.of(WWW_AUTHENTICATE), wwwAuthenticate),
                "");
    }
}
