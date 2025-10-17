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
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;

import java.util.Map;
import java.util.Optional;

public class AuthorizationManager
{
    private final Map<String, UserConfiguration> presetUsers;
    private final LbLdapClient lbLdapClient;
    private final AuthorizationConfiguration authorizationConfiguration;

    public AuthorizationManager(AuthorizationConfiguration configuration,
            Map<String, UserConfiguration> presetUsers)
    {
        this.presetUsers = presetUsers;
        this.authorizationConfiguration = configuration;
        if (configuration != null && configuration.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(configuration.getLdapConfigPath()));
        }
        else {
            lbLdapClient = null;
        }
    }

    public Optional<String> getPrivileges(String username)
    {
        //check the preset users
        String privs = "";

        UserConfiguration user = presetUsers.get(username);
        if (user != null) {
            privs = user.privileges();
        }
        else if (lbLdapClient != null) {
            privs = lbLdapClient.getMemberOf(username);
        }

        if (privs == null || privs.trim().isEmpty()) {
            if (authorizationConfiguration != null && authorizationConfiguration.getDefaultPrivilege() != null) {
                return Optional.of(authorizationConfiguration.getDefaultPrivilege());
            }
            return Optional.empty(); // No default privilege if not configured
        }
        return Optional.of(privs);
    }
}
