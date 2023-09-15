package io.trino.gateway.ha.router;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaRoutingManager extends RoutingManager {
  QueryHistoryManager queryHistoryManager;

  public HaRoutingManager(
          GatewayBackendManager gatewayBackendManager,
          QueryHistoryManager queryHistoryManager,
          CookieCacheManager cacheManager) {
    super(gatewayBackendManager, cacheManager);
    this.queryHistoryManager = queryHistoryManager;
  }

  @Override
  protected String findBackendForUnknownQueryId(String queryId) {
    String backend;
    backend = queryHistoryManager.getBackendForQueryId(queryId);
    if (Strings.isNullOrEmpty(backend)) {
      log.debug("Unable to find backend mapping for [{}]. Searching for suitable backend", queryId);
      backend = super.findBackendForUnknownQueryId(queryId);
    }
    return backend;
  }

}
