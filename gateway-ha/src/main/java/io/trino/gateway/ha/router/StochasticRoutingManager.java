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

import com.google.inject.Inject;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class StochasticRoutingManager
        extends BaseRoutingManager
{
    @Inject
    public StochasticRoutingManager(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            RoutingConfiguration routingConfiguration)
    {
        super(gatewayBackendManager, queryHistoryManager, routingConfiguration);
    }

    @Override
    protected Optional<ProxyBackendConfiguration> selectBackend(List<ProxyBackendConfiguration> backends, String user)
    {
        if (backends.isEmpty()) {
            return Optional.empty();
        }
        int backendId = ThreadLocalRandom.current().nextInt(backends.size());
        return Optional.of(backends.get(backendId));
    }
}
