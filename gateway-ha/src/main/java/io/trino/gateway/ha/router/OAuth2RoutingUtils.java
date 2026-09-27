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
import com.google.common.hash.Hashing;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
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
 * The {@code oauth2_routing} table is keyed by a pin key, never by the raw {@code authId} or
 * {@code authIdHash}: the {@code authId} is enough to poll for the token, and the
 * {@code authIdHash} is enough to drive the browser initiate leg. See {@link #pinKeyForAuthIdHash}.
 * <p>
 * The minting coordinator advertises both identifiers in the {@code WWW-Authenticate} challenge it
 * returns on the unauthenticated request:
 * <pre>
 *   WWW-Authenticate: Bearer x_redirect_server="https://host/oauth2/token/initiate/{authIdHash}",
 *                            x_token_server="https://host/oauth2/token/{authId}"
 * </pre>
 * Only {@code x_token_server} (the raw {@code authId}) is used to record the pin. The
 * {@code authIdHash} is derived locally, so one row covers both the poll and initiate legs.
 * <p>
 * NOTE: the path and challenge-parameter constants below mirror Trino's token-exchange flow
 * (verified against Trino 483). They are intentionally isolated here; if a future Trino version
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

    // Placeholder for ids in logged paths: never log a raw authId, authIdHash or pin key.
    private static final String REDACTED = "<redacted>";

    // Domain separation, not a secret: a pin key never equals a bare sha256 of the authIdHash.
    private static final String PIN_KEY_PREFIX = "tgw-oauth2-pin:";

    // Trino's authIdHash is a lower-case sha256 hex digest.
    private static final Pattern HEX_64_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private static final int AUTH_ID_STRING_LENGTH = 36;

    // Parameters Trino places in the WWW-Authenticate challenge for the token-exchange flow.
    private static final String TOKEN_SERVER_PARAM = "x_token_server";

    // Trino round-trips the id through the IdP inside the "state" query parameter (a signed JWT); its
    // "handler_state" claim is the authIdHash, which maps to the same pin key as the other legs.
    private static final String STATE_PARAM = "state";
    private static final String HANDLER_STATE_CLAIM = "handler_state";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern TOKEN_SERVER_PATTERN = challengeParamPattern(TOKEN_SERVER_PARAM);

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
     * The pin-store lookup key for a handshake request carried in the path: the driver poll
     * ({@code /oauth2/token/{authId}}) or the browser initiate ({@code /oauth2/token/initiate/{authIdHash}}).
     * Empty if {@code path} is neither, or the id is not a canonical lower-case UUID (poll) or 64
     * lower-case hex chars (initiate). The callback is handled by {@link #pinKeyFromCallback}.
     */
    public static Optional<String> pinKeyFromRequestPath(String path)
    {
        if (isNullOrEmpty(path)) {
            return Optional.empty();
        }
        if (path.startsWith(OAUTH2_INITIATE_PATH_PREFIX)) {
            return firstSegmentAfter(path, OAUTH2_INITIATE_PATH_PREFIX)
                    .filter(OAuth2RoutingUtils::isHex64)
                    .map(OAuth2RoutingUtils::pinKeyForAuthIdHash);
        }
        if (path.startsWith(OAUTH2_TOKEN_PATH_PREFIX)) {
            return firstSegmentAfter(path, OAUTH2_TOKEN_PATH_PREFIX)
                    .flatMap(OAuth2RoutingUtils::parseAuthId)
                    .map(authId -> pinKeyForAuthIdHash(hashAuthId(authId)));
        }
        return Optional.empty();
    }

    /**
     * The pin-store lookup key for an OAuth2 callback ({@code /oauth2/callback?state=...&code=...}).
     * The id is the {@code handler_state} claim (the {@code authIdHash}) of the signed {@code state}
     * JWT. The signature is not verified here: this only selects a backend and the coordinator
     * verifies it. Empty if the claim is absent or malformed, in which case normal routing applies.
     */
    public static Optional<String> pinKeyFromCallback(String path, String queryString)
    {
        if (isNullOrEmpty(path) || !path.startsWith(OAUTH2_CALLBACK_PATH)) {
            return Optional.empty();
        }
        return stateParam(queryString)
                .flatMap(OAuth2RoutingUtils::handlerStateClaim)
                .filter(OAuth2RoutingUtils::isHex64)
                .map(OAuth2RoutingUtils::pinKeyForAuthIdHash);
    }

    /**
     * The pin-store lookup/write key for a {@code 401} token-exchange challenge, derived from the
     * {@code authId} in {@code x_token_server}. Empty if {@code wwwAuthenticate} is not such a
     * challenge, or its {@code authId} is not a valid UUID. {@code x_redirect_server} is not used,
     * because the {@code authIdHash} is derived from the {@code authId}.
     */
    public static Optional<String> pinKeyFromChallenge(String wwwAuthenticate)
    {
        if (isNullOrEmpty(wwwAuthenticate) || !wwwAuthenticate.contains(OAUTH2_TOKEN_PATH_PREFIX)) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN_SERVER_PATTERN.matcher(wwwAuthenticate);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String serverUrl = matcher.group(1);
        int tokenPathStart = serverUrl.indexOf(OAUTH2_TOKEN_PATH_PREFIX);
        if (tokenPathStart < 0) {
            return Optional.empty();
        }
        return pinKeyFromRequestPath(serverUrl.substring(tokenPathStart));
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

    /**
     * A form of an OAuth2 request path (and, for the callback, its query string) safe to log: the
     * {@code authId}, {@code authIdHash} or {@code state} portion is replaced with {@value #REDACTED}.
     * The segments are matched anywhere in {@code path}, because callers may pass a backend URI path
     * with a {@code proxyTo} base path in front (for example {@code /trino/oauth2/token/<uuid>}).
     */
    public static String redactForLog(String path, String queryString)
    {
        if (isNullOrEmpty(path)) {
            return path;
        }
        int initiateIndex = path.indexOf(OAUTH2_INITIATE_PATH_PREFIX);
        if (initiateIndex >= 0) {
            return path.substring(0, initiateIndex) + OAUTH2_INITIATE_PATH_PREFIX + REDACTED;
        }
        int tokenIndex = path.indexOf(OAUTH2_TOKEN_PATH_PREFIX);
        if (tokenIndex >= 0) {
            return path.substring(0, tokenIndex) + OAUTH2_TOKEN_PATH_PREFIX + REDACTED;
        }
        int callbackIndex = path.indexOf(OAUTH2_CALLBACK_PATH);
        if (callbackIndex >= 0) {
            return path.substring(0, callbackIndex) + OAUTH2_CALLBACK_PATH + (queryString != null ? "?" + REDACTED : "");
        }
        return path + (queryString != null ? "?" + queryString : "");
    }

    /**
     * The {@code oauth2_routing} key for an {@code authIdHash}: {@code sha256("tgw-oauth2-pin:" + authIdHash)}.
     */
    public static String pinKeyForAuthIdHash(String authIdHash)
    {
        return sha256Hex(PIN_KEY_PREFIX + authIdHash);
    }

    /**
     * Mirrors Trino's {@code OAuth2TokenExchange.hashAuthId}: sha256 of the canonical UUID string.
     */
    private static String hashAuthId(UUID authId)
    {
        return sha256Hex(authId.toString());
    }

    private static String sha256Hex(String value)
    {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }

    private static boolean isHex64(String value)
    {
        return HEX_64_PATTERN.matcher(value).matches();
    }

    private static Optional<UUID> parseAuthId(String segment)
    {
        // Trino mints authId with UUID.randomUUID().toString(). UUID.fromString also accepts shortened
        // groups and upper-case hex, so require the canonical form instead of normalizing it.
        if (segment.length() != AUTH_ID_STRING_LENGTH) {
            return Optional.empty();
        }
        try {
            UUID authId = UUID.fromString(segment);
            return authId.toString().equals(segment) ? Optional.of(authId) : Optional.empty();
        }
        catch (IllegalArgumentException e) {
            // Not a UUID, fall back to normal routing.
            return Optional.empty();
        }
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
        catch (IllegalArgumentException | IOException e) {
            // Malformed base64url payload or unparseable JSON: fall back to normal routing.
            return Optional.empty();
        }
    }
}
