package io.trino.gateway.ha.config;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class LdapConfiguration
{
    private static final Logger log = LoggerFactory.getLogger(LdapConfiguration.class);
    private String ldapHost;
    private Integer ldapPort;
    private boolean useTls;
    private boolean useSsl;
    private String ldapAdminBindDn;
    private String ldapUserBaseDn;
    private String ldapUserSearch;
    private String ldapGroupMemberAttribute;
    private String ldapAdminPassword;
    private String ldapTrustStorePath;
    private String ldapTrustStorePassword;

    public LdapConfiguration(String ldapHost, Integer ldapPort, boolean useTls, boolean useSsl, String ldapAdminBindDn, String ldapUserBaseDn, String ldapUserSearch, String ldapGroupMemberAttribute, String ldapAdminPassword, String ldapTrustStorePath, String ldapTrustStorePassword)
    {
        this.ldapHost = ldapHost;
        this.ldapPort = ldapPort;
        this.useTls = useTls;
        this.useSsl = useSsl;
        this.ldapAdminBindDn = ldapAdminBindDn;
        this.ldapUserBaseDn = ldapUserBaseDn;
        this.ldapUserSearch = ldapUserSearch;
        this.ldapGroupMemberAttribute = ldapGroupMemberAttribute;
        this.ldapAdminPassword = ldapAdminPassword;
        this.ldapTrustStorePath = ldapTrustStorePath;
        this.ldapTrustStorePassword = ldapTrustStorePassword;
    }

    public LdapConfiguration() {}

    public static LdapConfiguration load(String path)
    {
        LdapConfiguration configuration = null;
        try {
            configuration =
                    new YamlConfigurationFactory<LdapConfiguration>(LdapConfiguration.class,
                            null,
                            Jackson.newObjectMapper(), "lb")
                            .build(new java.io.File(path));
        }
        catch (java.io.IOException e) {
            log.error("Error loading configuration file", e);
            throw new RuntimeException(e);
        }
        catch (ConfigurationException e) {
            log.error("Error loading configuration file", e);
            throw new RuntimeException(e);
        }
        return configuration;
    }

    public String getLdapHost()
    {
        return this.ldapHost;
    }

    public void setLdapHost(String ldapHost)
    {
        this.ldapHost = ldapHost;
    }

    public Integer getLdapPort()
    {
        return this.ldapPort;
    }

    public void setLdapPort(Integer ldapPort)
    {
        this.ldapPort = ldapPort;
    }

    public boolean isUseTls()
    {
        return this.useTls;
    }

    public void setUseTls(boolean useTls)
    {
        this.useTls = useTls;
    }

    public boolean isUseSsl()
    {
        return this.useSsl;
    }

    public void setUseSsl(boolean useSsl)
    {
        this.useSsl = useSsl;
    }

    public String getLdapAdminBindDn()
    {
        return this.ldapAdminBindDn;
    }

    public void setLdapAdminBindDn(String ldapAdminBindDn)
    {
        this.ldapAdminBindDn = ldapAdminBindDn;
    }

    public String getLdapUserBaseDn()
    {
        return this.ldapUserBaseDn;
    }

    public void setLdapUserBaseDn(String ldapUserBaseDn)
    {
        this.ldapUserBaseDn = ldapUserBaseDn;
    }

    public String getLdapUserSearch()
    {
        return this.ldapUserSearch;
    }

    public void setLdapUserSearch(String ldapUserSearch)
    {
        this.ldapUserSearch = ldapUserSearch;
    }

    public String getLdapGroupMemberAttribute()
    {
        return this.ldapGroupMemberAttribute;
    }

    public void setLdapGroupMemberAttribute(String ldapGroupMemberAttribute)
    {
        this.ldapGroupMemberAttribute = ldapGroupMemberAttribute;
    }

    public String getLdapAdminPassword()
    {
        return this.ldapAdminPassword;
    }

    public void setLdapAdminPassword(String ldapAdminPassword)
    {
        this.ldapAdminPassword = ldapAdminPassword;
    }

    public String getLdapTrustStorePath()
    {
        return this.ldapTrustStorePath;
    }

    public void setLdapTrustStorePath(String ldapTrustStorePath)
    {
        this.ldapTrustStorePath = ldapTrustStorePath;
    }

    public String getLdapTrustStorePassword()
    {
        return this.ldapTrustStorePassword;
    }

    public void setLdapTrustStorePassword(String ldapTrustStorePassword)
    {
        this.ldapTrustStorePassword = ldapTrustStorePassword;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LdapConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object ldapHost = this.getLdapHost();
        final Object otherLdapHost = other.getLdapHost();
        if (!Objects.equals(ldapHost, otherLdapHost)) {
            return false;
        }
        final Object ldapPort = this.getLdapPort();
        final Object otherLdapPort = other.getLdapPort();
        if (!Objects.equals(ldapPort, otherLdapPort)) {
            return false;
        }
        if (this.isUseTls() != other.isUseTls()) {
            return false;
        }
        if (this.isUseSsl() != other.isUseSsl()) {
            return false;
        }
        final Object ldapAdminBindDn = this.getLdapAdminBindDn();
        final Object otherLdapAdminBindDn = other.getLdapAdminBindDn();
        if (!Objects.equals(ldapAdminBindDn, otherLdapAdminBindDn)) {
            return false;
        }
        final Object ldapUserBaseDn = this.getLdapUserBaseDn();
        final Object otherLdapUserBaseDn = other.getLdapUserBaseDn();
        if (!Objects.equals(ldapUserBaseDn, otherLdapUserBaseDn)) {
            return false;
        }
        final Object ldapUserSearch = this.getLdapUserSearch();
        final Object otherLdapUserSearch = other.getLdapUserSearch();
        if (!Objects.equals(ldapUserSearch, otherLdapUserSearch)) {
            return false;
        }
        final Object ldapGroupMemberAttribute = this.getLdapGroupMemberAttribute();
        final Object otherLdapGroupMemberAttribute = other.getLdapGroupMemberAttribute();
        if (!Objects.equals(ldapGroupMemberAttribute, otherLdapGroupMemberAttribute)) {
            return false;
        }
        final Object ldapAdminPassword = this.getLdapAdminPassword();
        final Object otherLdapAdminPassword = other.getLdapAdminPassword();
        if (!Objects.equals(ldapAdminPassword, otherLdapAdminPassword)) {
            return false;
        }
        final Object ldapTrustStorePath = this.getLdapTrustStorePath();
        final Object otherLdapTrustStorePath = other.getLdapTrustStorePath();
        if (!Objects.equals(ldapTrustStorePath, otherLdapTrustStorePath)) {
            return false;
        }
        final Object ldapTrustStorePassword = this.getLdapTrustStorePassword();
        final Object otherLdapTrustStorePassword = other.getLdapTrustStorePassword();
        return Objects.equals(ldapTrustStorePassword, otherLdapTrustStorePassword);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof LdapConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object ldapHost = this.getLdapHost();
        result = result * prime + (ldapHost == null ? 43 : ldapHost.hashCode());
        final Object ldapPort = this.getLdapPort();
        result = result * prime + (ldapPort == null ? 43 : ldapPort.hashCode());
        result = result * prime + (this.isUseTls() ? 79 : 97);
        result = result * prime + (this.isUseSsl() ? 79 : 97);
        final Object ldapAdminBindDn = this.getLdapAdminBindDn();
        result = result * prime + (ldapAdminBindDn == null ? 43 : ldapAdminBindDn.hashCode());
        final Object ldapUserBaseDn = this.getLdapUserBaseDn();
        result = result * prime + (ldapUserBaseDn == null ? 43 : ldapUserBaseDn.hashCode());
        final Object ldapUserSearch = this.getLdapUserSearch();
        result = result * prime + (ldapUserSearch == null ? 43 : ldapUserSearch.hashCode());
        final Object xldapGroupMemberAttribute = this.getLdapGroupMemberAttribute();
        result = result * prime + (xldapGroupMemberAttribute == null ? 43 : xldapGroupMemberAttribute.hashCode());
        final Object ldapAdminPassword = this.getLdapAdminPassword();
        result = result * prime + (ldapAdminPassword == null ? 43 : ldapAdminPassword.hashCode());
        final Object ldapTrustStorePath = this.getLdapTrustStorePath();
        result = result * prime + (ldapTrustStorePath == null ? 43 : ldapTrustStorePath.hashCode());
        final Object ldapTrustStorePassword = this.getLdapTrustStorePassword();
        result = result * prime + (ldapTrustStorePassword == null ? 43 : ldapTrustStorePassword.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "LdapConfiguration{" +
                "ldapHost='" + ldapHost + '\'' +
                ", ldapPort=" + ldapPort +
                ", useTls=" + useTls +
                ", useSsl=" + useSsl +
                ", ldapAdminBindDn='" + ldapAdminBindDn + '\'' +
                ", ldapUserBaseDn='" + ldapUserBaseDn + '\'' +
                ", ldapUserSearch='" + ldapUserSearch + '\'' +
                ", ldapGroupMemberAttribute='" + ldapGroupMemberAttribute + '\'' +
                ", ldapAdminPassword='" + ldapAdminPassword + '\'' +
                ", ldapTrustStorePath='" + ldapTrustStorePath + '\'' +
                ", ldapTrustStorePassword='" + ldapTrustStorePassword + '\'' +
                '}';
    }
}
