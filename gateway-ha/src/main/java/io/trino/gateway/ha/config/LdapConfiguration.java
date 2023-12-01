package io.trino.gateway.ha.config;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    {return this.ldapHost;}

    public void setLdapHost(String ldapHost)
    {this.ldapHost = ldapHost;}

    public Integer getLdapPort()
    {return this.ldapPort;}

    public void setLdapPort(Integer ldapPort)
    {this.ldapPort = ldapPort;}

    public boolean isUseTls()
    {return this.useTls;}

    public void setUseTls(boolean useTls)
    {this.useTls = useTls;}

    public boolean isUseSsl()
    {return this.useSsl;}

    public void setUseSsl(boolean useSsl)
    {this.useSsl = useSsl;}

    public String getLdapAdminBindDn()
    {return this.ldapAdminBindDn;}

    public void setLdapAdminBindDn(String ldapAdminBindDn)
    {this.ldapAdminBindDn = ldapAdminBindDn;}

    public String getLdapUserBaseDn()
    {return this.ldapUserBaseDn;}

    public void setLdapUserBaseDn(String ldapUserBaseDn)
    {this.ldapUserBaseDn = ldapUserBaseDn;}

    public String getLdapUserSearch()
    {return this.ldapUserSearch;}

    public void setLdapUserSearch(String ldapUserSearch)
    {this.ldapUserSearch = ldapUserSearch;}

    public String getLdapGroupMemberAttribute()
    {return this.ldapGroupMemberAttribute;}

    public void setLdapGroupMemberAttribute(String ldapGroupMemberAttribute)
    {this.ldapGroupMemberAttribute = ldapGroupMemberAttribute;}

    public String getLdapAdminPassword()
    {return this.ldapAdminPassword;}

    public void setLdapAdminPassword(String ldapAdminPassword)
    {this.ldapAdminPassword = ldapAdminPassword;}

    public String getLdapTrustStorePath()
    {return this.ldapTrustStorePath;}

    public void setLdapTrustStorePath(String ldapTrustStorePath)
    {this.ldapTrustStorePath = ldapTrustStorePath;}

    public String getLdapTrustStorePassword()
    {return this.ldapTrustStorePassword;}

    public void setLdapTrustStorePassword(String ldapTrustStorePassword)
    {this.ldapTrustStorePassword = ldapTrustStorePassword;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LdapConfiguration)) {
            return false;
        }
        final LdapConfiguration other = (LdapConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$ldapHost = this.getLdapHost();
        final Object other$ldapHost = other.getLdapHost();
        if (this$ldapHost == null ? other$ldapHost != null : !this$ldapHost.equals(other$ldapHost)) {
            return false;
        }
        final Object this$ldapPort = this.getLdapPort();
        final Object other$ldapPort = other.getLdapPort();
        if (this$ldapPort == null ? other$ldapPort != null : !this$ldapPort.equals(other$ldapPort)) {
            return false;
        }
        if (this.isUseTls() != other.isUseTls()) {
            return false;
        }
        if (this.isUseSsl() != other.isUseSsl()) {
            return false;
        }
        final Object this$ldapAdminBindDn = this.getLdapAdminBindDn();
        final Object other$ldapAdminBindDn = other.getLdapAdminBindDn();
        if (this$ldapAdminBindDn == null ? other$ldapAdminBindDn != null : !this$ldapAdminBindDn.equals(other$ldapAdminBindDn)) {
            return false;
        }
        final Object this$ldapUserBaseDn = this.getLdapUserBaseDn();
        final Object other$ldapUserBaseDn = other.getLdapUserBaseDn();
        if (this$ldapUserBaseDn == null ? other$ldapUserBaseDn != null : !this$ldapUserBaseDn.equals(other$ldapUserBaseDn)) {
            return false;
        }
        final Object this$ldapUserSearch = this.getLdapUserSearch();
        final Object other$ldapUserSearch = other.getLdapUserSearch();
        if (this$ldapUserSearch == null ? other$ldapUserSearch != null : !this$ldapUserSearch.equals(other$ldapUserSearch)) {
            return false;
        }
        final Object this$ldapGroupMemberAttribute = this.getLdapGroupMemberAttribute();
        final Object other$ldapGroupMemberAttribute = other.getLdapGroupMemberAttribute();
        if (this$ldapGroupMemberAttribute == null ? other$ldapGroupMemberAttribute != null : !this$ldapGroupMemberAttribute.equals(other$ldapGroupMemberAttribute)) {
            return false;
        }
        final Object this$ldapAdminPassword = this.getLdapAdminPassword();
        final Object other$ldapAdminPassword = other.getLdapAdminPassword();
        if (this$ldapAdminPassword == null ? other$ldapAdminPassword != null : !this$ldapAdminPassword.equals(other$ldapAdminPassword)) {
            return false;
        }
        final Object this$ldapTrustStorePath = this.getLdapTrustStorePath();
        final Object other$ldapTrustStorePath = other.getLdapTrustStorePath();
        if (this$ldapTrustStorePath == null ? other$ldapTrustStorePath != null : !this$ldapTrustStorePath.equals(other$ldapTrustStorePath)) {
            return false;
        }
        final Object this$ldapTrustStorePassword = this.getLdapTrustStorePassword();
        final Object other$ldapTrustStorePassword = other.getLdapTrustStorePassword();
        if (this$ldapTrustStorePassword == null ? other$ldapTrustStorePassword != null : !this$ldapTrustStorePassword.equals(other$ldapTrustStorePassword)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof LdapConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $ldapHost = this.getLdapHost();
        result = result * PRIME + ($ldapHost == null ? 43 : $ldapHost.hashCode());
        final Object $ldapPort = this.getLdapPort();
        result = result * PRIME + ($ldapPort == null ? 43 : $ldapPort.hashCode());
        result = result * PRIME + (this.isUseTls() ? 79 : 97);
        result = result * PRIME + (this.isUseSsl() ? 79 : 97);
        final Object $ldapAdminBindDn = this.getLdapAdminBindDn();
        result = result * PRIME + ($ldapAdminBindDn == null ? 43 : $ldapAdminBindDn.hashCode());
        final Object $ldapUserBaseDn = this.getLdapUserBaseDn();
        result = result * PRIME + ($ldapUserBaseDn == null ? 43 : $ldapUserBaseDn.hashCode());
        final Object $ldapUserSearch = this.getLdapUserSearch();
        result = result * PRIME + ($ldapUserSearch == null ? 43 : $ldapUserSearch.hashCode());
        final Object $ldapGroupMemberAttribute = this.getLdapGroupMemberAttribute();
        result = result * PRIME + ($ldapGroupMemberAttribute == null ? 43 : $ldapGroupMemberAttribute.hashCode());
        final Object $ldapAdminPassword = this.getLdapAdminPassword();
        result = result * PRIME + ($ldapAdminPassword == null ? 43 : $ldapAdminPassword.hashCode());
        final Object $ldapTrustStorePath = this.getLdapTrustStorePath();
        result = result * PRIME + ($ldapTrustStorePath == null ? 43 : $ldapTrustStorePath.hashCode());
        final Object $ldapTrustStorePassword = this.getLdapTrustStorePassword();
        result = result * PRIME + ($ldapTrustStorePassword == null ? 43 : $ldapTrustStorePassword.hashCode());
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
