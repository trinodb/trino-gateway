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
package io.trino.gateway.ha.config;

import io.trino.gateway.baseapp.AppConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HaGatewayConfiguration
        extends AppConfiguration
{
    private RequestRouterConfiguration requestRouter;
    private NotifierConfiguration notifier;
    private DataStoreConfiguration dataStore;
    private MonitorConfiguration monitor = new MonitorConfiguration();
    private RoutingRulesConfiguration routingRules = new RoutingRulesConfiguration();
    private AuthenticationConfiguration authentication;
    private AuthorizationConfiguration authorization;
    private Map<String, UserConfiguration> presetUsers = new HashMap();
    private BackendStateConfiguration backendState;
    private ClusterStatsConfiguration clusterStatsConfiguration;
    private List<String> extraWhitelistPaths = new ArrayList<>();

    public HaGatewayConfiguration() {}

    public RequestRouterConfiguration getRequestRouter()
    {
        return this.requestRouter;
    }

    public void setRequestRouter(RequestRouterConfiguration requestRouter)
    {
        this.requestRouter = requestRouter;
    }

    public NotifierConfiguration getNotifier()
    {
        return this.notifier;
    }

    public void setNotifier(NotifierConfiguration notifier)
    {
        this.notifier = notifier;
    }

    public DataStoreConfiguration getDataStore()
    {
        return this.dataStore;
    }

    public void setDataStore(DataStoreConfiguration dataStore)
    {
        this.dataStore = dataStore;
    }

    public MonitorConfiguration getMonitor()
    {
        return this.monitor;
    }

    public void setMonitor(MonitorConfiguration monitor)
    {
        this.monitor = monitor;
    }

    public RoutingRulesConfiguration getRoutingRules()
    {
        return this.routingRules;
    }

    public void setRoutingRules(RoutingRulesConfiguration routingRules)
    {
        this.routingRules = routingRules;
    }

    public AuthenticationConfiguration getAuthentication()
    {
        return this.authentication;
    }

    public void setAuthentication(AuthenticationConfiguration authentication)
    {
        this.authentication = authentication;
    }

    public AuthorizationConfiguration getAuthorization()
    {
        return this.authorization;
    }

    public void setAuthorization(AuthorizationConfiguration authorization)
    {
        this.authorization = authorization;
    }

    public Map<String, UserConfiguration> getPresetUsers()
    {
        return this.presetUsers;
    }

    public void setPresetUsers(Map<String, UserConfiguration> presetUsers)
    {
        this.presetUsers = presetUsers;
    }

    public BackendStateConfiguration getBackendState()
    {
        return this.backendState;
    }

    public void setBackendState(BackendStateConfiguration backendState)
    {
        this.backendState = backendState;
    }

    public ClusterStatsConfiguration getClusterStatsConfiguration()
    {
        return this.clusterStatsConfiguration;
    }

    public void setClusterStatsConfiguration(ClusterStatsConfiguration clusterStatsConfiguration)
    {
        this.clusterStatsConfiguration = clusterStatsConfiguration;
    }

    public List<String> getExtraWhitelistPaths()
    {
        return this.extraWhitelistPaths;
    }

    public void setExtraWhitelistPaths(List<String> extraWhitelistPaths)
    {
        this.extraWhitelistPaths = extraWhitelistPaths;
    }
}
