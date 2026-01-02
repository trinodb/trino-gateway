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

import com.google.inject.Inject;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;

import java.util.Map;
import java.util.Optional;

public class AuthorizationManager
{
    private final Map<String, UserConfiguration> presetUsers;
    private final LbLdapClient lbLdapClient;

    @Inject
    public AuthorizationManager(HaGatewayConfiguration config)
    {
        AuthorizationConfiguration authorizationConfig = config.getAuthorization();
        this.presetUsers = config.getPresetUsers();
        if (authorizationConfig != null && authorizationConfig.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(authorizationConfig.getLdapConfigPath()));
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
        return Optional.ofNullable(privs);
    }
}
