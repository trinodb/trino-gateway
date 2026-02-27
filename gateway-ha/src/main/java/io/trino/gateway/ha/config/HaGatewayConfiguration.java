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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;

public class HaGatewayConfiguration
{
    private Map<String, String> serverConfig = new HashMap<>();
    private DataStoreConfiguration dataStore;
    private MonitorConfiguration monitor = new MonitorConfiguration();
    private RoutingConfiguration routing = new RoutingConfiguration();
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
    private List<String> statementPaths = ImmutableList.of(V1_STATEMENT_PATH);
    private boolean includeClusterHostInResponse;
    private ProxyResponseConfiguration proxyResponseConfiguration = new ProxyResponseConfiguration();
    private RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
    private UIConfiguration uiConfiguration = new UIConfiguration();
    private DatabaseCacheConfiguration databaseCache = new DatabaseCacheConfiguration();

    private DistributedCacheConfiguration distributedCacheConfiguration = new DistributedCacheConfiguration();

    // List of Modules with FQCN (Fully Qualified Class Name)
    private List<String> modules;

    // List of ManagedApps with FQCN (Fully Qualified Class Name)
    private List<String> managedApps;

    public HaGatewayConfiguration() {}

    public Map<String, String> getServerConfig()
    {
        return this.serverConfig;
    }

    public void setServerConfig(Map<String, String> serverConfig)
    {
        this.serverConfig = serverConfig;
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

    public RoutingConfiguration getRouting()
    {
        return routing;
    }

    public void setRouting(RoutingConfiguration routing)
    {
        this.routing = routing;
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

    public RequestAnalyzerConfig getRequestAnalyzerConfig()
    {
        return requestAnalyzerConfig;
    }

    public void setRequestAnalyzerConfig(RequestAnalyzerConfig requestAnalyzerConfig)
    {
        this.requestAnalyzerConfig = requestAnalyzerConfig;
    }

    public UIConfiguration getUiConfiguration()
    {
        return uiConfiguration;
    }

    public void setUiConfiguration(UIConfiguration uiConfiguration)
    {
        this.uiConfiguration = uiConfiguration;
    }

    public DistributedCacheConfiguration getDistributedCacheConfiguration()
    {
        return distributedCacheConfiguration;
    }

    public void setDistributedCacheConfiguration(DistributedCacheConfiguration distributedCacheConfiguration)
    {
        this.distributedCacheConfiguration = distributedCacheConfiguration;
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

    public List<String> getStatementPaths()
    {
        return statementPaths;
    }

    public void setAdditionalStatementPaths(List<String> statementPaths)
    {
        // remove trailing slashes to ensure predictable behavior when splitting on "/"
        this.statementPaths = Streams.concat(ImmutableList.of(V1_STATEMENT_PATH).stream(),
                statementPaths.stream().peek(s -> validateStatementPath(s, statementPaths)).map(s -> s.replaceAll("/+$", ""))).toList();
    }

    public boolean isIncludeClusterHostInResponse()
    {
        return includeClusterHostInResponse;
    }

    public void setIncludeClusterHostInResponse(boolean includeClusterHostInResponse)
    {
        this.includeClusterHostInResponse = includeClusterHostInResponse;
    }

    public ProxyResponseConfiguration getProxyResponseConfiguration()
    {
        return this.proxyResponseConfiguration;
    }

    public void setProxyResponseConfiguration(ProxyResponseConfiguration proxyResponseConfiguration)
    {
        this.proxyResponseConfiguration = proxyResponseConfiguration;
    }

    public DatabaseCacheConfiguration getDatabaseCache()
    {
        return databaseCache;
    }

    public void setDatabaseCache(DatabaseCacheConfiguration databaseCache)
    {
        this.databaseCache = databaseCache;
    }

    private void validateStatementPath(String statementPath, List<String> statementPaths)
    {
        if (statementPath.startsWith(V1_STATEMENT_PATH) ||
                statementPaths.stream().filter(s -> !s.equals(statementPath)).anyMatch(s -> s.startsWith(statementPath))) {
            throw new HaGatewayConfigurationException("Statement paths cannot be prefixes of other statement paths");
        }
        if (!statementPath.startsWith("/")) {
            throw new HaGatewayConfigurationException("Statement paths must be absolute");
        }
    }

    public static class HaGatewayConfigurationException
            extends RuntimeException
    {
        public HaGatewayConfigurationException(String message)
        {
            super(message);
        }
    }
}
