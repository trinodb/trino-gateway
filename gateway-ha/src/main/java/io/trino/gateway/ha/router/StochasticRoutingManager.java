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
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RoutingConfiguration;

public class StochasticRoutingManager
        extends RoutingManager
{
    private static final Logger log = Logger.get(StochasticRoutingManager.class);
    private final QueryHistoryManager queryHistoryManager;

    @Inject
    public StochasticRoutingManager(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            RoutingConfiguration routingConfiguration)
    {
        super(gatewayBackendManager, queryHistoryManager, routingConfiguration);
        this.queryHistoryManager = queryHistoryManager;
    }

    @Override
    protected String findBackendForUnknownQueryId(String queryId)
    {
        String backend;
        backend = queryHistoryManager.getBackendForQueryId(queryId);
        if (Strings.isNullOrEmpty(backend)) {
            log.debug("Unable to find backend mapping for [%s]. Searching for suitable backend", queryId);
            backend = super.findBackendForUnknownQueryId(queryId);
        }
        return backend;
    }
}
