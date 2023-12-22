package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.clustermonitor.ClusterStatsHttpMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsJdbcMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor;
import io.trino.gateway.ha.config.ClusterStatsConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

public class ClusterStatsMonitorModule
        extends AppModule<HaGatewayConfiguration, Environment>
{
    private final HaGatewayConfiguration config;

    public ClusterStatsMonitorModule(HaGatewayConfiguration config, Environment env)
    {
        super(config, env);
        this.config = config;
    }

    @Provides
    @Singleton
    public ClusterStatsMonitor getClusterStatsMonitor()
    {
        ClusterStatsConfiguration clusterStatsConfig = config.getClusterStatsConfiguration();
        if (clusterStatsConfig.isUseApi()) {
            return new ClusterStatsHttpMonitor(config.getBackendState());
        }
        else {
            return new ClusterStatsJdbcMonitor(config.getBackendState());
        }
    }
}
