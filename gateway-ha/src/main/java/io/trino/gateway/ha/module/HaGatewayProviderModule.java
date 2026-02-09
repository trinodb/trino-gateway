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
package io.trino.gateway.ha.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.cache.DistributedCache;
import io.trino.gateway.ha.cache.QueryCacheManager;
import io.trino.gateway.ha.cache.ValkeyDistributedCache;
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsHttpMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsInfoApiMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsJdbcMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsJmxMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsMetricsMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterStatsObserver;
import io.trino.gateway.ha.clustermonitor.ForMonitor;
import io.trino.gateway.ha.clustermonitor.HealthCheckObserver;
import io.trino.gateway.ha.clustermonitor.NoopClusterStatsMonitor;
import io.trino.gateway.ha.clustermonitor.TrinoClusterStatsObserver;
import io.trino.gateway.ha.config.AuthenticationConfiguration;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.ClusterStatsConfiguration;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.OAuth2GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import io.trino.gateway.ha.config.ValkeyConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.RecordAndAnnotatedConstructorMapper;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.ForRouter;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaResourceGroupsManager;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.security.AuthorizationManager;
import io.trino.gateway.ha.security.LbAuthorizer;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.NoopAuthorizer;
import io.trino.gateway.ha.security.NoopFilter;
import io.trino.gateway.ha.security.ResourceSecurityDynamicFeature;
import io.trino.gateway.ha.security.util.Authorizer;
import io.trino.gateway.ha.security.util.ChainedAuthFilter;
import jakarta.ws.rs.container.ContainerRequestFilter;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.trino.gateway.ha.config.ClusterStatsMonitorType.INFO_API;
import static io.trino.gateway.ha.config.ClusterStatsMonitorType.NOOP;
import static java.util.Objects.requireNonNull;

