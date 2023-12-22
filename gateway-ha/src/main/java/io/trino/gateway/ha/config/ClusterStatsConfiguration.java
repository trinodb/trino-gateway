package io.trino.gateway.ha.config;

public class ClusterStatsConfiguration
{
    private boolean useApi;

    public ClusterStatsConfiguration() {}

    public boolean isUseApi()
    {
        return this.useApi;
    }

    public void setUseApi(boolean useApi)
    {
        this.useApi = useApi;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ClusterStatsConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return this.isUseApi() == other.isUseApi();
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof ClusterStatsConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        result = result * prime + (this.isUseApi() ? 79 : 97);
        return result;
    }

    @Override
    public String toString()
    {
        return "ClusterStatsConfiguration{" + "useApi=" + useApi + '}';
    }
}
