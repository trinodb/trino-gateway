package io.trino.gateway.ha.config;

import io.trino.gateway.proxyserver.ProxyServerConfiguration;

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
    {this.externalUrl = externalUrl;}

    public boolean isActive()
    {return this.active;}

    public void setActive(boolean active)
    {this.active = active;}

    public String getRoutingGroup()
    {return this.routingGroup;}

    public void setRoutingGroup(String routingGroup)
    {this.routingGroup = routingGroup;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProxyBackendConfiguration)) {
            return false;
        }
        final ProxyBackendConfiguration other = (ProxyBackendConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        if (this.isActive() != other.isActive()) {
            return false;
        }
        final Object this$routingGroup = this.getRoutingGroup();
        final Object other$routingGroup = other.getRoutingGroup();
        if (this$routingGroup == null ? other$routingGroup != null : !this$routingGroup.equals(other$routingGroup)) {
            return false;
        }
        final Object this$externalUrl = this.getExternalUrl();
        final Object other$externalUrl = other.getExternalUrl();
        if (this$externalUrl == null ? other$externalUrl != null : !this$externalUrl.equals(other$externalUrl)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof ProxyBackendConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = super.hashCode();
        result = result * PRIME + (this.isActive() ? 79 : 97);
        final Object $routingGroup = this.getRoutingGroup();
        result = result * PRIME + ($routingGroup == null ? 43 : $routingGroup.hashCode());
        final Object $externalUrl = this.getExternalUrl();
        result = result * PRIME + ($externalUrl == null ? 43 : $externalUrl.hashCode());
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
