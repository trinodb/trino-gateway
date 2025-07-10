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
import io.trino.gateway.ha.security.util.Authorizer;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.container.ContainerRequestContext;

public class LbAuthorizer
        implements Authorizer
{
    private static final Logger log = Logger.get(LbAuthorizer.class);

    public LbAuthorizer()
    {
    }

    @Override
    public boolean authorize(LbPrincipal principal,
            String role,
            @Nullable ContainerRequestContext ctx)
    {
        switch (role) {
            case "ADMIN":
                if (principal.getPrivileges().contains("ADMIN")) {
                    log.info("User '%s' with memberOf(%s) was identified as ADMIN",
                            principal.getName(), principal.getPrivileges());
                    return true;
                }
                return false;
            case "USER":
                if (principal.getPrivileges().contains("USER")) {
                    log.info("User '%s' with memberOf(%s) identified as USER",
                            principal.getName(), principal.getPrivileges());
                    return true;
                }
                return false;
            case "API":
                if (principal.getPrivileges().contains("API")) {
                    log.info("User '%s' with memberOf(%s) identified as API",
                            principal.getName(), principal.getPrivileges());
                    return true;
                }
                return false;
            default:
                log.warn("User '%s' with role %s has no regex match based on ldap search",
                        principal.getName(), role);
                return false;
        }
    }
}
