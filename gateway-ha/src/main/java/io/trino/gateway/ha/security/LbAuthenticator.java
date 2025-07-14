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

import com.auth0.jwt.interfaces.Claim;
import io.airlift.log.Logger;
import io.trino.gateway.ha.security.util.AuthenticationException;
import io.trino.gateway.ha.security.util.IdTokenAuthenticator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LbAuthenticator
        implements IdTokenAuthenticator
{
    private static final Logger log = Logger.get(LbAuthenticator.class);
    private final LbOAuthManager oauthManager;
    private final AuthorizationManager authorizationManager;

    public LbAuthenticator(LbOAuthManager oauthManager,
            AuthorizationManager authorizationManager)
    {
        this.oauthManager = oauthManager;
        this.authorizationManager = authorizationManager;
    }

    /**
     * If the idToken is valid and has the right claims, it returns the principal,
     * otherwise is returns an empty optional.
     *
     * @param idToken idToken from authorization server
     * @return an optional principal
     */
    @Override
    public Optional<LbPrincipal> authenticate(String idToken)
            throws AuthenticationException
    {
        String userIdField = oauthManager.getUserIdField();
        Optional<String> privilegesField = oauthManager.getPrivilegesField();
        if (privilegesField.isPresent()) {
            Map<String, Claim> claims = oauthManager.getClaimsFromIdToken(idToken).orElseThrow();
            Claim userIdClaim = claims.get(userIdField);
            if (userIdClaim == null) {
                log.error("Required userId field %s not found", userIdField);
                throw new AuthenticationException("UserId field does not exist");
            }
            String userId = userIdClaim.asString().replace("\"", "");

            Claim claim = claims.get(privilegesField.orElseThrow());
            if (claim == null) {
                log.error("Required privileges field %s not found", privilegesField.orElseThrow());
                throw new AuthenticationException("Privileges field does not exist");
            }
            Optional<String> privileges;
            List<String> roles = claim.asList(String.class);
            if (roles != null) {
                // claim is List<String>
                privileges = Optional.of(String.join("_", roles));
            }
            else {
                // claim is String
                String role = claim.asString();
                privileges = Optional.ofNullable(role);
            }

            return Optional.of(new LbPrincipal(userId, privileges));
        }
        return oauthManager
                .getClaimsFromIdToken(idToken)
                .map(c -> c.get(userIdField))
                .map(Object::toString)
                .map(s -> s.replace("\"", ""))
                .map(sub -> new LbPrincipal(sub, authorizationManager.getPrivileges(sub)));
    }
}
