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
import com.google.inject.Singleton;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.Authorizer;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.chained.ChainedAuthFilter;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.trino.gateway.ha.config.AuthenticationConfiguration;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestRouterConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import io.trino.gateway.ha.handler.ProxyHandlerStats;
import io.trino.gateway.ha.handler.QueryIdCachingProxyHandler;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.security.ApiAuthenticator;
import io.trino.gateway.ha.security.AuthorizationManager;
import io.trino.gateway.ha.security.FormAuthenticator;
import io.trino.gateway.ha.security.LbAuthenticator;
import io.trino.gateway.ha.security.LbAuthorizer;
import io.trino.gateway.ha.security.LbFilter;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbPrincipal;
import io.trino.gateway.ha.security.LbUnauthorizedHandler;
import io.trino.gateway.ha.security.NoopAuthenticator;
import io.trino.gateway.ha.security.NoopAuthorizer;
import io.trino.gateway.ha.security.NoopFilter;
import io.trino.gateway.proxyserver.ProxyHandler;
import io.trino.gateway.proxyserver.ProxyServer;
import io.trino.gateway.proxyserver.ProxyServerConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HaGatewayProviderModule
        extends AbstractModule
{
    private final LbOAuthManager oauthManager;
    private final LbFormAuthManager formAuthManager;
    private final AuthorizationManager authorizationManager;
    private final BackendStateManager backendStateConnectionManager;
    private final AuthFilter authenticationFilter;
    private final List<String> extraWhitelistPaths;
    private final HaGatewayConfiguration configuration;
    private final Environment environment;

    public HaGatewayProviderModule(HaGatewayConfiguration configuration, Environment environment)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");
        this.environment = requireNonNull(environment, "environment is null");
        Map<String, UserConfiguration> presetUsers = configuration.getPresetUsers();

        oauthManager = getOAuthManager(configuration);
        formAuthManager = getFormAuthManager(configuration);

        authorizationManager = new AuthorizationManager(configuration.getAuthorization(), presetUsers);
        authenticationFilter = getAuthFilter(configuration);
        backendStateConnectionManager = new BackendStateManager();
        extraWhitelistPaths = configuration.getExtraWhitelistPaths();
    }

    private LbOAuthManager getOAuthManager(HaGatewayConfiguration configuration)
    {
        AuthenticationConfiguration authenticationConfiguration = configuration.getAuthentication();
        if (authenticationConfiguration != null && authenticationConfiguration.getOauth() != null) {
            return new LbOAuthManager(authenticationConfiguration.getOauth(), configuration.getPagePermissions());
        }
        return null;
    }

    private LbFormAuthManager getFormAuthManager(HaGatewayConfiguration configuration)
    {
        AuthenticationConfiguration authenticationConfiguration = configuration.getAuthentication();
        if (authenticationConfiguration != null && authenticationConfiguration.getForm() != null) {
            return new LbFormAuthManager(authenticationConfiguration.getForm(),
                    configuration.getPresetUsers(), configuration.getPagePermissions());
        }
        return null;
    }

    private ChainedAuthFilter getAuthenticationFilters(AuthenticationConfiguration config,
            Authorizer<LbPrincipal> authorizer)
    {
        List<AuthFilter> authFilters = new ArrayList<>();
        String defaultType = config.getDefaultType();
        if (oauthManager != null) {
            authFilters.add(new LbFilter.Builder<LbPrincipal>()
                    .setAuthenticator(new LbAuthenticator(oauthManager, authorizationManager))
                    .setAuthorizer(authorizer)
                    .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
                    .setPrefix("Bearer")
                    .buildAuthFilter());
        }

        if (formAuthManager != null) {
            authFilters.add(new LbFilter.Builder<LbPrincipal>()
                    .setAuthenticator(new FormAuthenticator(formAuthManager, authorizationManager))
                    .setAuthorizer(authorizer)
                    .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
                    .setPrefix("Bearer")
                    .buildAuthFilter());

            authFilters.add(new BasicCredentialAuthFilter.Builder<LbPrincipal>()
                    .setAuthenticator(new ApiAuthenticator(formAuthManager, authorizationManager))
                    .setAuthorizer(authorizer)
                    .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
                    .setPrefix("Basic")
                    .buildAuthFilter());
        }

        return new ChainedAuthFilter(authFilters);
    }

    private ProxyHandler getProxyHandler(QueryHistoryManager queryHistoryManager,
                                         RoutingManager routingManager)
    {
        ProxyHandlerStats proxyHandlerStats = ProxyHandlerStats.create(
                environment,
                configuration.getRequestRouter().getName() + ".requests");

        // By default, use routing group header to route
        RoutingGroupSelector routingGroupSelector = RoutingGroupSelector.byRoutingGroupHeader();
        // Use rules engine if enabled
        RoutingRulesConfiguration routingRulesConfig = configuration.getRoutingRules();
        if (routingRulesConfig.isRulesEngineEnabled()) {
            String rulesConfigPath = routingRulesConfig.getRulesConfigPath();
            routingGroupSelector = RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);
        }

        return new QueryIdCachingProxyHandler(
                queryHistoryManager,
                routingManager,
                routingGroupSelector,
                getApplicationPort(),
                proxyHandlerStats,
                extraWhitelistPaths);
    }

    private int getApplicationPort()
    {
        Stream<ConnectorFactory> connectors =
                configuration.getServerFactory() instanceof DefaultServerFactory
                        ? ((DefaultServerFactory) configuration.getServerFactory())
                        .getApplicationConnectors().stream()
                        : Stream.of((SimpleServerFactory) configuration.getServerFactory())
                        .map(SimpleServerFactory::getConnector);

        return connectors
                .filter(connector -> connector.getClass().isAssignableFrom(HttpConnectorFactory.class))
                .map(connector -> (HttpConnectorFactory) connector)
                .mapToInt(HttpConnectorFactory::getPort)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private AuthFilter getAuthFilter(HaGatewayConfiguration configuration)
    {
        AuthorizationConfiguration authorizationConfig = configuration.getAuthorization();
        Authorizer<LbPrincipal> authorizer = (authorizationConfig != null)
                ? new LbAuthorizer(authorizationConfig) : new NoopAuthorizer();

        AuthenticationConfiguration authenticationConfig = configuration.getAuthentication();

        if (authenticationConfig != null) {
            return getAuthenticationFilters(authenticationConfig, authorizer);
        }

        return new NoopFilter.Builder<LbPrincipal>()
                .setAuthenticator(new NoopAuthenticator())
                .setAuthorizer(authorizer)
                .buildAuthFilter();
    }

    @Provides
    @Singleton
    public ProxyServer provideGateway(QueryHistoryManager queryHistoryManager,
                                        RoutingManager routingManager)
    {
        ProxyServer gateway = null;
        if (configuration.getRequestRouter() != null) {
            // Setting up request router
            RequestRouterConfiguration routerConfiguration = configuration.getRequestRouter();

            ProxyServerConfiguration routerProxyConfig = new ProxyServerConfiguration();
            routerProxyConfig.setLocalPort(routerConfiguration.getPort());
            routerProxyConfig.setName(routerConfiguration.getName());
            routerProxyConfig.setProxyTo("");
            routerProxyConfig.setSsl(routerConfiguration.isSsl());
            routerProxyConfig.setKeystorePath(routerConfiguration.getKeystorePath());
            routerProxyConfig.setKeystorePass(routerConfiguration.getKeystorePass());
            routerProxyConfig.setForwardKeystore(routerConfiguration.isForwardKeystore());
            routerProxyConfig.setPreserveHost("false");
            routerProxyConfig.setOutputBufferSize(routerConfiguration.getOutputBufferSize());
            routerProxyConfig.setRequestHeaderSize(routerConfiguration.getRequestHeaderSize());
            routerProxyConfig.setResponseHeaderSize(routerConfiguration.getResponseHeaderSize());
            routerProxyConfig.setRequestBufferSize(routerConfiguration.getRequestBufferSize());
            routerProxyConfig.setResponseHeaderSize(routerConfiguration.getResponseBufferSize());
            ProxyHandler proxyHandler = getProxyHandler(queryHistoryManager, routingManager);
            gateway = new ProxyServer(routerProxyConfig, proxyHandler);
        }
        return gateway;
    }

    @Provides
    @Singleton
    public LbOAuthManager getAuthenticationManager()
    {
        return this.oauthManager;
    }

    @Provides
    @Singleton
    public LbFormAuthManager getFormAuthentication()
    {
        return this.formAuthManager;
    }

    @Provides
    @Singleton
    public AuthorizationManager getAuthorizationManager()
    {
        return this.authorizationManager;
    }

    @Provides
    @Singleton
    public AuthFilter getAuthenticationFilter()
    {
        return authenticationFilter;
    }

    @Provides
    @Singleton
    public BackendStateManager getBackendStateConnectionManager()
    {
        return this.backendStateConnectionManager;
    }
}
