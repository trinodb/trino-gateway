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
import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.api.LdapEncoder;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.controls.OpaqueControl;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.util.Strings;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
final class TestLbLdapClient
{
    private static final String AD_DOMAIN_SCOPE_CONTROL_OID = "1.2.840.113556.1.4.1339";

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

    @Test
    void testDomainScopeControlIsAddedWhenEnabled()
    {
        String user = "user1";
        String[] attributes = new String[] {"memberOf"};
        String filter = ldapConfig.getLdapUserSearch().replace("${USER}", user);

        ldapConfig.setLdapAdDomainScopeControl(false);
        SearchRequest disabledRequest = getMemberOfSearchRequest(filter, attributes);
        assertThat(disabledRequest.hasControl(AD_DOMAIN_SCOPE_CONTROL_OID)).isFalse();

        ldapConfig.setLdapAdDomainScopeControl(true);
        SearchRequest enabledRequest = getMemberOfSearchRequest(filter, attributes);
        assertThat(enabledRequest.hasControl(AD_DOMAIN_SCOPE_CONTROL_OID)).isTrue();
        assertThat(enabledRequest.getBase().toString()).isEqualTo(ldapConfig.getLdapUserBaseDn());
        assertThat(enabledRequest.getFilter().toString()).isEqualTo(filter);
        assertThat(enabledRequest.getScope()).isEqualTo(SearchScope.SUBTREE);
        assertThat(enabledRequest.getAttributes()).containsExactly("memberOf");

        OpaqueControl control = (OpaqueControl) enabledRequest.getControl(AD_DOMAIN_SCOPE_CONTROL_OID);
        assertThat(control).isNotNull();
        assertThat(control.getOid()).isEqualTo("1.2.840.113556.1.4.1339");
        assertThat(control.isCritical()).isFalse();
        assertThat(control.hasEncodedValue()).isTrue();
        assertThat(control.getEncodedValue()).isEmpty();
    }

    @Test
    void testDomainScopeControlCanBeEncoded()
            throws Exception
    {
        SearchRequest searchRequest = new SearchRequestImpl();
        searchRequest.setBase(new Dn(ldapConfig.getLdapUserBaseDn()));
        searchRequest.setFilter(ldapConfig.getLdapUserSearch().replace("${USER}", "user1"));
        searchRequest.setScope(SearchScope.SUBTREE);
        searchRequest.setMessageId(1);

        OpaqueControl control = new OpaqueControl(AD_DOMAIN_SCOPE_CONTROL_OID, false);
        control.setEncodedValue(Strings.EMPTY_BYTES);
        searchRequest.addControl(control);

        LdapApiService ldapApiService = LbLdapClient.createLdapApiService();
        assertThat(ldapApiService.isControlRegistered(AD_DOMAIN_SCOPE_CONTROL_OID)).isTrue();

        ByteBuffer encoded = LdapEncoder.encodeMessage(new Asn1Buffer(), ldapApiService, searchRequest);
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    private SearchRequest getMemberOfSearchRequest(String filter, String[] attributes)
    {
        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        SearchRequest searchRequest = new SearchRequestImpl();

        when(ldapConnectionTemplate.newSearchRequest(
                eq(ldapConfig.getLdapUserBaseDn()),
                eq(filter),
                eq(SearchScope.SUBTREE),
                eq(attributes)))
                .thenAnswer(invocation -> {
                    searchRequest.setBase(new Dn(new String[] {invocation.getArgument(0)}));
                    searchRequest.setFilter((String) invocation.getArgument(1));
                    searchRequest.setScope(invocation.getArgument(2));
                    searchRequest.addAttributes(attributes);
                    return searchRequest;
                });
        when(ldapConnectionTemplate.search(
                searchRequestCaptor.capture(),
                any(LbLdapClient.UserEntryMapper.class)))
                .thenReturn(null);

        lbLdapClient.getMemberOf("user1");
        return searchRequestCaptor.getValue();
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
