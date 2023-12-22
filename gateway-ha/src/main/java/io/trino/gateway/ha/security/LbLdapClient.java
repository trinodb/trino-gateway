package io.trino.gateway.ha.security;

import io.trino.gateway.ha.config.LdapConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapClientTrustStoreManager;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.ValidatingPoolableLdapConnectionFactory;
import org.apache.directory.ldap.client.template.EntryMapper;
import org.apache.directory.ldap.client.template.LdapConnectionTemplate;
import org.apache.directory.ldap.client.template.PasswordWarning;
import org.apache.directory.ldap.client.template.exception.PasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LbLdapClient
{
    private static final Logger log = LoggerFactory.getLogger(LbLdapClient.class);
    private LdapConnectionTemplate ldapConnectionTemplate;
    private LdapConfiguration config;
    private UserEntryMapper userRecordEntryMapper;

    public LbLdapClient(LdapConfiguration ldapConfig)
    {
        config = ldapConfig;
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
                    trustStorePassword.toCharArray(), null, true));
        }

        DefaultLdapConnectionFactory defaultFactory =
                new DefaultLdapConnectionFactory(connectionConfig);

        //A single connection and keep it alive
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(1);
        poolConfig.setMaxTotal(1);
        poolConfig.setMinIdle(0);

        ValidatingPoolableLdapConnectionFactory validatingFactory =
                new ValidatingPoolableLdapConnectionFactory(defaultFactory);
        LdapConnectionPool connectionPool = new LdapConnectionPool(validatingFactory, poolConfig);
        ldapConnectionTemplate = new LdapConnectionTemplate(connectionPool);
        userRecordEntryMapper = new UserEntryMapper(config.getLdapGroupMemberAttribute());
    }

    public boolean authenticate(String user, String password)
    {
        try {
            String filter = config.getLdapUserSearch().replace("${USER}", user);
            PasswordWarning passwordWarning =
                    ldapConnectionTemplate.authenticate(config.getLdapUserBaseDn(),
                            filter,
                            SearchScope.SUBTREE,
                            password.toCharArray());

            if (passwordWarning != null) {
                log.warn("password warning {}", passwordWarning);
                return true;
            }
        }
        catch (PasswordException exception) {
            log.error("Failed to authenticate {}", exception.getResultCode());
            return false;
        }
        log.info("Authenticated successfully");
        return true;
    }

    public String getMemberOf(String user)
    {
        String filter = config.getLdapUserSearch().replace("${USER}", user);

        String[] attributes = new String[] {config.getLdapGroupMemberAttribute()};
        List<UserRecord> list = ldapConnectionTemplate.search(config.getLdapUserBaseDn(),
                filter,
                SearchScope.SUBTREE,
                attributes,
                userRecordEntryMapper);

        String memberOf = "";
        if (list != null && !list.isEmpty()) {
            memberOf = list.listIterator().next().getMemberOf();
            log.debug("Member of {}", memberOf);
        }
        return memberOf;
    }

    public static class UserRecord
    {
        String memberOf;

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
        String memberOf;

        public UserEntryMapper(String memberOfAttr)
        {
            memberOf = memberOfAttr;
        }

        @Override
        public UserRecord map(Entry entry)
                throws LdapException
        {
            return new UserRecord(entry.get(memberOf).toString());
        }
    }
}
