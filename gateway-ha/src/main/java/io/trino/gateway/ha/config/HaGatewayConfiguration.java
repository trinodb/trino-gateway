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
    {return this.requestRouter;}

    public void setRequestRouter(RequestRouterConfiguration requestRouter)
    {this.requestRouter = requestRouter;}

    public NotifierConfiguration getNotifier()
    {return this.notifier;}

    public void setNotifier(NotifierConfiguration notifier)
    {this.notifier = notifier;}

    public DataStoreConfiguration getDataStore()
    {return this.dataStore;}

    public void setDataStore(DataStoreConfiguration dataStore)
    {this.dataStore = dataStore;}

    public MonitorConfiguration getMonitor()
    {return this.monitor;}

    public void setMonitor(MonitorConfiguration monitor)
    {this.monitor = monitor;}

    public RoutingRulesConfiguration getRoutingRules()
    {return this.routingRules;}

    public void setRoutingRules(RoutingRulesConfiguration routingRules)
    {this.routingRules = routingRules;}

    public AuthenticationConfiguration getAuthentication()
    {return this.authentication;}

    public void setAuthentication(AuthenticationConfiguration authentication)
    {this.authentication = authentication;}

    public AuthorizationConfiguration getAuthorization()
    {return this.authorization;}

    public void setAuthorization(AuthorizationConfiguration authorization)
    {this.authorization = authorization;}

    public Map<String, UserConfiguration> getPresetUsers()
    {return this.presetUsers;}

    public void setPresetUsers(Map<String, UserConfiguration> presetUsers)
    {this.presetUsers = presetUsers;}

    public BackendStateConfiguration getBackendState()
    {return this.backendState;}

    public void setBackendState(BackendStateConfiguration backendState)
    {this.backendState = backendState;}

    public ClusterStatsConfiguration getClusterStatsConfiguration()
    {return this.clusterStatsConfiguration;}

    public void setClusterStatsConfiguration(ClusterStatsConfiguration clusterStatsConfiguration)
    {this.clusterStatsConfiguration = clusterStatsConfiguration;}

    public List<String> getExtraWhitelistPaths()
    {return this.extraWhitelistPaths;}

    public void setExtraWhitelistPaths(List<String> extraWhitelistPaths)
    {this.extraWhitelistPaths = extraWhitelistPaths;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HaGatewayConfiguration)) {
            return false;
        }
        final HaGatewayConfiguration other = (HaGatewayConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Object this$requestRouter = this.getRequestRouter();
        final Object other$requestRouter = other.getRequestRouter();
        if (this$requestRouter == null ? other$requestRouter != null : !this$requestRouter.equals(other$requestRouter)) {
            return false;
        }
        final Object this$notifier = this.getNotifier();
        final Object other$notifier = other.getNotifier();
        if (this$notifier == null ? other$notifier != null : !this$notifier.equals(other$notifier)) {
            return false;
        }
        final Object this$dataStore = this.getDataStore();
        final Object other$dataStore = other.getDataStore();
        if (this$dataStore == null ? other$dataStore != null : !this$dataStore.equals(other$dataStore)) {
            return false;
        }
        final Object this$monitor = this.getMonitor();
        final Object other$monitor = other.getMonitor();
        if (this$monitor == null ? other$monitor != null : !this$monitor.equals(other$monitor)) {
            return false;
        }
        final Object this$routingRules = this.getRoutingRules();
        final Object other$routingRules = other.getRoutingRules();
        if (this$routingRules == null ? other$routingRules != null : !this$routingRules.equals(other$routingRules)) {
            return false;
        }
        final Object this$authentication = this.getAuthentication();
        final Object other$authentication = other.getAuthentication();
        if (this$authentication == null ? other$authentication != null : !this$authentication.equals(other$authentication)) {
            return false;
        }
        final Object this$authorization = this.getAuthorization();
        final Object other$authorization = other.getAuthorization();
        if (this$authorization == null ? other$authorization != null : !this$authorization.equals(other$authorization)) {
            return false;
        }
        final Object this$presetUsers = this.getPresetUsers();
        final Object other$presetUsers = other.getPresetUsers();
        if (this$presetUsers == null ? other$presetUsers != null : !this$presetUsers.equals(other$presetUsers)) {
            return false;
        }
        final Object this$backendState = this.getBackendState();
        final Object other$backendState = other.getBackendState();
        if (this$backendState == null ? other$backendState != null : !this$backendState.equals(other$backendState)) {
            return false;
        }
        final Object this$clusterStatsConfiguration = this.getClusterStatsConfiguration();
        final Object other$clusterStatsConfiguration = other.getClusterStatsConfiguration();
        if (this$clusterStatsConfiguration == null ? other$clusterStatsConfiguration != null : !this$clusterStatsConfiguration.equals(other$clusterStatsConfiguration)) {
            return false;
        }
        final Object this$extraWhitelistPaths = this.getExtraWhitelistPaths();
        final Object other$extraWhitelistPaths = other.getExtraWhitelistPaths();
        if (this$extraWhitelistPaths == null ? other$extraWhitelistPaths != null : !this$extraWhitelistPaths.equals(other$extraWhitelistPaths)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof HaGatewayConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = super.hashCode();
        final Object $requestRouter = this.getRequestRouter();
        result = result * PRIME + ($requestRouter == null ? 43 : $requestRouter.hashCode());
        final Object $notifier = this.getNotifier();
        result = result * PRIME + ($notifier == null ? 43 : $notifier.hashCode());
        final Object $dataStore = this.getDataStore();
        result = result * PRIME + ($dataStore == null ? 43 : $dataStore.hashCode());
        final Object $monitor = this.getMonitor();
        result = result * PRIME + ($monitor == null ? 43 : $monitor.hashCode());
        final Object $routingRules = this.getRoutingRules();
        result = result * PRIME + ($routingRules == null ? 43 : $routingRules.hashCode());
        final Object $authentication = this.getAuthentication();
        result = result * PRIME + ($authentication == null ? 43 : $authentication.hashCode());
        final Object $authorization = this.getAuthorization();
        result = result * PRIME + ($authorization == null ? 43 : $authorization.hashCode());
        final Object $presetUsers = this.getPresetUsers();
        result = result * PRIME + ($presetUsers == null ? 43 : $presetUsers.hashCode());
        final Object $backendState = this.getBackendState();
        result = result * PRIME + ($backendState == null ? 43 : $backendState.hashCode());
        final Object $clusterStatsConfiguration = this.getClusterStatsConfiguration();
        result = result * PRIME + ($clusterStatsConfiguration == null ? 43 : $clusterStatsConfiguration.hashCode());
        final Object $extraWhitelistPaths = this.getExtraWhitelistPaths();
        result = result * PRIME + ($extraWhitelistPaths == null ? 43 : $extraWhitelistPaths.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "HaGatewayConfiguration{" +
                "requestRouter=" + requestRouter +
                ", notifier=" + notifier +
                ", dataStore=" + dataStore +
                ", monitor=" + monitor +
                ", routingRules=" + routingRules +
                ", authentication=" + authentication +
                ", authorization=" + authorization +
                ", presetUsers=" + presetUsers +
                ", backendState=" + backendState +
                ", clusterStatsConfiguration=" + clusterStatsConfiguration +
                ", extraWhitelistPaths=" + extraWhitelistPaths +
                '}';
    }
}
