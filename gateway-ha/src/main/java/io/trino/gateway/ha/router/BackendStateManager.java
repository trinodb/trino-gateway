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

import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BackendStateManager
{
    private final Map<String, ClusterStats> clusterStats;

    public BackendStateManager()
    {
        this.clusterStats = new HashMap<>();
    }

    public BackendState getBackendState(ProxyBackendConfiguration backend)
    {
        String name = backend.getName();
        ClusterStats stats = clusterStats.getOrDefault(backend.getName(), ClusterStats.builder(name).build());
        Map<String, Integer> state = new HashMap<>();
        state.put("QUEUED", stats.queuedQueryCount());
        state.put("RUNNING", stats.runningQueryCount());
        return new BackendState(name, state);
    }

    public void updateStates(String clusterId, ClusterStats stats)
    {
        clusterStats.put(clusterId, stats);
    }

    public record BackendState(String name, Map<String, Integer> state)
    {
        public BackendState
        {
            requireNonNull(name, "name is null");
            state = ImmutableMap.copyOf(requireNonNull(state, "state is null"));
        }
    }
}
