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
import io.trino.gateway.ha.config.LdapConfiguration;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
final class TestLbLdapClient
{
    private static final Logger log = Logger.get(TestLbLdapClient.class);
    @Mock
    LdapConnectionTemplate ldapConnectionTemplate;
    @Spy
    LdapConfiguration ldapConfig =
            LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml");
    @InjectMocks
    LbLdapClient lbLdapClient =
            new LbLdapClient(LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml"));

    @BeforeEach
    public void initMocks()
    {
        log.info("initializing test");
        org.mockito.MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void resetMocks()
    {
        log.info("resetting mocks");
        Mockito.reset(ldapConnectionTemplate);
        Mockito.reset(ldapConfig);
    }

    @Test
    void testAuthenticate()
            throws Exception
    {
        String user = "user1";
        String password = "pass1";

        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        ldapConfig.getLdapUserBaseDn(),
                        filter,
                        SearchScope.SUBTREE,
                        password.toCharArray()))
                .thenReturn(null);

        // Success case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        ldapConfig.getLdapUserBaseDn(),
                        filter,
                        SearchScope.SUBTREE,
                        password.toCharArray()))
                .thenReturn(new TestLbLdapClient.DummyPasswordWarning());

        // Warning case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        ldapConfig.getLdapUserBaseDn(),
                        filter,
                        SearchScope.SUBTREE,
                        password.toCharArray()))
                .thenThrow(PasswordException.class);

        // failure case
        assertThat(lbLdapClient.authenticate(user, password)).isFalse();

        assertThatThrownBy(() -> Mockito
                .when(ldapConnectionTemplate.authenticate(
                        ldapConfig.getLdapUserBaseDn(),
                        filter,
                        SearchScope.SUBTREE,
                        password.toCharArray()))
                .thenReturn(null))
                .isInstanceOf(PasswordException.class);
    }

    @Test
    void testAuthenticateWithUserDnPattern()
            throws Exception
    {
        String user = "user1";
        String password = "pass1";

        Mockito
                .when(ldapConfig.getLdapUserDnPattern())
                .thenReturn("uid=${USER},OU=accts,DC=dept1,DC=example,DC=com");

        Dn expectedDn = new Dn("uid=user1,OU=accts,DC=dept1,DC=example,DC=com");

        Mockito
                .when(ldapConnectionTemplate.authenticate(expectedDn, password.toCharArray()))
                .thenReturn(null);

        // Success case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        // The user entry is bound directly, without a search
        Mockito
                .verify(ldapConnectionTemplate, Mockito.never())
                .authenticate(
                        any(String.class),
                        any(String.class),
                        any(SearchScope.class),
                        any(char[].class));

        Mockito
                .when(ldapConnectionTemplate.authenticate(expectedDn, password.toCharArray()))
                .thenReturn(new TestLbLdapClient.DummyPasswordWarning());

        // Warning case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        Mockito
                .when(ldapConnectionTemplate.authenticate(expectedDn, password.toCharArray()))
                .thenThrow(PasswordException.class);

        // Failure case
        assertThat(lbLdapClient.authenticate(user, password)).isFalse();
    }

    @Test
    void testAuthenticateWithUserDnPatternEscapesUserName()
            throws Exception
    {
        String user = "x,OU=admins";
        String password = "pass1";

        Mockito
                .when(ldapConfig.getLdapUserDnPattern())
                .thenReturn("uid=${USER},OU=accts,DC=dept1,DC=example,DC=com");

        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        ArgumentCaptor<Dn> boundDn = ArgumentCaptor.forClass(Dn.class);
        Mockito
                .verify(ldapConnectionTemplate)
                .authenticate(boundDn.capture(), eq(password.toCharArray()));

        // The comma in the user name stays inside the uid value instead of adding an RDN
        assertThat(boundDn.getValue().size()).isEqualTo(5);
        assertThat(boundDn.getValue().getName())
                .isEqualTo("uid=x\\,OU\\=admins,OU=accts,DC=dept1,DC=example,DC=com");
    }

    @Test
    void testAuthenticateRejectsEmptyPassword()
    {
        assertThat(lbLdapClient.authenticate("user1", "")).isFalse();
        assertThat(lbLdapClient.authenticate("user1", null)).isFalse();

        Mockito.verifyNoInteractions(ldapConnectionTemplate);
    }

    @Test
    void testAuthenticateWithInvalidUserDnPattern()
    {
        // LdapConfiguration rejects such a pattern while loading, so this only covers the
        // defensive handling for a configuration that was built without validation
        Mockito
                .when(ldapConfig.getLdapUserDnPattern())
                .thenReturn("not a dn ${USER}");

        assertThat(lbLdapClient.authenticate("user1", "pass1")).isFalse();

        Mockito.verifyNoInteractions(ldapConnectionTemplate);
    }

    @Test
    void testMemberof()
    {
        String user = "user1";
        String[] attributes = new String[] {"memberOf"};
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

        java.util.ArrayList users = new java.util.ArrayList();
        users.add(new LbLdapClient.UserRecord("Admin,User"));

        Mockito
                .when(ldapConnectionTemplate.search(
                        eq(ldapConfig.getLdapUserBaseDn()),
                        eq(filter),
                        eq(SearchScope.SUBTREE),
                        eq(attributes),
                        any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(users);

        // Success case
        String ret = lbLdapClient.getMemberOf(user);

        log.info("ret is %s", ret);
        assertThat(ret).isEqualTo("Admin,User");

        org.mockito.Mockito
                .when(ldapConnectionTemplate.search(
                        eq(ldapConfig.getLdapUserBaseDn()),
                        eq(filter),
                        eq(SearchScope.SUBTREE),
                        eq(attributes),
                        any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(null);

        // failure case
        assertThat(lbLdapClient.getMemberOf(user)).isNotEqualTo("Admin,User");
    }

    static class DummyPasswordWarning
            implements org.apache.directory.ldap.client.template.PasswordWarning
    {
        @Override
        public int getTimeBeforeExpiration()
        {
            return 0;
        }

        @Override
        public int getGraceAuthNsRemaining()
        {
            return 0;
        }

        @Override
        public boolean isChangeAfterReset()
        {
            return false;
        }
    }
}
