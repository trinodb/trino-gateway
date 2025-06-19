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

import static java.util.Objects.requireNonNull;

public class ClusterMetricsStats
{
    private final String clusterName;
    private final GatewayBackendManager gatewayBackendManager;

    public ClusterMetricsStats(String clusterName, GatewayBackendManager gatewayBackendManager)
    {
        this.clusterName = requireNonNull(clusterName, "clusterName is null");
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
    }

    public String getClusterName()
    {
        return clusterName;
    }

    @Managed
    public int getActivationStatus()
    {
        return gatewayBackendManager.getBackendByName(clusterName)
                .map(cluster -> cluster.isActive() ? 1 : 0)
                .orElse(-1);
    }
}
