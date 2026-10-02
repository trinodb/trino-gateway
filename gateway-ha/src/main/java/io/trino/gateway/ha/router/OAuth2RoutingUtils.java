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
 * <b>The routing key stored and looked up is never the raw {@code authId} or {@code authIdHash}.</b>
 * Trino's {@code authIdHash} is itself just {@code sha256(authId)} (see
 * {@code OAuth2TokenExchange.hashAuthId}), and a driver poll ({@code GET /oauth2/token/{authId}})
 * carries the raw {@code authId} in the URL. If the gateway stored rows keyed by {@code authId} or
 * {@code authIdHash} directly, anyone who could read the {@code oauth2_routing} table would have
 * both values for a handshake and could poll {@code /oauth2/token/{authId}} on the pinned
 * coordinator to steal the token the real client is waiting for. Instead, every leg derives Trino's
 * {@code authIdHash} locally and hashes it again, under a fixed prefix that domain-separates this
 * hash from a bare {@code sha256(authId)}, into a {@link #pinKeyForAuthIdHash pin key}. The table
 * therefore stores neither {@code authId} nor {@code authIdHash}, and {@code pin_key} cannot be
 * inverted back to either one; it is never itself written to a log line either. See
 * {@link #pinKeyForAuthIdHash}.
 * <p>
 * The minting coordinator advertises both identifiers in the {@code WWW-Authenticate} challenge it
 * returns on the unauthenticated request:
 * <pre>
 *   WWW-Authenticate: Bearer x_redirect_server="https://host/oauth2/token/initiate/{authIdHash}",
 *                            x_token_server="https://host/oauth2/token/{authId}"
 * </pre>
 * Only {@code x_token_server} (the raw {@code authId}) is used to record the pin: its
 * {@code authIdHash} is derived locally by hashing the {@code authId}, exactly as the poll leg does,
 * so one row covers both the poll and initiate legs without trusting anything the challenge response
 * advertises about the hash itself.
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

    // Replaces any id in a logged request path/query — never log a raw authId/authIdHash or a pin key.
    private static final String REDACTED = "<redacted>";

    // The fixed prefix pin keys are hashed under (see pinKeyForAuthIdHash). This is domain
    // separation, not secrecy: the prefix is public (it is right here in the source), and hiding it
    // would add no protection. What it buys is that a pin key is never equal to a bare sha256(authId)
    // computed anywhere else (Trino's own authIdHash included), so it cannot be confused for, or
    // substituted by, that value.
    private static final String PIN_KEY_PREFIX = "tgw-oauth2-pin:";

    // Trino's authIdHash is always a lower-case sha256 hex digest (64 chars); validate any hash we did
    // not compute ourselves (the initiate path segment, the callback's handler_state claim) against
    // this exact shape before trusting it, rather than hashing arbitrary attacker-controlled input
    // into a pin key. Uppercase hex is rejected rather than normalized: Trino never emits it, so
    // accepting it would only widen the set of inputs treated as valid ids for no reason.
    private static final Pattern HEX_64_PATTERN = Pattern.compile("[0-9a-f]{64}");

    // The length of a canonical UUID string ("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"), i.e. what
    // UUID.randomUUID().toString() always produces -- see parseAuthId.
    private static final int AUTH_ID_STRING_LENGTH = 36;

    // Parameters Trino places in the WWW-Authenticate challenge for the token-exchange flow.
    private static final String TOKEN_SERVER_PARAM = "x_token_server";

    // Trino round-trips the id through the IdP inside the "state" query parameter (a signed JWT); its
    // "handler_state" claim is the authIdHash the initiate/poll legs are already pinned by.
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
     * The pin-store lookup key for an in-flight handshake request carried in the request
     * <em>path</em>: the driver poll ({@code /oauth2/token/{authId}}) and the browser initiate
     * ({@code /oauth2/token/initiate/{authIdHash}}). Empty if {@code path} is neither, or if the id
     * in the path does not have the expected shape (the canonical, lower-case 36-character form of a
     * UUID for the poll leg -- {@code segment.length() == 36 && UUID.fromString(segment).toString()
     * .equals(segment)}, not merely anything {@code UUID.fromString} accepts, which is looser; 64
     * lower-case hex chars for the initiate leg) — never hashed into a lookup on unvalidated input.
     * The browser callback ({@code /oauth2/callback}) carries its id in the {@code state} parameter
     * instead — see {@link #pinKeyFromCallback}.
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
     * The pin-store lookup key for an OAuth2 callback request
     * ({@code /oauth2/callback?state=...&code=...}). The callback is the browser leg returning from
     * the IdP; unlike the token/initiate legs it carries no id in its path. Trino round-trips the id
     * through the IdP inside the signed {@code state} JWT, as the {@code handler_state} claim — which
     * is exactly the {@code authIdHash} the initiate and poll legs are already pinned by. We decode
     * the JWT payload (base64url, without verifying the signature — this only selects a backend; the
     * coordinator still verifies it), validate the claim looks like a sha256 hex digest, and hash it
     * into the same pin key the initiate/poll legs use. Empty if this is not a callback, the
     * {@code state} is absent, it is a browser-UI login (no {@code handler_state}), the claim is not a
     * valid hash shape, or the state cannot be parsed — in which case the caller falls back to normal
     * (cookie/stochastic) routing.
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
     * The pin-store lookup/write key for a {@code 401} token-exchange challenge, derived <em>only</em>
     * from the {@code authId} advertised in {@code x_token_server}. Empty if {@code wwwAuthenticate}
     * is not such a challenge, or its {@code authId} is not a valid UUID.
     * <p>
     * {@code x_redirect_server} (which advertises {@code authIdHash}) is deliberately not consulted:
     * the same {@code authIdHash} is derived here by hashing {@code authId}, exactly as the poll and
     * initiate legs derive it, so recording one row from {@code authId} alone covers every leg without
     * trusting a second, redundant identifier off the wire.
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
        // Reuse the request-path logic (poll-leg branch) on the path portion of the advertised URL.
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
     * A redacted form of an OAuth2 request path (and, for the callback, its query string) safe to
     * write to a log line: the {@code authId}/{@code authIdHash}/{@code state} portion — which lets a
     * DB or log reader impersonate the poll, initiate, or callback leg — is replaced with
     * {@value #REDACTED}. Non-OAuth2 paths are returned unchanged.
     * <p>
     * The token/initiate/callback segments are matched <em>anywhere</em> in {@code path}, not only as
     * a prefix: some callers pass the backend URI's path, which carries a {@code proxyTo} base path in
     * front of it (e.g. {@code /trino/oauth2/token/<uuid>}, or with a trailing slash on the base path,
     * {@code /trino//oauth2/token/<uuid>}), so anchoring the match to the start of the string would
     * leave those forms unredacted.
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
     * Hashes an {@code authIdHash} (itself already {@code sha256(authId)}, Trino's own hash — see
     * {@code OAuth2TokenExchange.hashAuthId}) into the key the {@code oauth2_routing} table is keyed
     * by, under the {@value #PIN_KEY_PREFIX} domain-separation prefix (see that field). The
     * {@code oauth2_routing} table stores neither {@code authId} nor {@code authIdHash} — only this
     * derived {@code pin_key} — and {@code pin_key} cannot be inverted back to either one, so a reader
     * of the table or its logs can never recover the wire-visible id.
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
        // HEX_64_PATTERN is anchored to exactly 64 characters via matches(), so no separate length
        // check is needed here.
        return HEX_64_PATTERN.matcher(value).matches();
    }

    private static Optional<UUID> parseAuthId(String segment)
    {
        // Trino mints authId via UUID.randomUUID().toString(): always the canonical 36-character
        // lower-case form. UUID.fromString is far more lenient than that -- it accepts shortened
        // groups ("1-2-3-4-5"), upper-case hex, and a leading "+" on a group, silently canonicalizing
        // all of them -- so a real authId would still be recognized if we hashed the parsed result's
        // own toString(). Require the segment to already be in that exact canonical form instead of
        // normalizing it, for the same reason the initiate/callback legs reject upper-case authIdHash
        // rather than lower-casing it: accepting non-canonical input here would only widen the set of
        // strings treated as a valid authId for no reason, since Trino itself never emits one.
        if (segment.length() != AUTH_ID_STRING_LENGTH) {
            return Optional.empty();
        }
        try {
            UUID authId = UUID.fromString(segment);
            return authId.toString().equals(segment) ? Optional.of(authId) : Optional.empty();
        }
        catch (IllegalArgumentException e) {
            // Not a UUID: cannot be a real authId, so there is nothing valid to hash. Fall back to
            // normal routing rather than looking up garbage.
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
