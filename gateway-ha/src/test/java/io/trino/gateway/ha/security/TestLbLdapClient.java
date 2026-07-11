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
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
        SearchRequest searchRequest = new SearchRequestImpl();

        Mockito
                .when(ldapConnectionTemplate.newSearchRequest(
                        eq(ldapConfig.getLdapUserBaseDn()),
                        eq(filter),
                        eq(SearchScope.SUBTREE),
                        any(String[].class)))
                .thenReturn(searchRequest);

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        eq(searchRequest),
                        any(char[].class)))
                .thenReturn(null);

        // Success case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        eq(searchRequest),
                        any(char[].class)))
                .thenReturn(new TestLbLdapClient.DummyPasswordWarning());

        // Warning case
        assertThat(lbLdapClient.authenticate(user, password)).isTrue();

        Mockito
                .when(ldapConnectionTemplate.authenticate(
                        eq(searchRequest),
                        any(char[].class)))
                .thenThrow(PasswordException.class);

        // failure case
        assertThat(lbLdapClient.authenticate(user, password)).isFalse();
    }

    @Test
    void testMemberof()
    {
        String user = "user1";
        String[] attributes = new String[] {"memberOf"};
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);
        SearchRequest searchRequest = new SearchRequestImpl();

        Mockito
                .when(ldapConnectionTemplate.newSearchRequest(
                        eq(ldapConfig.getLdapUserBaseDn()),
                        eq(filter),
                        eq(SearchScope.SUBTREE),
                        eq(attributes)))
                .thenReturn(searchRequest);

        java.util.ArrayList users = new java.util.ArrayList();
        users.add(new LbLdapClient.UserRecord("Admin,User"));

        Mockito
                .when(ldapConnectionTemplate.search(
                        eq(searchRequest),
                        any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(users);

        // Success case
        String ret = lbLdapClient.getMemberOf(user);

        log.info("ret is %s", ret);
        assertThat(ret).isEqualTo("Admin,User");

        org.mockito.Mockito
                .when(ldapConnectionTemplate.search(
                        eq(searchRequest),
                        any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(null);

        // failure case
        assertThat(lbLdapClient.getMemberOf(user)).isNotEqualTo("Admin,User");
    }

    @Test
    void testReferralPolicy()
    {
        String user = "user1";
        String[] attributes = new String[] {"memberOf"};
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

        SearchRequest defaultSearchRequest = getMemberOfSearchRequest(filter, attributes);
        assertThat(defaultSearchRequest.isFollowReferrals()).isFalse();
        assertThat(defaultSearchRequest.isIgnoreReferrals()).isFalse();

        ldapConfig.setLdapReferralPolicy(LdapConfiguration.LdapReferralPolicy.FOLLOW);
        SearchRequest followSearchRequest = getMemberOfSearchRequest(filter, attributes);
        assertThat(followSearchRequest.isFollowReferrals()).isTrue();
        assertThat(followSearchRequest.isIgnoreReferrals()).isFalse();

        ldapConfig.setLdapReferralPolicy(LdapConfiguration.LdapReferralPolicy.IGNORE);
        SearchRequest ignoreSearchRequest = getMemberOfSearchRequest(filter, attributes);
        assertThat(ignoreSearchRequest.isFollowReferrals()).isFalse();
        assertThat(ignoreSearchRequest.isIgnoreReferrals()).isTrue();
    }

    private SearchRequest getMemberOfSearchRequest(String filter, String[] attributes)
    {
        SearchRequest searchRequest = new SearchRequestImpl();
        Mockito
                .when(ldapConnectionTemplate.newSearchRequest(
                        eq(ldapConfig.getLdapUserBaseDn()),
                        eq(filter),
                        eq(SearchScope.SUBTREE),
                        eq(attributes)))
                .thenReturn(searchRequest);
        Mockito
                .when(ldapConnectionTemplate.search(
                        eq(searchRequest),
                        any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(null);

        lbLdapClient.getMemberOf("user1");

        return searchRequest;
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
