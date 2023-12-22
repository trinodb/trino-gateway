package io.trino.gateway.ha.config;

import io.trino.gateway.proxyserver.ProxyServerConfiguration;

import java.util.Objects;

public class ProxyBackendConfiguration
        extends ProxyServerConfiguration
{
    private boolean active = true;
    private String routingGroup = "adhoc";
    private String externalUrl;

    public ProxyBackendConfiguration() {}

    public String getExternalUrl()
    {
        if (externalUrl == null) {
            return getProxyTo();
        }
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl)
    {
        this.externalUrl = externalUrl;
    }

    public boolean isActive()
    {
        return this.active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    public String getRoutingGroup()
    {
        return this.routingGroup;
    }

    public void setRoutingGroup(String routingGroup)
    {
        this.routingGroup = routingGroup;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProxyBackendConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        if (this.isActive() != other.isActive()) {
            return false;
        }
        final Object routingGroup = this.getRoutingGroup();
        final Object otherRoutingGroup = other.getRoutingGroup();
        if (!Objects.equals(routingGroup, otherRoutingGroup)) {
            return false;
        }
        final Object externalUrl = this.getExternalUrl();
        final Object otherExternalUrl = other.getExternalUrl();
        return Objects.equals(externalUrl, otherExternalUrl);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof ProxyBackendConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = super.hashCode();
        result = result * prime + (this.isActive() ? 79 : 97);
        final Object routingGroup = this.getRoutingGroup();
        result = result * prime + (routingGroup == null ? 43 : routingGroup.hashCode());
        final Object externalUrl = this.getExternalUrl();
        result = result * prime + (externalUrl == null ? 43 : externalUrl.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "ProxyBackendConfiguration{" +
                "active=" + active +
                ", routingGroup='" + routingGroup + '\'' +
                ", externalUrl='" + externalUrl + '\'' +
                '}';
    }
}
