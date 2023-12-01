package io.trino.gateway.ha.config;

import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;

public class MonitorConfiguration
{
    private int connectionTimeout = ActiveClusterMonitor.BACKEND_CONNECT_TIMEOUT_SECONDS;
    private int taskDelayMin = ActiveClusterMonitor.MONITOR_TASK_DELAY_MIN;

    public MonitorConfiguration() {}

    public int getConnectionTimeout()
    {return this.connectionTimeout;}

    public void setConnectionTimeout(int connectionTimeout)
    {this.connectionTimeout = connectionTimeout;}

    public int getTaskDelayMin()
    {return this.taskDelayMin;}

    public void setTaskDelayMin(int taskDelayMin)
    {this.taskDelayMin = taskDelayMin;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MonitorConfiguration)) {
            return false;
        }
        final MonitorConfiguration other = (MonitorConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.getConnectionTimeout() != other.getConnectionTimeout()) {
            return false;
        }
        if (this.getTaskDelayMin() != other.getTaskDelayMin()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof MonitorConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getConnectionTimeout();
        result = result * PRIME + this.getTaskDelayMin();
        return result;
    }

    @Override
    public String toString()
    {
        return "MonitorConfiguration{" +
                "connectionTimeout=" + connectionTimeout +
                ", taskDelayMin=" + taskDelayMin +
                '}';
    }
}
