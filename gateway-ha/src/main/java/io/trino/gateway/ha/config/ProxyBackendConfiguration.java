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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

public class ProxyBackendConfiguration
{
    private boolean active = true;
    private String routingGroup = "adhoc";
    private String externalUrl;
    private String name;
    private String proxyTo;

    @JsonProperty
    public String getName()
    {
        return this.name;
    }

    @JsonProperty
    public String getProxyTo()
    {
        return this.proxyTo;
    }

    @JsonSetter
    public void setName(String name)
    {
        this.name = name;
    }

    @JsonSetter
    public void setProxyTo(String proxyTo)
    {
        this.proxyTo = proxyTo;
    }

    @JsonProperty
    public String getExternalUrl()
    {
        if (externalUrl == null) {
            return getProxyTo();
        }
        return externalUrl;
    }

    @JsonSetter
    public void setExternalUrl(String externalUrl)
    {
        this.externalUrl = externalUrl;
    }

    @JsonProperty
    public boolean isActive()
    {
        return this.active;
    }

    @JsonSetter
    public void setActive(boolean active)
    {
        this.active = active;
    }

    @JsonProperty
    public String getRoutingGroup()
    {
        return this.routingGroup;
    }

    @JsonSetter
    public void setRoutingGroup(String routingGroup)
    {
        this.routingGroup = routingGroup;
    }
}
