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

import io.trino.gateway.ha.config.AuthorizationConfiguration;

import java.util.HashSet;
import java.util.Set;

public class LbLdapParser
{
    private final AuthorizationConfiguration authorizationConfiguration;

    public LbLdapParser(AuthorizationConfiguration authorizationConfiguration)
    {
        this.authorizationConfiguration = authorizationConfiguration;
    }

    /**
     * The input is the ldap search from the user. It's in the format of
     * String memberOf = """
     * memberOf: ...
     * memberOf: ...
     * """
     * We need to parse it into the format of ADMIN_USER_API to make it compatible
     * with the rest of the program
     *
     * @param memberOf
     * @return roles
     */
    public String parse(String memberOf)
    {
        Set<String> roles = new HashSet<>();
        String[] lines = memberOf.split("\n");
        for (String line : lines) {
            String[] parts = line.split(": ");
            String attributes = parts[1];
            if (attributes.matches(authorizationConfiguration.getAdmin())) {
                roles.add("ADMIN");
            }
            else if (attributes.matches(authorizationConfiguration.getUser())) {
                roles.add("USER");
            }
            else if (attributes.matches(authorizationConfiguration.getApi())) {
                roles.add("API");
            }
        }
        return String.join("_", roles);
    }
}
