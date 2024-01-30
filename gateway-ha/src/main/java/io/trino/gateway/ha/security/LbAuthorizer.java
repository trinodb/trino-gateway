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

import io.airlift.log.Logger;
import io.dropwizard.auth.Authorizer;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.container.ContainerRequestContext;

public class LbAuthorizer
        implements Authorizer<LbPrincipal>
{
    private static final Logger log = Logger.get(LbAuthorizer.class);
    private final AuthorizationConfiguration configuration;

    public LbAuthorizer(AuthorizationConfiguration configuration)
    {
        this.configuration = configuration;
    }

    @Override
    public boolean authorize(LbPrincipal principal,
            String role,
            @Nullable ContainerRequestContext ctx)
    {
        switch (role) {
            case "ADMIN":
                log.info("User '%s' with memberOf(%s) was identified as ADMIN(%s)",
                        principal.getName(), principal.getMemberOf(), configuration.getAdmin());
                return principal.getMemberOf()
                        .filter(m -> m.matches(configuration.getAdmin()))
                        .isPresent();
            case "USER":
                log.info("User '%s' with memberOf(%s) identified as USER(%s)",
                        principal.getName(), principal.getMemberOf(), configuration.getUser());
                return principal.getMemberOf()
                        .filter(m -> m.matches(configuration.getUser()))
                        .isPresent();
            case "API":
                log.info("User '%s' with memberOf(%s) identified as API(%s)",
                        principal.getName(), principal.getMemberOf(), configuration.getApi());
                return principal.getMemberOf()
                        .filter(m -> m.matches(configuration.getApi()))
                        .isPresent();
            default:
                log.warn("User '%s' with role %s has no regex match based on ldap search",
                        principal.getName(), role);
                return false;
        }
    }
}
