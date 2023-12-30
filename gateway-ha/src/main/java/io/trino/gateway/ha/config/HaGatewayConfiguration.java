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
import java.util.Objects;

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

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HaGatewayConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Object requestRouter = this.getRequestRouter();
        final Object otherRequestRouter = other.getRequestRouter();
        if (!Objects.equals(requestRouter, otherRequestRouter)) {
            return false;
        }
        final Object notifier = this.getNotifier();
        final Object otherNotifier = other.getNotifier();
        if (!Objects.equals(notifier, otherNotifier)) {
            return false;
        }
        final Object dataStore = this.getDataStore();
        final Object otherDataStore = other.getDataStore();
        if (!Objects.equals(dataStore, otherDataStore)) {
            return false;
        }
        final Object monitor = this.getMonitor();
        final Object otherMonitor = other.getMonitor();
        if (!Objects.equals(monitor, otherMonitor)) {
            return false;
        }
        final Object routingRules = this.getRoutingRules();
        final Object otherRoutingRules = other.getRoutingRules();
        if (!Objects.equals(routingRules, otherRoutingRules)) {
            return false;
        }
        final Object authentication = this.getAuthentication();
        final Object otherAuthentication = other.getAuthentication();
        if (!Objects.equals(authentication, otherAuthentication)) {
            return false;
        }
        final Object authorization = this.getAuthorization();
        final Object otherAuthorization = other.getAuthorization();
        if (!Objects.equals(authorization, otherAuthorization)) {
            return false;
        }
        final Object presetUsers = this.getPresetUsers();
        final Object otherPresetUsers = other.getPresetUsers();
        if (!Objects.equals(presetUsers, otherPresetUsers)) {
            return false;
        }
        final Object backendState = this.getBackendState();
        final Object otherBackendState = other.getBackendState();
        if (!Objects.equals(backendState, otherBackendState)) {
            return false;
        }
        final Object clusterStatsConfiguration = this.getClusterStatsConfiguration();
        final Object otherClusterStatsConfiguration = other.getClusterStatsConfiguration();
        if (!Objects.equals(clusterStatsConfiguration, otherClusterStatsConfiguration)) {
            return false;
        }
        final Object extraWhitelistPaths = this.getExtraWhitelistPaths();
        final Object otherExtraWhitelistPaths = other.getExtraWhitelistPaths();
        return Objects.equals(extraWhitelistPaths, otherExtraWhitelistPaths);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof HaGatewayConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = super.hashCode();
        final Object requestRouter = this.getRequestRouter();
        result = result * prime + (requestRouter == null ? 43 : requestRouter.hashCode());
        final Object notifier = this.getNotifier();
        result = result * prime + (notifier == null ? 43 : notifier.hashCode());
        final Object dataStore = this.getDataStore();
        result = result * prime + (dataStore == null ? 43 : dataStore.hashCode());
        final Object monitor = this.getMonitor();
        result = result * prime + (monitor == null ? 43 : monitor.hashCode());
        final Object routingRules = this.getRoutingRules();
        result = result * prime + (routingRules == null ? 43 : routingRules.hashCode());
        final Object authentication = this.getAuthentication();
        result = result * prime + (authentication == null ? 43 : authentication.hashCode());
        final Object authorization = this.getAuthorization();
        result = result * prime + (authorization == null ? 43 : authorization.hashCode());
        final Object presetUsers = this.getPresetUsers();
        result = result * prime + (presetUsers == null ? 43 : presetUsers.hashCode());
        final Object backendState = this.getBackendState();
        result = result * prime + (backendState == null ? 43 : backendState.hashCode());
        final Object clusterStatsConfiguration = this.getClusterStatsConfiguration();
        result = result * prime + (clusterStatsConfiguration == null ? 43 : clusterStatsConfiguration.hashCode());
        final Object extraWhitelistPaths = this.getExtraWhitelistPaths();
        result = result * prime + (extraWhitelistPaths == null ? 43 : extraWhitelistPaths.hashCode());
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
