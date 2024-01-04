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
}
