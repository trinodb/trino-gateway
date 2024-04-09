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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.airlift.log.Logger;

import java.io.File;
import java.io.IOException;

public class LdapConfiguration
{
    private static final Logger log = Logger.get(LdapConfiguration.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
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
    private Integer poolMaxIdle;
    private Integer poolMaxTotal;
    private Integer poolMinIdle;
    private boolean poolTestOnBorrow;

    public LdapConfiguration(
            String ldapHost,
            Integer ldapPort,
            boolean useTls,
            boolean useSsl,
            String ldapAdminBindDn,
            String ldapUserBaseDn,
            String ldapUserSearch,
            String ldapGroupMemberAttribute,
            String ldapAdminPassword,
            String ldapTrustStorePath,
            String ldapTrustStorePassword,
            Integer poolMaxIdle,
            Integer poolMaxTotal,
            Integer poolMinIdle,
            boolean poolTestOnBorrow)
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
        this.poolMaxIdle = poolMaxIdle;
        this.poolMaxTotal = poolMaxTotal;
        this.poolMinIdle = poolMinIdle;
        this.poolTestOnBorrow = poolTestOnBorrow;
    }

    public LdapConfiguration() {}

    public static LdapConfiguration load(String path)
    {
        LdapConfiguration configuration = null;
        try {
            configuration = OBJECT_MAPPER.readValue(new File(path), LdapConfiguration.class);
        }
        catch (IOException e) {
            log.error(e, "Error loading configuration file");
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

    public Integer getPoolMaxIdle()
    {
        return this.poolMaxIdle;
    }

    public void setPoolMaxIdle(Integer poolMaxIdle)
    {
        this.poolMaxIdle = poolMaxIdle;
    }

    public Integer getPoolMaxTotal()
    {
        return this.poolMaxTotal;
    }

    public void setPoolMaxTotal(Integer poolMaxTotal)
    {
        this.poolMaxTotal = poolMaxTotal;
    }

    public Integer getPoolMinIdle()
    {
        return this.poolMinIdle;
    }

    public void setPoolMinIdle(Integer poolMinIdle)
    {
        this.poolMinIdle = poolMinIdle;
    }

    public boolean isPoolTestOnBorrow()
    {
        return this.poolTestOnBorrow;
    }

    public void setPoolTestOnBorrow(boolean poolTestOnBorrow)
    {
        this.poolTestOnBorrow = poolTestOnBorrow;
    }
}
