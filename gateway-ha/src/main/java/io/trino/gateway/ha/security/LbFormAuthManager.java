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

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.airlift.log.Logger;
import io.dropwizard.auth.basic.BasicCredentials;
import io.trino.gateway.ha.config.FormAuthConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.request.RestLoginRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class LbFormAuthManager
{
    private static final Logger log = Logger.get(LbFormAuthManager.class);
    /**
     * Cookie key to pass the token.
     */
    private final LbKeyProvider lbKeyProvider;
    private final Map<String, UserConfiguration> presetUsers;
    private final Map<String, String> pagePermissions;
    private final LbLdapClient lbLdapClient;

    public LbFormAuthManager(FormAuthConfiguration configuration,
            Map<String, UserConfiguration> presetUsers,
            Map<String, String> pagePermissions)
    {
        this.presetUsers = presetUsers;
        this.pagePermissions = pagePermissions;

        if (configuration != null) {
            this.lbKeyProvider = new LbKeyProvider(configuration
                    .getSelfSignKeyPair());
        }
        else {
            this.lbKeyProvider = null;
        }

        if (configuration != null && configuration.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(configuration.getLdapConfigPath()));
        }
        else {
            lbLdapClient = null;
        }
    }

    public String getUserIdField()
    {
        return "sub";
    }

    /**
     * Login REST API
     *
     * @param loginForm {@link RestLoginRequest}
     * @return token
     */
    public Result<?> processRESTLogin(RestLoginRequest loginForm)
    {
        if (authenticate(new BasicCredentials(loginForm.getUsername(), loginForm.getPassword()))) {
            String token = getSelfSignedToken(loginForm.getUsername());
            return Result.ok(Map.of("token", token));
        }
        return Result.fail("Authentication failed.");
    }

    /**
     * Verifies if the id token is valid. If valid, it returns a map with the claims,
     * else an empty optional. idToken docs: https://www.oauth
     * .com/oauth2-servers/openid-connect/id-tokens/
     *
     * @param idToken the access token provided back by the authorization server.
     * @return a map with the token claims
     * @throws Exception is thrown if the access token is invalid
     */
    public Optional<Map<String, Claim>> getClaimsFromIdToken(String idToken)
    {
        try {
            DecodedJWT jwt = JWT.decode(idToken);

            if (LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.empty())) {
                return Optional.of(jwt.getClaims());
            }
        }
        catch (Exception exc) {
            log.error(exc, "Could not validate token or get claims from it.");
        }
        return Optional.empty();
    }

    private String getSelfSignedToken(String username)
    {
        String token = "";

        try {
            Algorithm algorithm = Algorithm.RSA256(lbKeyProvider.getRsaPublicKey(),
                    lbKeyProvider.getRsaPrivateKey());

            Map<String, Object> headers = Map.of("alg", "RS256");

            token = JWT.create()
                    .withHeader(headers)
                    .withIssuer(SessionCookie.SELF_ISSUER_ID)
                    .withSubject(username)
                    .sign(algorithm);
        }
        catch (JWTCreationException exception) {
            // Invalid Signing configuration / Couldn't convert Claims.
            log.error("Error while creating the selfsigned token JWT");
            throw exception;
        }
        return token;
    }

    public boolean authenticate(BasicCredentials credentials)
    {
        if (lbLdapClient != null
                && lbLdapClient.authenticate(credentials.getUsername(),
                credentials.getPassword())) {
            return true;
        }

        if (presetUsers != null) {
            UserConfiguration user = presetUsers.get(credentials.getUsername());
            return user != null
                    && user.password().equals(credentials.getPassword());
        }

        return false;
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
}
