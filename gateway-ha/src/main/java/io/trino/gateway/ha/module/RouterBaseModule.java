package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.baseapp.AppModule;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaResourceGroupsManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;

public class RouterBaseModule extends AppModule<HaGatewayConfiguration, Environment> {
  final ResourceGroupsManager resourceGroupsManager;
  final GatewayBackendManager gatewayBackendManager;
  final QueryHistoryManager queryHistoryManager;
  final JdbcConnectionManager connectionManager;

  public RouterBaseModule(HaGatewayConfiguration configuration, Environment environment) {
    super(configuration, environment);
    connectionManager     = new JdbcConnectionManager(configuration.getDataStore());
    resourceGroupsManager = new HaResourceGroupsManager(connectionManager);
    gatewayBackendManager = new HaGatewayManager(connectionManager);
    queryHistoryManager   = new HaQueryHistoryManager(connectionManager);
  }

  @Provides
  public JdbcConnectionManager getConnectionManager() {
    return this.connectionManager;
  }
  @Provides
  public ResourceGroupsManager getResourceGroupsManager() {
    return this.resourceGroupsManager;
  }

  @Provides
  public GatewayBackendManager getGatewayBackendManager() {
    return this.gatewayBackendManager;
  }

  @Provides
  public QueryHistoryManager getQueryHistoryManager() {
    return this.queryHistoryManager;
  }

}
