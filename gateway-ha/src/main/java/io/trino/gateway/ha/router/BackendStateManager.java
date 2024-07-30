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

import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.HashMap;
import java.util.Map;

public class BackendStateManager
{
    private final Map<String, ClusterStats> clusterStats;

    public BackendStateManager()
    {
        this.clusterStats = new HashMap<>();
    }

    public ClusterStats getBackendState(ProxyBackendConfiguration backend)
    {
        String name = backend.getName();
        return clusterStats.getOrDefault(name, ClusterStats.builder(name).build());
    }

    public Map<String, ClusterStats> getAllBackendStates()
    {
        return clusterStats;
    }

    public void updateStates(String clusterId, ClusterStats stats)
    {
        clusterStats.put(clusterId, stats);
    }
}
