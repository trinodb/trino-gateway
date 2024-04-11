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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HaGatewayConfiguration
{
    private RequestRouterConfiguration requestRouter;
    private NotifierConfiguration notifier;
    private DataStoreConfiguration dataStore;
    private MonitorConfiguration monitor = new MonitorConfiguration();
    private RoutingRulesConfiguration routingRules = new RoutingRulesConfiguration();
    private AuthenticationConfiguration authentication;
    private AuthorizationConfiguration authorization;
    private Map<String, UserConfiguration> presetUsers = new HashMap<>();
    private Map<String, String> pagePermissions = new HashMap<>();
    private BackendStateConfiguration backendState;
    private ClusterStatsConfiguration clusterStatsConfiguration;
    private List<String> extraWhitelistPaths = new ArrayList<>();
    private OAuth2GatewayCookieConfiguration oauth2GatewayCookieConfiguration = new OAuth2GatewayCookieConfiguration();
    private GatewayCookieConfiguration gatewayCookieConfiguration = new GatewayCookieConfiguration();

    // List of Modules with FQCN (Fully Qualified Class Name)
    private List<String> modules;

    // List of ManagedApps with FQCN (Fully Qualified Class Name)
    private List<String> managedApps;

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

    public Map<String, String> getPagePermissions()
    {
        return this.pagePermissions;
    }

    public void setPagePermissions(Map<String, String> pagePermissions)
    {
        this.pagePermissions = pagePermissions;
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

    public OAuth2GatewayCookieConfiguration getOauth2GatewayCookieConfiguration()
    {
        return oauth2GatewayCookieConfiguration;
    }

    public void setOauth2GatewayCookieConfiguration(OAuth2GatewayCookieConfiguration oauth2GatewayCookieConfiguration)
    {
        this.oauth2GatewayCookieConfiguration = oauth2GatewayCookieConfiguration;
    }

    public GatewayCookieConfiguration getGatewayCookieConfiguration()
    {
        return gatewayCookieConfiguration;
    }

    public void setGatewayCookieConfiguration(GatewayCookieConfiguration gatewayCookieConfiguration)
    {
        this.gatewayCookieConfiguration = gatewayCookieConfiguration;
    }

    public List<String> getModules()
    {
        return this.modules;
    }

    public void setModules(List<String> modules)
    {
        this.modules = modules;
    }

    public List<String> getManagedApps()
    {
        return this.managedApps;
    }

    public void setManagedApps(List<String> managedApps)
    {
        this.managedApps = managedApps;
    }
}
