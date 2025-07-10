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

import java.util.Map;
import java.util.Optional;

public class FormAuthenticator
        implements IdTokenAuthenticator
{
    private static final Logger log = Logger.get(FormAuthenticator.class);
    private final LbFormAuthManager formAuthManager;

    public FormAuthenticator(LbFormAuthManager formAuthManager)
    {
        this.formAuthManager = formAuthManager;
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
        String userIdField = formAuthManager.getUserIdField();
        String privilegesField = formAuthManager.getPrivilegesField();

        Map<String, Claim> claims = null;
        try {
            claims = formAuthManager.getClaimsFromIdToken(idToken).orElseThrow();
        }
        catch (Exception e) {
            return Optional.empty();
        }
        String userId = claims.get(userIdField).asString().replace("\"", "");

        Claim claim = claims.get(privilegesField);
        if (claim == null || claim.asString() == null) {
            log.warn("No privileges found for user %s in idToken", userId);
            throw new AuthenticationException("No privileges found for user " + userId + " in idToken");
        }

        String privileges = claim.asString();
        if (privileges == null) {
            privileges = "";
        }
        return Optional.of(new LbPrincipal(userId, privileges));
    }
}
