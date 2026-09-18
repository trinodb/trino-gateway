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
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.filter.FilterEncoder;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.controls.OpaqueControl;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
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

import static java.util.Objects.requireNonNull;

public class LbLdapClient
{
    static final String AD_DOMAIN_SCOPE_CONTROL_OID = "1.2.840.113556.1.4.1339";

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
        config = requireNonNull(ldapConfig, "ldapConfig is null");
        this.ldapConnectionTemplate = requireNonNull(
                ldapConnectionTemplate,
                "ldapConnectionTemplate is null");
        userRecordEntryMapper = new UserEntryMapper(config.getLdapGroupMemberAttribute());
    }

    private static LdapConnectionTemplate createLdapConnectionTemplate(LdapConfiguration ldapConfig)
    {
        requireNonNull(ldapConfig, "ldapConfig is null");

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

        LdapApiService ldapApiService;
        try {
            ldapApiService = createLdapApiService();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize LDAP client", exception);
        }
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
        // A bind with an empty password is an unauthenticated bind, which some directory
        // servers answer with success. Reject it before it reaches the server.
        if (password == null || password.isEmpty()) {
            log.error("Rejected authentication attempt with an empty password");
            return false;
        }

        try {
            PasswordWarning passwordWarning;
            String userDnPattern = config.getLdapUserDnPattern();

            if (userDnPattern != null && !userDnPattern.isEmpty()) {
                // The user name is interpolated into a DN, so it needs DN escaping rather
                // than search filter escaping. Without it a name such as "x,ou=admins"
                // would change which DN gets bound.
                Dn userDn = new Dn(userDnPattern.replace("${USER}", Rdn.escapeValue(user)));
                passwordWarning =
                        ldapConnectionTemplate.authenticate(userDn, password.toCharArray());
            }
            else {
                String filter = createUserSearchFilter(user);
                SearchRequest searchRequest = newUserSearchRequest(filter);
                passwordWarning =
                        ldapConnectionTemplate.authenticate(searchRequest, password.toCharArray());
            }

            if (passwordWarning != null) {
                log.warn("password warning %s", passwordWarning);
                return true;
            }
        }
        catch (PasswordException exception) {
            log.error("Failed to authenticate %s", exception.getResultCode());
            return false;
        }
        catch (LdapInvalidDnException exception) {
            log.error(exception, "ldapUserDnPattern did not produce a valid DN");
            return false;
        }
        log.info("Authenticated successfully");
        return true;
    }

    public String getMemberOf(String user)
    {
        String filter = createUserSearchFilter(user);

        SearchRequest searchRequest = newUserSearchRequest(
                filter,
                config.getLdapGroupMemberAttribute());
        List<UserRecord> list = ldapConnectionTemplate.search(searchRequest, userRecordEntryMapper);

        String memberOf = "";
        if (list != null && !list.isEmpty()) {
            memberOf = list.getFirst().getMemberOf();
            log.debug("Member of %s", memberOf);
        }
        return memberOf;
    }

    private String createUserSearchFilter(String user)
    {
        return config.getLdapUserSearch()
                .replace("${USER}", FilterEncoder.encodeFilterValue(user));
    }

    private SearchRequest newUserSearchRequest(String filter, String... attributes)
    {
        SearchRequest searchRequest = ldapConnectionTemplate.newSearchRequest(
                config.getLdapUserBaseDn(),
                filter,
                SearchScope.SUBTREE,
                attributes);

        if (config.isLdapAdDomainScopeControl()) {
            searchRequest.addControl(createAdDomainScopeControl());
        }

        return searchRequest;
    }

    private static OpaqueControl createAdDomainScopeControl()
    {
        OpaqueControl control = new OpaqueControl(AD_DOMAIN_SCOPE_CONTROL_OID, false);
        // Apache Directory's opaque control encoder requires an encoded value,
        // while the Active Directory Domain Scope control has no payload.
        control.setEncodedValue(Strings.EMPTY_BYTES);
        return control;
    }

    static LdapApiService createLdapApiService()
            throws Exception
    {
        LdapApiService ldapApiService = new StandaloneLdapApiService();
        ldapApiService.registerRequestControl(
                new OpaqueControlFactory(ldapApiService, AD_DOMAIN_SCOPE_CONTROL_OID));
        return ldapApiService;
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
            this.memberOfAttribute = requireNonNull(
                    memberOfAttribute,
                    "memberOfAttribute is null");
        }

        @Override
        public UserRecord map(Entry entry)
                throws LdapException
        {
            return new UserRecord(entry.get(memberOfAttribute).toString());
        }
    }
}
