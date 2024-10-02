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
package io.trino.gateway.ha.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.OAuthConfiguration;
import io.trino.gateway.ha.domain.Result;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.hash.Hashing.sha256;
import static com.nimbusds.oauth2.sdk.ResponseType.CODE;
import static com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet.NONCE_CLAIM_NAME;
import static jakarta.ws.rs.core.Response.Status.FOUND;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

public class LbOAuthManager
{
    private static final Logger log = Logger.get(LbOAuthManager.class);
    /**
     * Cookie key to pass the token.
     */
    private final OAuthConfiguration oauthConfig;
    private final Map<String, String> pagePermissions;

    public LbOAuthManager(OAuthConfiguration configuration, Map<String, String> pagePermissions)
    {
        this.oauthConfig = configuration;
        this.pagePermissions = pagePermissions.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(toImmutableMap(entry -> entry.getKey().toUpperCase(ENGLISH), Map.Entry::getValue));
    }

    public String getUserIdField()
    {
        return oauthConfig.getUserIdField();
    }

    public Optional<String> getPrivilegesField()
    {
        return Optional.ofNullable(oauthConfig.getPrivilegesField());
    }

    /**
     * Exchanges authorization code for access token.
     * Sets it in a cookie and redirects back to a location.
     *
     * @param code the authorization code obtained from the authorization server
     * @param nonce the nonce used in authentication request before hash
     * @param redirectLocation the application path to redirect back to
     * @return redirect response with a Set-Cookie header
     */
    public Response exchangeCodeForToken(String code, String nonce, String redirectLocation)
    {
        TokenRequest tokenRequest = new TokenRequest(
                oauthConfig.getTokenEndpoint(),
                new ClientSecretBasic(new ClientID(oauthConfig.getClientId()), new Secret(oauthConfig.getClientSecret())),
                new AuthorizationCodeGrant(new AuthorizationCode(code), oauthConfig.getRedirectUrl()),
                null);

        TokenResponse tokenResponse;
        try {
            tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
        }
        catch (ParseException | IOException e) {
            log.error("Failed to parse token response: %s", e.getMessage());
            return buildUnauthorizedResponse();
        }

        if (!tokenResponse.indicatesSuccess()) {
            HTTPResponse httpResponse = tokenResponse.toErrorResponse().toHTTPResponse();
            log.error("token response failed with code %d - %s", httpResponse.getStatusCode(), httpResponse.getBody());
            return buildUnauthorizedResponse();
        }

        OIDCTokenResponse successResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
        String idToken = successResponse.getOIDCTokens().getIDToken().serialize();

        Optional<Claim> result = getClaimsFromIdToken(idToken)
                .map(map -> map.get(NONCE_CLAIM_NAME))
                .filter(nonceInClaim -> nonceInClaim.asString().equals(hashNonce(nonce)));
        if (result.isEmpty()) {
            log.error("Invalid nonce");
            return buildUnauthorizedResponse();
        }
        return Response.status(FOUND)
                .location(oauthConfig.getRedirectWebUrl().orElse(URI.create(redirectLocation)))
                .cookie(SessionCookie.getTokenCookie(idToken))
                .cookie(OidcCookie.delete())
                .build();
    }

    /**
     * Redirects to the authorization provider for the authorization code.
     *
     * @return redirect response to the authorization provider
     */
    public Response getAuthorizationCodeResponse()
    {
        State state = new State();
        Nonce nonce = new Nonce();
        AuthenticationRequest request = new AuthenticationRequest.Builder(
                CODE,
                new Scope(oauthConfig.getScopes().toArray(String[]::new)),
                new ClientID(oauthConfig.getClientId()),
                oauthConfig.getRedirectUrl())
                .endpointURI(oauthConfig.getAuthorizationEndpoint())
                .state(state)
                .nonce(new Nonce(hashNonce(nonce.getValue())))
                .build();
        return Response.ok(Result.ok("Ok", request.toURI().toString()))
                .cookie(OidcCookie.create(state.getValue(), nonce.getValue()))
                .build();
    }

    /**
     * Verifies if the id token is valid. If valid, it returns a map with the claims,
     * else an empty optional. idToken docs: https://www.oauth
     * .com/oauth2-servers/openid-connect/id-tokens/
     *
     * @param idToken the access token provided back by the authorization server.
     * @return a map with the token claims
     */
    public Optional<Map<String, Claim>> getClaimsFromIdToken(String idToken)
    {
        try {
            DecodedJWT jwt = JWT.decode(idToken);

            URI jwkEndpoint = oauthConfig.getJwkEndpoint();
            JwkProvider provider = new UrlJwkProvider(jwkEndpoint.toURL());
            Jwk jwk = provider.get(jwt.getKeyId());
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

            if (LbTokenUtil.validateToken(idToken, publicKey, jwt.getIssuer(), Optional.ofNullable(oauthConfig.getAudiences()))) {
                return Optional.of(jwt.getClaims());
            }
        }
        catch (Exception exc) {
            log.error(exc, "Could not validate token or get claims from it.");
        }
        return Optional.empty();
    }

    public List<String> processPagePermissions(List<String> roles)
    {
        for (String role : roles) {
            String value = pagePermissions.get(role);
            if (value == null) {
                return Collections.emptyList();
            }
        }
        return roles.stream()
                .flatMap(role -> Stream.of(pagePermissions.get(role).split("_")))
                .distinct().toList();
    }

    private String hashNonce(String nonce)
    {
        return sha256()
                .hashString(nonce, UTF_8)
                .toString();
    }

    public static Response buildUnauthorizedResponse()
    {
        return Response.status(UNAUTHORIZED)
                .cookie(OidcCookie.delete())
                .build();
    }
}
