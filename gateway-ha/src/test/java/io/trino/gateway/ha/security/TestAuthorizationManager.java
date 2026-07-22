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
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TestAuthorizationManager
{
    @Test
    public void testNoDefaultPrivilege()
    {
        AuthorizationManager authManager = createAuthorizationManager(new AuthorizationConfiguration(), Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isEmpty();
    }

    @Test
    public void testCustomDefaultPrivilege()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege("CUSTOM_DEFAULT");
        AuthorizationManager authManager = createAuthorizationManager(authConfig, Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("CUSTOM_DEFAULT");
    }

    @Test
    public void testDefaultPrivilegeNoneDeniesAll()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege(AuthorizationManager.DENY_ALL_PRIVILEGE);
        AuthorizationManager authManager = createAuthorizationManager(authConfig, Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isEmpty();
    }

    @Test
    public void testDefaultPrivilegeNoneIsCaseInsensitive()
    {
        for (String noneValue : new String[] {"none", "None", " NONE "}) {
            AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
            authConfig.setDefaultPrivilege(noneValue);
            AuthorizationManager authManager = createAuthorizationManager(authConfig, Map.of());

            Optional<String> privileges = authManager.getPrivileges("newUser");
            assertThat(privileges).as("defaultPrivilege '%s' should deny all", noneValue).isEmpty();
        }
    }

    @Test
    public void testEmptyDefaultPrivilegeDeniesAll()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege("   ");
        AuthorizationManager authManager = createAuthorizationManager(authConfig, Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isEmpty();
    }

    @Test
    public void testPresetUserKeepsPrivilegesWithNoneDefault()
    {
        UserConfiguration presetUser = new UserConfiguration("ADMIN", "password");
        Map<String, UserConfiguration> presetUsers = Map.of("adminUser", presetUser);
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege(AuthorizationManager.DENY_ALL_PRIVILEGE);
        AuthorizationManager authManager = createAuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("adminUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("ADMIN");
    }

    @Test
    public void testOauthDefaultUserRoleEnabled()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege("USER");
        AuthorizationManager authManager = createAuthorizationManager(authConfig, Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("USER");
    }

    @Test
    public void testOauthDefaultUserRoleDisabled()
    {
        AuthorizationManager authManager = createAuthorizationManager(new AuthorizationConfiguration(), Map.of());

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isNotPresent();
    }

    @Test
    public void testPresetUserRole()
    {
        UserConfiguration presetUser = new UserConfiguration("ADMIN", "password");
        Map<String, UserConfiguration> presetUsers = Map.of("adminUser", presetUser);
        AuthorizationManager authManager = createAuthorizationManager(new AuthorizationConfiguration(), presetUsers);

        Optional<String> privileges = authManager.getPrivileges("adminUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("ADMIN");
    }

    @Test
    public void testPresetUserWithEmptyPrivileges()
    {
        UserConfiguration presetUser = new UserConfiguration("", "password"); // Empty privileges
        Map<String, UserConfiguration> presetUsers = Map.of("emptyPrivUser", presetUser);
        AuthorizationManager authManager = createAuthorizationManager(new AuthorizationConfiguration(), presetUsers);

        Optional<String> privileges = authManager.getPrivileges("emptyPrivUser");
        assertThat(privileges).isEmpty();
    }

    private static AuthorizationManager createAuthorizationManager(AuthorizationConfiguration authorizationConfiguration, Map<String, UserConfiguration> presetUsers)
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.setAuthorization(authorizationConfiguration);
        config.setPresetUsers(presetUsers);
        return new AuthorizationManager(config);
    }
}
