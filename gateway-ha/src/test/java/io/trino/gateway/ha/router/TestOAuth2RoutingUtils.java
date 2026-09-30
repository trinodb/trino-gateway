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

import com.google.common.hash.Hashing;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class TestOAuth2RoutingUtils
{
    // A fixed authId used across tests so the poll/initiate/challenge/callback legs can be checked
    // for cross-leg agreement (they must all resolve to the same pin key).
    private static final UUID AUTH_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String AUTH_ID_HASH = Hashing.sha256().hashString(AUTH_ID.toString(), StandardCharsets.UTF_8).toString();

    @Test
    void testPinKeyFromRequestPathPoll()
    {
        // The driver poll loop's key is derived from the authId in the path.
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/" + AUTH_ID))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
        // Only the first segment after the prefix is the id.
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/" + AUTH_ID + "/extra"))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
    }

    @Test
    void testPinKeyFromRequestPathPollRejectsNonUuid()
    {
        // Trino mints authId via UUID.randomUUID(); anything else cannot be a real one, so it must
        // never be hashed into a lookup (that would let an attacker probe with arbitrary strings).
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/not-a-uuid")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/../../etc/passwd")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/")).isEmpty();
    }

    @Test
    void testPinKeyFromRequestPathPollRejectsNonCanonicalUuid()
    {
        // UUID.fromString is more lenient than the canonical 36-character lower-case form
        // UUID.randomUUID().toString() always produces: it accepts upper-case hex and shortened or
        // "+"-prefixed groups, silently canonicalizing all of them. None of those are what Trino ever
        // puts on the wire, so none of them should be accepted (and hashed into a lookup) here.
        // AUTH_ID itself has no a-f digits, so upper-casing it would be a no-op; use one that does.
        UUID authIdWithHexLetters = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/" + authIdWithHexLetters.toString().toUpperCase(Locale.ROOT))).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/1-2-3-4-5")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/+1-2-3-4-5")).isEmpty();
    }

    @Test
    void testPinKeyFromRequestPathInitiate()
    {
        // The browser initiate redirect's key is derived from the authIdHash in the path (more
        // specific prefix wins over the poll prefix it is nested under).
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/" + AUTH_ID_HASH))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
    }

    @Test
    void testPinKeyFromRequestPathInitiateRejectsBadHash()
    {
        // Trino's authIdHash is always a 64-char lowercase sha256 hex digest; anything else must
        // never be hashed into a lookup.
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/short")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/" + "g".repeat(64))).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/" + AUTH_ID_HASH + "a")).isEmpty();
        // Uppercase hex is rejected outright, not normalized: Trino never emits it.
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/token/initiate/" + AUTH_ID_HASH.toUpperCase(Locale.ROOT))).isEmpty();
    }

    @Test
    void testPinKeyFromRequestPathNotPinnable()
    {
        // Not pinnable here (callback carries its id only inside the signed state token).
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/oauth2/callback")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath("/v1/statement")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromRequestPath(null)).isEmpty();
    }

    @Test
    void testPinKeyFromChallenge()
    {
        // The 401 challenge's authId (from x_token_server alone) resolves to the same pin key as the
        // poll leg it will be looked up under.
        String challenge = "Bearer x_redirect_server=\"https://coord-a:8443/oauth2/token/initiate/" + AUTH_ID_HASH + "\", "
                + "x_token_server=\"https://coord-a:8443/oauth2/token/" + AUTH_ID + "\"";
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge(challenge))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
    }

    @Test
    void testPinKeyFromChallengeIgnoresRedirectServer()
    {
        // x_redirect_server alone (no x_token_server) is deliberately not enough to record a pin: the
        // pin key is always derived from the authId in x_token_server.
        String redirectServerOnly = "Bearer x_redirect_server=\"https://coord-a:8443/oauth2/token/initiate/" + AUTH_ID_HASH + "\"";
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge(redirectServerOnly)).isEmpty();
    }

    @Test
    void testPinKeyFromChallengeRejectsNonUuidAuthId()
    {
        String challenge = "Bearer x_token_server=\"https://coord-a:8443/oauth2/token/not-a-uuid\"";
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge(challenge)).isEmpty();
    }

    @Test
    void testPinKeyFromChallengeIgnoresNonTokenExchange()
    {
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge("Bearer realm=\"trino\", error=\"invalid_token\"")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge(null)).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromChallenge("")).isEmpty();
    }

    @Test
    void testForceReAuthResponsePollLeg()
    {
        // Trino's HttpTokenPoller maps a 200 body with an "error" field to TokenPollResult.failed,
        // ending the poll cleanly (rather than a generic non-2xx failure) so the client re-auths.
        Response response = OAuth2RoutingUtils.forceReAuthResponse("/oauth2/token/" + AUTH_ID);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
        assertThat((String) response.getEntity()).contains("\"error\"");
    }

    @Test
    void testForceReAuthResponseBrowserLegs()
    {
        // The browser initiate and callback legs have no poll contract to satisfy; a plain 401 ends
        // them (a 200 token-poll body is meaningless to a browser).
        assertThat(OAuth2RoutingUtils.forceReAuthResponse("/oauth2/token/initiate/" + AUTH_ID_HASH).getStatus()).isEqualTo(401);
        assertThat(OAuth2RoutingUtils.forceReAuthResponse("/oauth2/callback").getStatus()).isEqualTo(401);
    }

    @Test
    void testPinKeyFromCallback()
    {
        // The callback is pinned by the authIdHash carried in the state JWT's handler_state claim,
        // so it resolves to the same pin key as the matching initiate/poll legs.
        String state = jwt("{\"aud\":\"trino_oauth_ui\",\"handler_state\":\"" + AUTH_ID_HASH + "\"}");
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "code=xyz&state=" + state))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
        // Order of query params does not matter.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=" + state + "&code=xyz"))
                .hasValue(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH));
    }

    @Test
    void testPinKeyFromCallbackNotPinnable()
    {
        String uiLoginState = jwt("{\"aud\":\"trino_oauth_ui\"}");
        // Browser-UI login (no handler_state) is stateless via the nonce cookie -> not pinned here.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "code=xyz&state=" + uiLoginState)).isEmpty();
        // handler_state present but empty, or not a valid hash shape.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=" + jwt("{\"handler_state\":\"\"}"))).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=" + jwt("{\"handler_state\":\"short\"}"))).isEmpty();
        // Uppercase hex is rejected outright, not normalized: Trino never emits it.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback(
                "/oauth2/callback",
                "state=" + jwt("{\"handler_state\":\"" + AUTH_ID_HASH.toUpperCase(Locale.ROOT) + "\"}"))).isEmpty();
        // Missing / unparseable state, and non-callback paths.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "code=xyz")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=not-a-jwt")).isEmpty();
        // Malformed percent-encoding in state must fall back gracefully, not throw a 500.
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=%")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", "state=%zz")).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/callback", null)).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback("/oauth2/token/" + AUTH_ID, "state=" + jwt("{\"handler_state\":\"" + AUTH_ID_HASH + "\"}"))).isEmpty();
        assertThat(OAuth2RoutingUtils.pinKeyFromCallback(null, null)).isEmpty();
    }

    @Test
    void testPinKeyForAuthIdHashIsNamespacedNotABareRehash()
    {
        // The domain-separation prefix means the pin key is never simply sha256(authIdHash): a pin
        // key must never equal a bare re-hash of the authIdHash computed the same way Trino computes
        // its own hashes, so it can never be confused for, or substituted by, one.
        String bareRehash = Hashing.sha256().hashString(AUTH_ID_HASH, StandardCharsets.UTF_8).toString();
        assertThat(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH)).isNotEqualTo(bareRehash);
    }

    @Test
    void testPinKeyForAuthIdHashIsStableAndDeterministic()
    {
        assertThat(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH))
                .isEqualTo(OAuth2RoutingUtils.pinKeyForAuthIdHash(AUTH_ID_HASH))
                .hasSize(64);
    }

    @Test
    void testRedactForLog()
    {
        assertThat(OAuth2RoutingUtils.redactForLog("/oauth2/token/" + AUTH_ID, null))
                .isEqualTo("/oauth2/token/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/oauth2/token/initiate/" + AUTH_ID_HASH, null))
                .isEqualTo("/oauth2/token/initiate/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/oauth2/callback", "state=abc&code=xyz"))
                .isEqualTo("/oauth2/callback?<redacted>");
        // No query string on the callback: nothing to redact.
        assertThat(OAuth2RoutingUtils.redactForLog("/oauth2/callback", null)).isEqualTo("/oauth2/callback");
        // Unrelated paths are left alone.
        assertThat(OAuth2RoutingUtils.redactForLog("/v1/statement", null)).isEqualTo("/v1/statement");
        assertThat(OAuth2RoutingUtils.redactForLog("/v1/statement", "x=1")).isEqualTo("/v1/statement?x=1");
    }

    @Test
    void testRedactForLogMatchesAnywhereInPath()
    {
        // Some callers (e.g. the proxy log lines) pass the backend URI's path, which carries a
        // proxyTo base path in front of Trino's own path -- redactForLog must still redact the id
        // even though it is not at the start of the string.
        assertThat(OAuth2RoutingUtils.redactForLog("/trino/oauth2/token/" + AUTH_ID, null))
                .isEqualTo("/trino/oauth2/token/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/trino/oauth2/token/initiate/" + AUTH_ID_HASH, null))
                .isEqualTo("/trino/oauth2/token/initiate/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/trino/oauth2/callback", "state=abc&code=xyz"))
                .isEqualTo("/trino/oauth2/callback?<redacted>");
        // A trailing slash on the base path (a double slash ahead of Trino's own leading slash) must
        // not throw off the match either.
        assertThat(OAuth2RoutingUtils.redactForLog("/trino//oauth2/token/" + AUTH_ID, null))
                .isEqualTo("/trino//oauth2/token/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/trino//oauth2/token/initiate/" + AUTH_ID_HASH, null))
                .isEqualTo("/trino//oauth2/token/initiate/<redacted>");
        assertThat(OAuth2RoutingUtils.redactForLog("/trino//oauth2/callback", "state=abc"))
                .isEqualTo("/trino//oauth2/callback?<redacted>");
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
