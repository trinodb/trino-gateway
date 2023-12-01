package io.trino.gateway.ha.config;

public class ClusterStatsConfiguration
{
    private boolean useApi;

    public ClusterStatsConfiguration() {}

    public boolean isUseApi()
    {return this.useApi;}

    public void setUseApi(boolean useApi)
    {this.useApi = useApi;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ClusterStatsConfiguration)) {
            return false;
        }
        final ClusterStatsConfiguration other = (ClusterStatsConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isUseApi() != other.isUseApi()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof ClusterStatsConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isUseApi() ? 79 : 97);
        return result;
    }

    @Override
    public String toString()
    {
        return "ClusterStatsConfiguration{" + "useApi=" + useApi + '}';
    }
}
