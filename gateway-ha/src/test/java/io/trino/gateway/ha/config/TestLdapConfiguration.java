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
package io.trino.gateway.ha.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestLdapConfiguration
{
    @Test
    void testValidateAcceptsUnsetUserDnPattern()
    {
        LdapConfiguration configuration = new LdapConfiguration();
        configuration.validate();

        configuration.setLdapUserDnPattern("");
        configuration.validate();
    }

    @Test
    void testValidateAcceptsUserDnPattern()
    {
        LdapConfiguration configuration = new LdapConfiguration();
        configuration.setLdapUserDnPattern("uid=${USER},OU=accts,DC=dept1,DC=example,DC=com");
        configuration.validate();
    }

    @Test
    void testValidateRejectsUserDnPatternWithoutPlaceholder()
    {
        LdapConfiguration configuration = new LdapConfiguration();
        configuration.setLdapUserDnPattern("uid=user1,OU=accts,DC=dept1,DC=example,DC=com");

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain the ${USER} placeholder");
    }

    @Test
    void testValidateRejectsUserDnPatternThatIsNotADn()
    {
        LdapConfiguration configuration = new LdapConfiguration();
        configuration.setLdapUserDnPattern("not a dn ${USER}");

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not resolve to a valid DN");
    }

    @Test
    void testLoadValidatesUserDnPattern()
    {
        assertThatThrownBy(() -> LdapConfiguration.load("src/test/resources/auth/ldapTestConfigInvalidUserDnPattern.yml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not resolve to a valid DN");

        // The search based configuration keeps loading
        LdapConfiguration configuration = LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml");
        assertThat(configuration.getLdapUserDnPattern()).isNull();
        assertThat(configuration.getLdapUserSearch()).isEqualTo("(&(objectclass=user)(sAMAccountName=${USER}))");
    }
}
