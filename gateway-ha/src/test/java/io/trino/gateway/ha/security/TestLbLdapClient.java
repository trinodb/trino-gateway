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
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class TestLbLdapClient
{
    @Mock
    private LdapConnectionTemplate ldapConnectionTemplate;

    private LdapConfiguration ldapConfig;
    private LbLdapClient lbLdapClient;

    @BeforeEach
    void setUp()
    {
        ldapConfig = LdapConfiguration.load("src/test/resources/auth/ldapTestConfig.yml");
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
