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
import io.trino.gateway.ha.audit.AuditAction;
import io.trino.gateway.ha.audit.AuditContext;

public class AdminProxyBackendConfiguration
{
    private final ProxyBackendConfiguration proxyBackendConfiguration = new ProxyBackendConfiguration();
    private AuditAction action;
    private String comment = "";
    private AuditContext context;

    @JsonProperty
    public String getName()
    {
        return proxyBackendConfiguration.getName();
    }

    @JsonSetter
    public void setName(String name)
    {
        proxyBackendConfiguration.setName(name);
    }

    @JsonProperty
    public String getProxyTo()
    {
        return proxyBackendConfiguration.getProxyTo();
    }

    @JsonSetter
    public void setProxyTo(String proxyTo)
    {
        proxyBackendConfiguration.setProxyTo(proxyTo);
    }

    @JsonProperty
    public String getExternalUrl()
    {
        return proxyBackendConfiguration.getExternalUrl();
    }

    @JsonSetter
    public void setExternalUrl(String externalUrl)
    {
        proxyBackendConfiguration.setExternalUrl(externalUrl);
    }

    @JsonProperty
    public boolean isActive()
    {
        return proxyBackendConfiguration.isActive();
    }

    @JsonSetter
    public void setActive(boolean active)
    {
        proxyBackendConfiguration.setActive(active);
    }

    @JsonProperty
    public String getRoutingGroup()
    {
        return proxyBackendConfiguration.getRoutingGroup();
    }

    @JsonSetter
    public void setRoutingGroup(String routingGroup)
    {
        proxyBackendConfiguration.setRoutingGroup(routingGroup);
    }

    @JsonProperty
    public AuditAction getAction()
    {
        return this.action;
    }

    @JsonSetter
    public void setAction(AuditAction action)
    {
        this.action = action;
    }

    @JsonProperty
    public String getComment()
    {
        return this.comment;
    }

    @JsonSetter
    public void setComment(String comment)
    {
        this.comment = comment;
    }

    @JsonProperty
    public AuditContext getContext()
    {
        return this.context;
    }

    @JsonSetter
    public void setContext(AuditContext context)
    {
        this.context = context;
    }

    public ProxyBackendConfiguration getProxyBackendConfiguration()
    {
        return proxyBackendConfiguration;
    }
}
