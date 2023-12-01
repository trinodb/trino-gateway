package io.trino.gateway.ha.router;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaRoutingManager extends RoutingManager {
  private static final Logger log = LoggerFactory.getLogger(HaRoutingManager.class);
  QueryHistoryManager queryHistoryManager;

  public HaRoutingManager(
      GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager) {
    super(gatewayBackendManager);
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
