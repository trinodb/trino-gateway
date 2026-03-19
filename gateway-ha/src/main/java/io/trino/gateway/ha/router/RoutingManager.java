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
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.util.List;

public interface RoutingManager
{
    /**
     * Updates the health status of a backend cluster.
     *
     * @param backendId the unique identifier of the backend cluster
     * @param value the health status of the backend (UP, DOWN, etc.)
     */
    void updateBackEndHealth(String backendId, TrinoStatus value);

    /**
     * Updates the statistics for all backend clusters.
     *
     * @param stats a list of ClusterStats objects representing the current state of each backend cluster
     */
    void updateClusterStats(List<ClusterStats> stats);

    /**
     * Associates a backend cluster with a specific query ID for sticky routing.
     *
     * @param queryId the unique identifier of the query
     * @param backend the backend cluster to associate with the query
     */
    void setBackendForQueryId(String queryId, String backend);

    /**
     * Finds the backend cluster associated with a given query ID.
     *
     * @param queryId the unique identifier of the query
     * @return the backend cluster ID, or null if not found
     */
    String findBackendForQueryId(String queryId);

    /**
     * Provides the backend configuration for a given routing group and user.
     *
     * @param routingGroup the routing group to use for backend selection
     * @param user the user requesting the backend
     * @return the backend configuration for the selected cluster
     */
    ProxyBackendConfiguration provideBackendConfiguration(String routingGroup, String user);
}
