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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.trino.gateway.ha.config.AuthenticationConfiguration;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.OAuth2GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import io.trino.gateway.ha.handler.RoutingTargetHandler;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.security.ApiAuthenticator;
import io.trino.gateway.ha.security.AuthorizationManager;
import io.trino.gateway.ha.security.BasicAuthFilter;
import io.trino.gateway.ha.security.FormAuthenticator;
import io.trino.gateway.ha.security.LbAuthenticator;
import io.trino.gateway.ha.security.LbAuthorizer;
import io.trino.gateway.ha.security.LbFilter;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbUnauthorizedHandler;
import io.trino.gateway.ha.security.NoopAuthorizer;
import io.trino.gateway.ha.security.NoopFilter;
import io.trino.gateway.ha.security.ResourceSecurityDynamicFeature;
import io.trino.gateway.ha.security.util.Authorizer;
import io.trino.gateway.ha.security.util.ChainedAuthFilter;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.util.Map;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.util.Objects.requireNonNull;

public class HaGatewayProviderModule
        extends AbstractModule
{
    private final LbOAuthManager oauthManager;
    private final LbFormAuthManager formAuthManager;
    private final AuthorizationManager authorizationManager;
    private final BackendStateManager backendStateConnectionManager;
    private final ResourceSecurityDynamicFeature resourceSecurityDynamicFeature;
    private final HaGatewayConfiguration configuration;

    @Override
    protected void configure()
    {
        jaxrsBinder(binder()).bindInstance(resourceSecurityDynamicFeature);
    }

    public HaGatewayProviderModule(HaGatewayConfiguration configuration)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");
        Map<String, UserConfiguration> presetUsers = configuration.getPresetUsers();

        oauthManager = getOAuthManager(configuration);
        formAuthManager = getFormAuthManager(configuration);

        authorizationManager = new AuthorizationManager(configuration.getAuthorization(), presetUsers);
        resourceSecurityDynamicFeature = getAuthFilter(configuration);
        backendStateConnectionManager = new BackendStateManager();

        GatewayCookieConfigurationPropertiesProvider gatewayCookieConfigurationPropertiesProvider = GatewayCookieConfigurationPropertiesProvider.getInstance();
        gatewayCookieConfigurationPropertiesProvider.initialize(configuration.getGatewayCookieConfiguration());

        OAuth2GatewayCookieConfigurationPropertiesProvider oAuth2GatewayCookieConfigurationPropertiesProvider = OAuth2GatewayCookieConfigurationPropertiesProvider.getInstance();
        oAuth2GatewayCookieConfigurationPropertiesProvider.initialize(configuration.getOauth2GatewayCookieConfiguration());
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

    private ChainedAuthFilter getAuthenticationFilters(AuthenticationConfiguration config, Authorizer authorizer)
    {
        ImmutableList.Builder<ContainerRequestFilter> authFilters = ImmutableList.builder();
        String defaultType = config.getDefaultType();
        if (oauthManager != null) {
            authFilters.add(new LbFilter(
                    new LbAuthenticator(oauthManager, authorizationManager),
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(defaultType)));
        }

        if (formAuthManager != null) {
            authFilters.add(new LbFilter(
                    new FormAuthenticator(formAuthManager, authorizationManager),
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(defaultType)));

            authFilters.add(new BasicAuthFilter(
                    new ApiAuthenticator(formAuthManager, authorizationManager),
                    authorizer,
                    new LbUnauthorizedHandler(defaultType)));
        }

        return new ChainedAuthFilter(authFilters.build());
    }

    private ResourceSecurityDynamicFeature getAuthFilter(HaGatewayConfiguration configuration)
    {
        AuthorizationConfiguration authorizationConfig = configuration.getAuthorization();
        Authorizer authorizer = (authorizationConfig != null)
                ? new LbAuthorizer(authorizationConfig) : new NoopAuthorizer();

        AuthenticationConfiguration authenticationConfig = configuration.getAuthentication();

        if (authenticationConfig != null) {
            return new ResourceSecurityDynamicFeature(getAuthenticationFilters(authenticationConfig, authorizer));
        }

        return new ResourceSecurityDynamicFeature(new NoopFilter());
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
    public BackendStateManager getBackendStateConnectionManager()
    {
        return this.backendStateConnectionManager;
    }

    @Provides
    @Singleton
    public RoutingGroupSelector getRoutingGroupSelector()
    {
        RoutingRulesConfiguration routingRulesConfig = configuration.getRoutingRules();
        if (routingRulesConfig.isRulesEngineEnabled()) {
            String rulesConfigPath = routingRulesConfig.getRulesConfigPath();
            return RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath, configuration.getRequestAnalyzerConfig());
        }
        return RoutingGroupSelector.byRoutingGroupHeader();
    }

    @Provides
    @Singleton
    public RoutingTargetHandler getRoutingTargetHandler(
            RoutingManager routingManager,
            RoutingGroupSelector routingGroupSelector)
    {
        return new RoutingTargetHandler(
                routingManager,
                routingGroupSelector,
                configuration.getExtraWhitelistPaths());
    }
}
