package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.TrinoQueueLengthRoutingTable;

public class QueueLengthRouterProvider extends RouterBaseModule {
  private final TrinoQueueLengthRoutingTable routingManager;

  public QueueLengthRouterProvider(HaGatewayConfiguration configuration, Environment environment) {
    super(configuration, environment);
    routingManager = new TrinoQueueLengthRoutingTable(gatewayBackendManager,
          (HaQueryHistoryManager) queryHistoryManager);
  }

  @Provides
  public TrinoQueueLengthRoutingTable getTrinoQueueLengthRoutingTableManager() {
    return this.routingManager;
  }

  @Provides
  public RoutingManager getRoutingManager() {
    return getTrinoQueueLengthRoutingTableManager();
  }

}
