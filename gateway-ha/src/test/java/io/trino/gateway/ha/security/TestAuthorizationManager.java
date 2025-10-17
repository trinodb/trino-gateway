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
import io.trino.gateway.ha.config.UserConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TestAuthorizationManager
{
    @Test
    public void testNoDefaultPrivilege()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        Map<String, UserConfiguration> presetUsers = Collections.emptyMap();
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isEmpty();
    }

    @Test
    public void testCustomDefaultPrivilege()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege("CUSTOM_DEFAULT");
        Map<String, UserConfiguration> presetUsers = Collections.emptyMap();
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("CUSTOM_DEFAULT");
    }

    @Test
    public void testOauthDefaultUserRoleEnabled()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        authConfig.setDefaultPrivilege("USER");
        Map<String, UserConfiguration> presetUsers = Collections.emptyMap();
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("USER");
    }

    @Test
    public void testOauthDefaultUserRoleDisabled()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        Map<String, UserConfiguration> presetUsers = Collections.emptyMap();
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("newUser");
        assertThat(privileges).isNotPresent();
    }

    @Test
    public void testPresetUserRole()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        UserConfiguration presetUser = new UserConfiguration("ADMIN", "password");
        Map<String, UserConfiguration> presetUsers = Map.of("adminUser", presetUser);
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("adminUser");
        assertThat(privileges).isPresent();
        assertThat(privileges.orElseThrow()).isEqualTo("ADMIN");
    }

    @Test
    public void testPresetUserWithEmptyPrivileges()
    {
        AuthorizationConfiguration authConfig = new AuthorizationConfiguration();
        UserConfiguration presetUser = new UserConfiguration("", "password"); // Empty privileges
        Map<String, UserConfiguration> presetUsers = Map.of("emptyPrivUser", presetUser);
        AuthorizationManager authManager = new AuthorizationManager(authConfig, presetUsers);

        Optional<String> privileges = authManager.getPrivileges("emptyPrivUser");
        assertThat(privileges).isEmpty();
    }
}
