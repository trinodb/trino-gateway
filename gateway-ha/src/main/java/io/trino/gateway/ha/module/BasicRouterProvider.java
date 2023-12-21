package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import io.dropwizard.core.setup.Environment;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaRoutingManager;
import io.trino.gateway.ha.router.RoutingManager;

public class BasicRouterProvider extends RouterBaseModule {
  private final HaRoutingManager routingManager;

  public BasicRouterProvider(HaGatewayConfiguration configuration, Environment environment) {
    super(configuration, environment);
    routingManager = new HaRoutingManager(gatewayBackendManager,
          (HaQueryHistoryManager) queryHistoryManager);
  }

  @Provides
  public HaRoutingManager getHaRoutingManager() {
    return this.routingManager;
  }
  
  @Provides
  public RoutingManager getRoutingManager() {
    return getHaRoutingManager();
  }
}
