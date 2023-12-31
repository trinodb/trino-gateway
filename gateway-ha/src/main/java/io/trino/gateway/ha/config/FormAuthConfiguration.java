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

public class FormAuthConfiguration
{
    private SelfSignKeyPairConfiguration selfSignKeyPair;
    private String ldapConfigPath;

    public FormAuthConfiguration(SelfSignKeyPairConfiguration selfSignKeyPair, String ldapConfigPath)
    {
        this.selfSignKeyPair = selfSignKeyPair;
        this.ldapConfigPath = ldapConfigPath;
    }

    public FormAuthConfiguration() {}

    public SelfSignKeyPairConfiguration getSelfSignKeyPair()
    {
        return this.selfSignKeyPair;
    }

    public void setSelfSignKeyPair(SelfSignKeyPairConfiguration selfSignKeyPair)
    {
        this.selfSignKeyPair = selfSignKeyPair;
    }

    public String getLdapConfigPath()
    {
        return this.ldapConfigPath;
    }

    public void setLdapConfigPath(String ldapConfigPath)
    {
        this.ldapConfigPath = ldapConfigPath;
    }
}
