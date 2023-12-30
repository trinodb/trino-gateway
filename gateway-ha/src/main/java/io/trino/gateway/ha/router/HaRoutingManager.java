/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
