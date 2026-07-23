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
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.codec.controls.OpaqueControlFactory;
import org.apache.directory.api.ldap.codec.standalone.StandaloneLdapApiService;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.controls.OpaqueControl;
import org.apache.directory.api.util.Strings;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapClientTrustStoreManager;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.ValidatingPoolableLdapConnectionFactory;
import org.apache.directory.ldap.client.template.EntryMapper;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.PasswordWarning;
import org.apache.directory.ldap.client.template.exception.PasswordException;

import java.util.List;

public class LbLdapClient
{
    private static final String AD_DOMAIN_SCOPE_CONTROL_OID = "1.2.840.113556.1.4.1339";

    private static final Logger log = Logger.get(LbLdapClient.class);
    private final LdapConnectionTemplate ldapConnectionTemplate;
    private final LdapConfiguration config;
    private final UserEntryMapper userRecordEntryMapper;

    public LbLdapClient(LdapConfiguration ldapConfig)
    {
        this(ldapConfig, createLdapConnectionTemplate(ldapConfig));
    }

    LbLdapClient(LdapConfiguration ldapConfig, LdapConnectionTemplate ldapConnectionTemplate)
    {
        config = ldapConfig;
        this.ldapConnectionTemplate = ldapConnectionTemplate;
        userRecordEntryMapper = new UserEntryMapper(config.getLdapGroupMemberAttribute());
    }

    private static LdapConnectionTemplate createLdapConnectionTemplate(LdapConfiguration ldapConfig)
    {
        LdapConnectionConfig connectionConfig = new LdapConnectionConfig();
        connectionConfig.setLdapHost(ldapConfig.getLdapHost());
        connectionConfig.setLdapPort(ldapConfig.getLdapPort());
        connectionConfig.setUseTls(ldapConfig.isUseTls());
        connectionConfig.setUseSsl(ldapConfig.isUseSsl());
        connectionConfig.setName(ldapConfig.getLdapAdminBindDn());
        connectionConfig.setCredentials(ldapConfig.getLdapAdminPassword());
        String trustStore = ldapConfig.getLdapTrustStorePath();
        String trustStorePassword = ldapConfig.getLdapTrustStorePassword();
        if (trustStore != null && trustStorePassword != null) {
            connectionConfig.setTrustManagers(new LdapClientTrustStoreManager(
                    trustStore,
                    trustStorePassword.toCharArray(),
                    null,
                    true));
        }

        LdapApiService ldapApiService = createLdapApiService();
        connectionConfig.setLdapApiService(ldapApiService);
        DefaultLdapConnectionFactory defaultFactory =
                new DefaultLdapConnectionFactory(connectionConfig);
        defaultFactory.setLdapApiService(ldapApiService);

        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(ldapConfig.getPoolMaxIdle());
        poolConfig.setMaxTotal(ldapConfig.getPoolMaxTotal());
        poolConfig.setMinIdle(ldapConfig.getPoolMinIdle());
        poolConfig.setTestOnBorrow(ldapConfig.isPoolTestOnBorrow());

        ValidatingPoolableLdapConnectionFactory validatingFactory =
                new ValidatingPoolableLdapConnectionFactory(defaultFactory);
        LdapConnectionPool connectionPool = new LdapConnectionPool(validatingFactory, poolConfig);
        return new LdapConnectionTemplate(connectionPool);
    }

    public boolean authenticate(String user, String password)
    {
        try {
            String filter = config.getLdapUserSearch().replace("${USER}", user);
            SearchRequest searchRequest = newUserSearchRequest(filter);
            PasswordWarning passwordWarning =
                    ldapConnectionTemplate.authenticate(searchRequest, password.toCharArray());

            if (passwordWarning != null) {
                log.warn("password warning %s", passwordWarning);
                return true;
            }
        }
        catch (PasswordException exception) {
            log.error("Failed to authenticate %s", exception.getResultCode());
            return false;
        }
        log.info("Authenticated successfully");
        return true;
    }

    public String getMemberOf(String user)
    {
        String filter = config.getLdapUserSearch().replace("${USER}", user);

        String[] attributes = new String[] {config.getLdapGroupMemberAttribute()};
        SearchRequest searchRequest = newUserSearchRequest(filter, attributes);
        List<UserRecord> list = ldapConnectionTemplate.search(searchRequest, userRecordEntryMapper);

        String memberOf = "";
        if (list != null && !list.isEmpty()) {
            memberOf = list.getFirst().getMemberOf();
            log.debug("Member of %s", memberOf);
        }
        return memberOf;
    }

    private SearchRequest newUserSearchRequest(String filter, String... attributes)
    {
        SearchRequest searchRequest = ldapConnectionTemplate.newSearchRequest(
                config.getLdapUserBaseDn(),
                filter,
                SearchScope.SUBTREE,
                attributes);

        if (config.isLdapAdDomainScopeControl()) {
            addAdDomainScopeControl(searchRequest);
        }

        return searchRequest;
    }

    private void addAdDomainScopeControl(SearchRequest searchRequest)
    {
        OpaqueControl control = new OpaqueControl(AD_DOMAIN_SCOPE_CONTROL_OID, false);
        // Apache Directory's opaque control encoder requires an encoded value.
        // The Active Directory Domain Scope control has no payload.
        control.setEncodedValue(Strings.EMPTY_BYTES);
        searchRequest.addControl(control);
    }

    static LdapApiService createLdapApiService()
    {
        try {
            LdapApiService ldapApiService = new StandaloneLdapApiService();
            ldapApiService.registerRequestControl(
                    new OpaqueControlFactory(ldapApiService, AD_DOMAIN_SCOPE_CONTROL_OID));
            return ldapApiService;
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize LDAP codec service", exception);
        }
    }

    public static class UserRecord
    {
        private final String memberOf;

        public UserRecord(String memberOf)
        {
            this.memberOf = memberOf;
        }

        String getMemberOf()
        {
            return memberOf;
        }
    }

    public static class UserEntryMapper
            implements EntryMapper<UserRecord>
    {
        private final String memberOfAttribute;

        public UserEntryMapper(String memberOfAttribute)
        {
            this.memberOfAttribute = memberOfAttribute;
        }

        @Override
        public UserRecord map(Entry entry)
                throws LdapException
        {
            return new UserRecord(entry.get(memberOfAttribute).toString());
        }
    }
}
