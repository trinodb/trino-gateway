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

import com.google.common.annotations.VisibleForTesting;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;

import java.util.ArrayList;
import java.util.List;
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
        if (configuration != null && configuration.getLdapConfigPath() != null) {
            lbLdapClient = new LbLdapClient(LdapConfiguration.load(configuration.getLdapConfigPath()));
        }
        else {
            lbLdapClient = null;
        }
        this.authorizationConfiguration = configuration;
    }

    @VisibleForTesting
    public AuthorizationManager(Map<String, UserConfiguration> presetUsers, LbLdapClient lbLdapClient, AuthorizationConfiguration authorizationConfiguration)
    {
        this.presetUsers = presetUsers;
        this.lbLdapClient = lbLdapClient;
        this.authorizationConfiguration = authorizationConfiguration;
    }

    public String getPrivileges(String username)
    {
        if (authorizationConfiguration == null) {
            return "ADMIN_USER_API";
        }
        Optional<String> memberOf = getMemberOf(username);
        List<String> privileges = new ArrayList<String>();

        if (authorizationConfiguration.getAdmin() != null) {
            memberOf.filter(m -> m.matches(authorizationConfiguration.getAdmin())).ifPresent(m -> privileges.add("ADMIN"));
        }
        if (authorizationConfiguration.getUser() != null) {
            memberOf.filter(m -> m.matches(authorizationConfiguration.getUser())).ifPresent(m -> privileges.add("USER"));
        }
        if (authorizationConfiguration.getApi() != null) {
            memberOf.filter(m -> m.matches(authorizationConfiguration.getApi())).ifPresent(m -> privileges.add("API"));
        }

        if (privileges.isEmpty()) {
            return "";
        }
        return String.join("_", privileges);
    }

    public Optional<String> getMemberOf(String username)
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
