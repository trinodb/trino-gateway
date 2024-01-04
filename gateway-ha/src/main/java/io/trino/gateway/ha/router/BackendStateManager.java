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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof BackendState other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            final Object name = this.getName();
            final Object otherName = other.getName();
            if (!Objects.equals(name, otherName)) {
                return false;
            }
            final Object state = this.getState();
            final Object otherState = other.getState();
            return Objects.equals(state, otherState);
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof BackendState;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final Object name = this.getName();
            result = result * prime + (name == null ? 43 : name.hashCode());
            final Object state = this.getState();
            result = result * prime + (state == null ? 43 : state.hashCode());
            return result;
        }

        public String toString()
        {
            return "BackendStateManager.BackendState(name=" + this.getName() + ", state=" + this.getState() + ")";
        }
    }
}