public class HaGatewayProviderModule
        extends AbstractModule
{
    private final HaGatewayConfiguration configuration;

    @Override
    protected void configure()
    {
        jaxrsBinder(binder()).bind(ResourceSecurityDynamicFeature.class);
        binder().bind(ResourceGroupsManager.class).to(HaResourceGroupsManager.class).in(Scopes.SINGLETON);
        binder().bind(GatewayBackendManager.class).to(HaGatewayManager.class).in(Scopes.SINGLETON);
        binder().bind(QueryHistoryManager.class).to(HaQueryHistoryManager.class).in(Scopes.SINGLETON);
        binder().bind(BackendStateManager.class).in(Scopes.SINGLETON);
        binder().bind(JdbcConnectionManager.class).in(Scopes.SINGLETON);
        binder().bind(AuthorizationManager.class).in(Scopes.SINGLETON);
        binder().bind(PathFilter.class).in(Scopes.SINGLETON);

        Multibinder<TrinoClusterStatsObserver> observers = newSetBinder(binder(), TrinoClusterStatsObserver.class);
        observers.addBinding().to(HealthCheckObserver.class).in(Scopes.SINGLETON);
        observers.addBinding().to(ClusterStatsObserver.class).in(Scopes.SINGLETON);

        if (configuration.getAuthentication() != null) {
            binder().bind(ContainerRequestFilter.class).to(ChainedAuthFilter.class).in(Scopes.SINGLETON);
        }
        else {
            binder().bind(ContainerRequestFilter.class).to(NoopFilter.class).in(Scopes.SINGLETON);
        }
        binder().bind(ActiveClusterMonitor.class).in(Scopes.SINGLETON);
    }

    public HaGatewayProviderModule(HaGatewayConfiguration configuration)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");

        GatewayCookieConfigurationPropertiesProvider gatewayCookieConfigurationPropertiesProvider = GatewayCookieConfigurationPropertiesProvider.getInstance();
        gatewayCookieConfigurationPropertiesProvider.initialize(configuration.getGatewayCookieConfiguration());

        OAuth2GatewayCookieConfigurationPropertiesProvider oAuth2GatewayCookieConfigurationPropertiesProvider = OAuth2GatewayCookieConfigurationPropertiesProvider.getInstance();
        oAuth2GatewayCookieConfigurationPropertiesProvider.initialize(configuration.getOauth2GatewayCookieConfiguration());
    }

    @Singleton
    @Provides
    public static Jdbi createJdbi(DataStoreConfiguration config)
    {
        Jdbi jdbi = Jdbi.create(config.getJdbcUrl(), config.getUser(), config.getPassword());
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.registerRowMapper(new RecordAndAnnotatedConstructorMapper());
        return jdbi;
    }

    @Provides
    @Singleton
    public static Authorizer getAuthorizer(HaGatewayConfiguration configuration)
    {
        AuthorizationConfiguration authorizationConfig = configuration.getAuthorization();
        return authorizationConfig != null ? new LbAuthorizer(authorizationConfig) : new NoopAuthorizer();
    }

    @Provides
    @Singleton
    public static LbOAuthManager getAuthenticationManager(HaGatewayConfiguration config)
    {
        AuthenticationConfiguration authenticationConfiguration = config.getAuthentication();
        if (authenticationConfiguration != null && authenticationConfiguration.getOauth() != null) {
            return new LbOAuthManager(authenticationConfiguration.getOauth(), config.getPagePermissions());
        }
        return null;
    }

    @Provides
    @Singleton
    public static LbFormAuthManager getFormAuthentication(HaGatewayConfiguration config)
    {
        AuthenticationConfiguration authenticationConfiguration = config.getAuthentication();
        if (authenticationConfiguration != null && authenticationConfiguration.getForm() != null) {
            return new LbFormAuthManager(authenticationConfiguration.getForm(), config.getPresetUsers(), config.getPagePermissions());
        }
        return null;
    }

    @Provides
    @Singleton
    public static RoutingGroupSelector getRoutingGroupSelector(@ForRouter HttpClient httpClient, HaGatewayConfiguration configuration)
    {
        RoutingRulesConfiguration routingRulesConfig = configuration.getRoutingRules();
        if (routingRulesConfig.isRulesEngineEnabled()) {
            try {
                return switch (routingRulesConfig.getRulesType()) {
                    case FILE -> RoutingGroupSelector.byRoutingRulesEngine(
                            routingRulesConfig.getRulesConfigPath(),
                            routingRulesConfig.getRulesRefreshPeriod(),
                            configuration.getRequestAnalyzerConfig());
                    case EXTERNAL -> {
                        RulesExternalConfiguration rulesExternalConfiguration = routingRulesConfig.getRulesExternalConfiguration();
                        yield RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, configuration.getRequestAnalyzerConfig());
                    }
                };
            }
            catch (Exception e) {
                return RoutingGroupSelector.byRoutingGroupHeader();
            }
        }
        return RoutingGroupSelector.byRoutingGroupHeader();
    }

    @Provides
    @Singleton
    public static ClusterStatsMonitor getClusterStatsMonitor(@ForMonitor HttpClient httpClient, HaGatewayConfiguration configuration)
    {
        ClusterStatsConfiguration clusterStatsConfig = configuration.getClusterStatsConfiguration();
        if (clusterStatsConfig == null) {
            return new ClusterStatsInfoApiMonitor(httpClient, configuration.getMonitor());
        }
        if (!(clusterStatsConfig.getMonitorType() == INFO_API || clusterStatsConfig.getMonitorType() == NOOP)
                && configuration.getBackendState() == null) {
            throw new IllegalArgumentException("BackendStateConfiguration is required for monitor type: " + clusterStatsConfig.getMonitorType());
        }
        return switch (clusterStatsConfig.getMonitorType()) {
            case INFO_API -> new ClusterStatsInfoApiMonitor(httpClient, configuration.getMonitor());
            case UI_API -> new ClusterStatsHttpMonitor(configuration.getBackendState());
            case JDBC -> new ClusterStatsJdbcMonitor(configuration.getBackendState(), configuration.getMonitor());
            case JMX -> new ClusterStatsJmxMonitor(httpClient, configuration.getBackendState());
            case METRICS -> new ClusterStatsMetricsMonitor(httpClient, configuration.getBackendState(), configuration.getMonitor());
            case NOOP -> new NoopClusterStatsMonitor();
        };
    }

    @Provides
    @Singleton
    public static ValkeyConfiguration getValkeyConfiguration(HaGatewayConfiguration configuration)
    {
        return configuration.getValkeyConfiguration();
    }

    @Provides
    @Singleton
    public static DistributedCache getDistributedCache(ValkeyConfiguration valkeyConfig)
    {
        return new ValkeyDistributedCache(valkeyConfig);
    }

    @Provides
    @Singleton
    public static QueryCacheManager getQueryCacheManager(DistributedCache distributedCache, QueryHistoryManager queryHistoryManager)
    {
        // Create a loader that fetches complete metadata from database
        QueryCacheManager.QueryCacheLoader loader = queryId -> {
            String backend = queryHistoryManager.getBackendForQueryId(queryId);
            String routingGroup = queryHistoryManager.getRoutingGroupForQueryId(queryId);
            String externalUrl = queryHistoryManager.getExternalUrlForQueryId(queryId);

            // Return null if nothing found, otherwise return metadata (even if partially populated)
            if (backend == null && routingGroup == null && externalUrl == null) {
                return null;
            }
            return new io.trino.gateway.ha.cache.QueryMetadata(backend, routingGroup, externalUrl);
        };
        return new QueryCacheManager(distributedCache, loader);
    }
}
