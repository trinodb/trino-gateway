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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import jakarta.ws.rs.core.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

/**
 * Wire-format knowledge for Trino's OAuth2 token-exchange handshake, kept in one place so the
 * coupling to Trino server internals is explicit and easy to adjust per Trino version.
 * <p>
 * The handshake spans two HTTP clients that share only an {@code authId} (and its hash):
 * <ul>
 *   <li>the driver/CLI poll loop: {@code GET /oauth2/token/{authId}}</li>
 *   <li>the browser: {@code GET /oauth2/token/initiate/{authIdHash}} → IdP → {@code /oauth2/callback}</li>
 * </ul>
 * The {@code authId} is minted by the coordinator that issues the {@code 401} challenge, and only
 * that coordinator holds the in-memory exchange state for it. So every request carrying that
 * {@code authId}/hash must be routed back to the minting coordinator.
 * <p>
 * The minting coordinator advertises both identifiers in the {@code WWW-Authenticate} challenge it
 * returns on the unauthenticated request:
 * <pre>
 *   WWW-Authenticate: Bearer x_redirect_server="https://host/oauth2/token/initiate/{authIdHash}",
 *                            x_token_server="https://host/oauth2/token/{authId}"
 * </pre>
 * Recording both identifiers from that single challenge lets the gateway pin the poll loop
 * ({@code authId}) and the browser initiate redirect ({@code authIdHash}) without ever having to
 * reproduce Trino's hashing of {@code authId}.
 * <p>
 * NOTE: the path and challenge-parameter constants below mirror Trino's token-exchange flow
 * (verified shape as of Trino 446). They are intentionally isolated here; if a future Trino version
 * changes them, this is the only file that needs to change.
 */
public final class OAuth2RoutingUtils
{
    private OAuth2RoutingUtils() {}

    // Trino's token-exchange endpoints. The initiate endpoint is a sub-path of the token endpoint,
    // so the more specific prefix must be checked first.
    public static final String OAUTH2_TOKEN_PATH_PREFIX = "/oauth2/token/";
    public static final String OAUTH2_INITIATE_PATH_PREFIX = "/oauth2/token/initiate/";
    // Trino's OAuth2 callback (the browser leg returning from the IdP). Unlike the token/initiate
    // legs it carries no id in its path — the id rides through the IdP inside the state parameter.
    public static final String OAUTH2_CALLBACK_PATH = "/oauth2/callback";

    // Parameters Trino places in the WWW-Authenticate challenge for the token-exchange flow.
    private static final String TOKEN_SERVER_PARAM = "x_token_server";
    private static final String REDIRECT_SERVER_PARAM = "x_redirect_server";

    // Trino round-trips the id through the IdP inside the "state" query parameter (a signed JWT); its
    // "handler_state" claim is the authIdHash the initiate/poll legs are already pinned by.
    private static final String STATE_PARAM = "state";
    private static final String HANDLER_STATE_CLAIM = "handler_state";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern TOKEN_SERVER_PATTERN = challengeParamPattern(TOKEN_SERVER_PARAM);
    private static final Pattern REDIRECT_SERVER_PATTERN = challengeParamPattern(REDIRECT_SERVER_PARAM);

    static final String REAUTH_MESSAGE =
            "Trino Gateway: the Trino coordinator handling this OAuth2 login is no longer available. Please reconnect to re-authenticate.";
    // Trino's token-poll failure contract: HttpTokenPoller maps a 200 response whose JSON body has an
    // "error" field to TokenPollResult.failed(error) — the same response a coordinator returns for a
    // failed exchange. It surfaces our message and ends the poll loop cleanly, so the next connection
    // re-authenticates against a healthy coordinator. A non-2xx status would not carry the message.
    private static final String REAUTH_TOKEN_POLL_BODY = "{\"error\":\"" + REAUTH_MESSAGE + "\"}";

    private static Pattern challengeParamPattern(String param)
    {
        // matches:  param="<value>"   (value is the advertised server URL)
        return Pattern.compile(Pattern.quote(param) + "\\s*=\\s*\"([^\"]+)\"");
    }

    /**
     * The stable routing key for an in-flight handshake request carried in the request <em>path</em>:
     * the driver poll ({@code /oauth2/token/{authId}}) and the browser initiate
     * ({@code /oauth2/token/initiate/{authIdHash}}). Empty if {@code path} is neither. The browser
     * callback ({@code /oauth2/callback}) carries its id in the {@code state} parameter instead — see
     * {@link #oauthIdFromCallback}.
     */
    public static Optional<String> oauthIdFromRequestPath(String path)
    {
        if (isNullOrEmpty(path)) {
            return Optional.empty();
        }
        if (path.startsWith(OAUTH2_INITIATE_PATH_PREFIX)) {
            return firstSegmentAfter(path, OAUTH2_INITIATE_PATH_PREFIX);
        }
        if (path.startsWith(OAUTH2_TOKEN_PATH_PREFIX)) {
            return firstSegmentAfter(path, OAUTH2_TOKEN_PATH_PREFIX);
        }
        return Optional.empty();
    }

