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

import io.trino.gateway.ha.config.LdapConfiguration;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class TestLbLdapClient
{
    private static final String SPECIAL_USER = "user*)(uid=*)\\test";
    private static final String SPECIAL_USER_FILTER =
            "(&(objectclass=user)(sAMAccountName=user\\2A\\29\\28uid=\\2A\\29\\5Ctest))";

    @Mock
    private LdapConnectionTemplate ldapConnectionTemplate;

    private LdapConfiguration ldapConfig;
    private LbLdapClient lbLdapClient;

    @BeforeEach
    void setUp()
    {
        ldapConfig = Mockito.spy(LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml"));
        lbLdapClient = new LbLdapClient(ldapConfig, ldapConnectionTemplate);
    }

    @Test
    void testAuthenticate()
            throws Exception
    {
        String user = "user1";
        String password = "pass1";
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);
        SearchRequest searchRequest = new SearchRequestImpl();

        when(ldapConnectionTemplate.newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(filter),
                eq(SearchScope.SUBTREE),
                any(String[].class)))
                .thenReturn(searchRequest);

        when(ldapConnectionTemplate.authenticate(eq(searchRequest), any(char[].class)))
                .thenReturn(null);
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        when(ldapConnectionTemplate.authenticate(eq(searchRequest), any(char[].class)))
                .thenReturn(new DummyPasswordWarning());
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        when(ldapConnectionTemplate.authenticate(eq(searchRequest), any(char[].class)))
                .thenThrow(PasswordException.class);
        assertThat(lbLdapClient.authenticate(user, password)).isFalse();
    }

    @Test
    void testAuthenticateEscapesFilterValue()
            throws Exception
    {
        SearchRequest searchRequest = mock(SearchRequest.class);

        when(ldapConnectionTemplate.newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(SPECIAL_USER_FILTER),
                eq(SearchScope.SUBTREE),
                any(String[].class)))
                .thenReturn(searchRequest);
        when(ldapConnectionTemplate.authenticate(eq(searchRequest), any(char[].class)))
                .thenReturn(null);

        assertThat(lbLdapClient.authenticate(SPECIAL_USER, "pass1")).isTrue();

        verify(ldapConnectionTemplate).newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(SPECIAL_USER_FILTER),
                eq(SearchScope.SUBTREE),
                any(String[].class));
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
    void testGetMemberOf()
    {
        String user = "user1";
        String[] attributes = new String[] {"memberOf"};
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);
        SearchRequest searchRequest = new SearchRequestImpl();

        when(ldapConnectionTemplate.newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(filter),
                eq(SearchScope.SUBTREE),
                eq(attributes)))
                .thenReturn(searchRequest);

        when(ldapConnectionTemplate.search(
                eq(searchRequest),
                any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(List.of(new LbLdapClient.UserRecord("Admin,User")));

        assertThat(lbLdapClient.getMemberOf(user)).isEqualTo("Admin,User");

        when(ldapConnectionTemplate.search(
                eq(searchRequest),
                any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(null);
        assertThat(lbLdapClient.getMemberOf(user)).isEmpty();

        when(ldapConnectionTemplate.search(
                eq(searchRequest),
                any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(List.of());
        assertThat(lbLdapClient.getMemberOf(user)).isEmpty();
    }

    @Test
    void testGetMemberOfEscapesFilterValue()
    {
        String[] attributes = new String[] {"memberOf"};
        SearchRequest searchRequest = mock(SearchRequest.class);

        when(ldapConnectionTemplate.newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(SPECIAL_USER_FILTER),
                eq(SearchScope.SUBTREE),
                eq(attributes)))
                .thenReturn(searchRequest);
        when(ldapConnectionTemplate.search(
                eq(searchRequest),
                any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(List.of(new LbLdapClient.UserRecord("Admin,User")));

        assertThat(lbLdapClient.getMemberOf(SPECIAL_USER)).isEqualTo("Admin,User");

        verify(ldapConnectionTemplate).newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(SPECIAL_USER_FILTER),
                eq(SearchScope.SUBTREE),
                eq(attributes));
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
