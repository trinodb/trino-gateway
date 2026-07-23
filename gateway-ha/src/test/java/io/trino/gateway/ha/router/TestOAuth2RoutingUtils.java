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
package io.trino.gateway.ha.router;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

final class TestOAuth2RoutingUtils
{
    @Test
    void testOauthIdFromRequestPath()
    {
        // Driver poll loop: key is the authId.
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/oauth2/token/abc123")).hasValue("abc123");
        // Browser initiate redirect: key is the authIdHash (more specific prefix wins).
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/oauth2/token/initiate/hashXYZ")).hasValue("hashXYZ");
        // Only the first segment after the prefix is the id.
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/oauth2/token/abc123/extra")).hasValue("abc123");

        // Not pinnable here (callback carries its id only inside the signed state token).
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/oauth2/callback")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/oauth2/token/")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath("/v1/statement")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromRequestPath(null)).isEmpty();
    }

    @Test
    void testOauthIdsFromChallenge()
    {
        // A single 401 challenge advertises both ids; recording both pins the poll loop and the
        // browser initiate to the minting coordinator without reproducing Trino's authId hashing.
        String challenge = "Bearer x_redirect_server=\"https://coord-a:8443/oauth2/token/initiate/HASH9\", "
                + "x_token_server=\"https://coord-a:8443/oauth2/token/AUTH9\"";
        assertThat(OAuth2RoutingUtils.oauthIdsFromChallenge(challenge))
                .containsExactlyInAnyOrder("HASH9", "AUTH9");
    }

    @Test
    void testOauthIdsFromChallengeIgnoresNonTokenExchange()
    {
        assertThat(OAuth2RoutingUtils.oauthIdsFromChallenge("Bearer realm=\"trino\", error=\"invalid_token\"")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdsFromChallenge(null)).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdsFromChallenge("")).isEmpty();
    }

    @Test
    void testForceReAuthResponseForPollLeg()
    {
        // Trino's HttpTokenPoller maps a 200 body with an "error" field to TokenPollResult.failed,
        // ending the poll cleanly (rather than a generic non-2xx failure) so the client re-auths.
        Response response = OAuth2RoutingUtils.forceReAuthResponse("/oauth2/token/abc123");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
        assertThat((String) response.getEntity()).contains("\"error\"");
    }

    @Test
    void testForceReAuthResponseForBrowserLegs()
    {
        // The browser initiate and callback legs have no poll contract to satisfy; a plain 401 ends
        // them (a 200 token-poll body is meaningless to a browser).
        assertThat(OAuth2RoutingUtils.forceReAuthResponse("/oauth2/token/initiate/hash9").getStatus()).isEqualTo(401);
        assertThat(OAuth2RoutingUtils.forceReAuthResponse("/oauth2/callback").getStatus()).isEqualTo(401);
    }

    @Test
    void testOauthIdFromCallback()
    {
        // The callback is pinned by the authIdHash carried in the state JWT's handler_state claim,
        // so it routes to the same coordinator as the matching initiate/poll legs.
        String state = jwt("{\"aud\":\"trino_oauth_ui\",\"handler_state\":\"HASH9\"}");
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "code=xyz&state=" + state))
                .hasValue("HASH9");
        // Order of query params does not matter.
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "state=" + state + "&code=xyz"))
                .hasValue("HASH9");
    }

    @Test
    void testOauthIdFromCallbackNotPinnable()
    {
        String uiLoginState = jwt("{\"aud\":\"trino_oauth_ui\"}");
        // Browser-UI login (no handler_state) is stateless via the nonce cookie -> not pinned here.
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "code=xyz&state=" + uiLoginState)).isEmpty();
        // handler_state present but empty.
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "state=" + jwt("{\"handler_state\":\"\"}"))).isEmpty();
        // Missing / unparseable state, and non-callback paths.
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "code=xyz")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "state=not-a-jwt")).isEmpty();
        // Malformed percent-encoding in state must fall back gracefully, not throw a 500.
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "state=%")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", "state=%zz")).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/callback", null)).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback("/oauth2/token/abc123", "state=" + jwt("{\"handler_state\":\"HASH9\"}"))).isEmpty();
        assertThat(OAuth2RoutingUtils.oauthIdFromCallback(null, null)).isEmpty();
    }

    private static String jwt(String payloadJson)
    {
        String header = base64Url("{\"alg\":\"HS256\"}");
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
