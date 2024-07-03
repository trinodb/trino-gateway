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
package io.trino.gateway.ha.module;

import com.google.inject.Provides;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.QueryCountBasedRouter;
import io.trino.gateway.ha.router.RoutingManager;

public class QueryCountBasedRouterProvider
          extends RouterBaseModule
{
    private final QueryCountBasedRouter routingManager;

    public QueryCountBasedRouterProvider(HaGatewayConfiguration configuration)
    {
        super(configuration);
        routingManager = new QueryCountBasedRouter(gatewayBackendManager, queryHistoryManager);
    }

    @Provides
    public RoutingManager getRoutingManager()
    {
        return this.routingManager;
    }
}
