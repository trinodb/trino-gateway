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

public class AuthorizationConfiguration
{
    private String admin;
    private String user;
    private String api;
    private String ldapConfigPath;
    private String defaultPrivilege;

    public AuthorizationConfiguration(String admin, String user, String api, String ldapConfigPath, String defaultPrivilege)
    {
        this.admin = admin;
        this.user = user;
        this.api = api;
        this.ldapConfigPath = ldapConfigPath;
        this.defaultPrivilege = defaultPrivilege;
    }

    public AuthorizationConfiguration() {}

    public String getAdmin()
    {
        return this.admin;
    }

    public void setAdmin(String admin)
    {
        this.admin = admin;
    }

    public String getUser()
    {
        return this.user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getApi()
    {
        return this.api;
    }

    public void setApi(String api)
    {
        this.api = api;
    }

    public String getLdapConfigPath()
    {
        return this.ldapConfigPath;
    }

    public void setLdapConfigPath(String ldapConfigPath)
    {
        this.ldapConfigPath = ldapConfigPath;
    }

    public String getDefaultPrivilege()
    {
        return this.defaultPrivilege;
    }

    public void setDefaultPrivilege(String defaultPrivilege)
    {
        this.defaultPrivilege = defaultPrivilege;
    }
}
