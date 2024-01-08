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
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public class BackendStateManager
{
    @Nullable
    private final BackendStateConfiguration configuration;

    private final Map<String, ClusterStats> clusterStats;

    public BackendStateManager(BackendStateConfiguration configuration)
    {
        this.configuration = configuration;
        this.clusterStats = new HashMap<>();
    }

    public BackendState getBackendState(ProxyBackendConfiguration backend)
    {
        String name = backend.getName();
        ClusterStats stats = clusterStats.getOrDefault(backend.getName(), new ClusterStats());
        Map<String, Integer> state = new HashMap<>();
        state.put("QUEUED", stats.getQueuedQueryCount());
        state.put("RUNNING", stats.getRunningQueryCount());
        return new BackendState(name, state);
    }

    public BackendStateConfiguration getBackendStateConfiguration()
    {
        return this.configuration;
    }

    public void updateStates(String clusterId, ClusterStats stats)
    {
        clusterStats.put(clusterId, stats);
    }

    public static class BackendState
    {
        private final String name;
        private final Map<String, Integer> state;

        public BackendState(String name, Map<String, Integer> state)
        {
            this.name = name;
            this.state = state;
        }

        public String getName()
        {
            return this.name;
        }

        public Map<String, Integer> getState()
        {
            return this.state;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BackendState that = (BackendState) o;
            return Objects.equals(name, that.name) && Objects.equals(state, that.state);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, state);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("name", name)
                    .add("state", state)
                    .toString();
        }
    }
}
