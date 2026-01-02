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

import static io.trino.gateway.ha.security.UserMapping.createUserMapping;

public class LbJwtAuthenticator
        implements IdTokenAuthenticator
{
    private static final Logger log = Logger.get(LbJwtAuthenticator.class);

    private final LbJwtManager jwtManager;
    private final AuthorizationManager authorizationManager;
    private final UserMapping userMapping;

    public LbJwtAuthenticator(LbJwtManager jwtManager,
            AuthorizationManager authorizationManager)
    {
        this.jwtManager = jwtManager;
        this.authorizationManager = authorizationManager;
        this.userMapping = createUserMapping(jwtManager.getUserMappingPattern(), jwtManager.getUserMappingFile());
    }

    /**
     * If the JWT token is valid and has the right claims, it returns the principal,
     * otherwise is returns an empty optional.
     *
     * @param token JWT token
     * @return an optional principal
     */
    @Override
    public Optional<LbPrincipal> authenticate(String token)
            throws AuthenticationException
    {
        Optional<Map<String, Claim>> claimsOptional = jwtManager.getClaimsFromToken(token);
        if (claimsOptional.isEmpty()) {
            log.error("JWT token verification failed");
            throw new AuthenticationException("JWT token verification failed");
        }

        Map<String, Claim> claims = claimsOptional.orElseThrow();

        String principalField = jwtManager.getPrincipalField();
        if (!claims.containsKey(principalField)) {
            log.error("Required principal field %s not found in JWT token", principalField);
            throw new AuthenticationException("Principal field does not exist in JWT token");
        }

        String originalUserId = claims.get(principalField).asString();
        // Apply user mapping to transform the username if configured
        String mappedUserId = userMapping.mapUser(originalUserId);
        if (log.isDebugEnabled()) {
            log.debug("JWT principal mapping: %s -> %s", originalUserId, mappedUserId);
        }

        // Get privileges from the authorization manager
        Optional<String> privileges = authorizationManager.getPrivileges(mappedUserId);

        return Optional.of(new LbPrincipal(mappedUserId, privileges));
    }
}
