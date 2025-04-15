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
package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.router.GatewayBackendManager;
import org.weakref.jmx.Managed;

public class BackendClusterMetricStats
{
    private final String clusterName;
    private final GatewayBackendManager gatewayBackendManager;

    public BackendClusterMetricStats(String clusterName, GatewayBackendManager gatewayBackendManager)
    {
        this.clusterName = clusterName;
        this.gatewayBackendManager = gatewayBackendManager;
    }

    public String getClusterName()
    {
        return clusterName;
    }

    @Managed
    public int getActivationStatus()
    {
        return gatewayBackendManager.getBackendByName(clusterName)
                .map(backend -> backend.isActive() ? 1 : 0)
                .orElse(-1);
    }
}