    /**
     * The routing key for an OAuth2 callback request ({@code /oauth2/callback?state=...&code=...}).
     * The callback is the browser leg returning from the IdP; unlike the token/initiate legs it
     * carries no id in its path. Trino round-trips the id through the IdP inside the signed
     * {@code state} JWT, as the {@code handler_state} claim — which is exactly the {@code authIdHash}
     * the initiate and poll legs are already pinned by. We decode the JWT payload (base64url, without
     * verifying the signature — this only selects a backend; the coordinator still verifies it) and
     * return that {@code authIdHash} so the callback is pinned to the same coordinator that minted the
     * handshake, without depending on {@link OAuth2GatewayCookie}. Empty if this is not a callback,
     * the {@code state} is absent, it is a browser-UI login (no {@code handler_state}), or the state
     * cannot be parsed — in which case the caller falls back to normal (cookie/stochastic) routing.
     */
    public static Optional<String> oauthIdFromCallback(String path, String queryString)
    {
        if (isNullOrEmpty(path) || !path.startsWith(OAUTH2_CALLBACK_PATH)) {
            return Optional.empty();
        }
        return stateParam(queryString).flatMap(OAuth2RoutingUtils::handlerStateClaim);
    }

    /**
     * Both routing keys ({@code authId} and {@code authIdHash}) advertised in a {@code 401}
     * token-exchange challenge, or empty if {@code wwwAuthenticate} is not such a challenge.
     */
    public static Set<String> oauthIdsFromChallenge(String wwwAuthenticate)
    {
        if (isNullOrEmpty(wwwAuthenticate) || !wwwAuthenticate.contains(OAUTH2_TOKEN_PATH_PREFIX)) {
            return ImmutableSet.of();
        }
        ImmutableSet.Builder<String> ids = ImmutableSet.builder();
        addIdFromServerUrl(ids, TOKEN_SERVER_PATTERN.matcher(wwwAuthenticate));
        addIdFromServerUrl(ids, REDIRECT_SERVER_PATTERN.matcher(wwwAuthenticate));
        return ids.build();
    }

    /**
     * The response to return when an in-flight handshake's pinned coordinator is gone, so the client
     * fails the dead handshake cleanly and re-authenticates on its next attempt. The browser legs (the
     * initiate redirect and the IdP callback) get a plain {@code 401}; only the driver poll loop gets
     * Trino's token-poll failure contract (HTTP 200 with an {@code error} body).
     */
    public static Response forceReAuthResponse(String requestPath)
    {
        if (requestPath != null
                && (requestPath.startsWith(OAUTH2_INITIATE_PATH_PREFIX) || requestPath.startsWith(OAUTH2_CALLBACK_PATH))) {
            return Response.status(UNAUTHORIZED).entity(REAUTH_MESSAGE).build();
        }
        return Response.ok(REAUTH_TOKEN_POLL_BODY).type(APPLICATION_JSON).build();
    }

    private static void addIdFromServerUrl(ImmutableSet.Builder<String> ids, Matcher matcher)
    {
        if (!matcher.find()) {
            return;
        }
        String serverUrl = matcher.group(1);
        int tokenPathStart = serverUrl.indexOf(OAUTH2_TOKEN_PATH_PREFIX);
        if (tokenPathStart < 0) {
            return;
        }
        // Reuse the request-path logic on the path portion of the advertised URL.
        oauthIdFromRequestPath(serverUrl.substring(tokenPathStart)).ifPresent(ids::add);
    }

    private static Optional<String> firstSegmentAfter(String path, String prefix)
    {
        String remainder = path.substring(prefix.length());
        int nextSlash = remainder.indexOf('/');
        if (nextSlash >= 0) {
            remainder = remainder.substring(0, nextSlash);
        }
        return remainder.isEmpty() ? Optional.empty() : Optional.of(remainder);
    }

    private static Optional<String> stateParam(String queryString)
    {
        if (isNullOrEmpty(queryString)) {
            return Optional.empty();
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(STATE_PARAM)) {
                String value = pair.substring(eq + 1);
                if (value.isEmpty()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(URLDecoder.decode(value, StandardCharsets.UTF_8));
                }
                catch (IllegalArgumentException e) {
                    // Malformed percent-encoding: treat as unparseable and fall back to normal routing.
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> handlerStateClaim(String state)
    {
        // A compact JWS is header.payload.signature; decode the payload and read handler_state.
        int firstDot = state.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : state.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(state.substring(firstDot + 1, secondDot));
            JsonNode claim = OBJECT_MAPPER.readTree(payload).get(HANDLER_STATE_CLAIM);
            if (claim == null || claim.isNull() || isNullOrEmpty(claim.asText())) {
                return Optional.empty();
            }
            return Optional.of(claim.asText());
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }
}
