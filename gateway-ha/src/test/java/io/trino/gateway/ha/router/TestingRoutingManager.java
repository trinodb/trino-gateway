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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestingRoutingManager
        implements RoutingManager
{
    private String adhocBackend;
    private Map<String, String> queryIdBackendMap = new HashMap<>();

    public TestingRoutingManager(String adhocBackend)
    {
        this.adhocBackend = adhocBackend;
    }

    public void setBackendForQueryId(String queryId, String backend)
    {
        queryIdBackendMap.put(queryId, backend);
    }

    public String provideAdhocBackend(String user)
    {
        return adhocBackend;
    }

    @Override
    public String provideBackendForRoutingGroup(String routingGroup, String user)
    {
        return adhocBackend;
    }

    @Override
    public String findBackendForQueryId(String queryId)
    {
        return queryIdBackendMap.get(queryId);
    }

    @Override
    public void updateBackEndHealth(String backendId, Boolean value)
    {
    }

    @Override
    public void updateBackEndStats(List<ClusterStats> stats)
    {
    }

    @Override
    public String findBackendForUnknownQueryId(String queryId)
    {
        return adhocBackend;
    }
}
