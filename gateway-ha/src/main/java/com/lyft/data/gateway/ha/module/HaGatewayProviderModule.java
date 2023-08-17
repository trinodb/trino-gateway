package com.lyft.data.gateway.ha.module;

import com.codahale.metrics.Meter;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.lyft.data.baseapp.AppModule;
import com.lyft.data.gateway.ha.config.AuthenticationConfiguration;
import com.lyft.data.gateway.ha.config.AuthorizationConfiguration;
import com.lyft.data.gateway.ha.config.HaGatewayConfiguration;
import com.lyft.data.gateway.ha.config.RequestRouterConfiguration;
import com.lyft.data.gateway.ha.config.RoutingRulesConfiguration;
import com.lyft.data.gateway.ha.config.UserConfiguration;
import com.lyft.data.gateway.ha.handler.QueryIdCachingProxyHandler;
import com.lyft.data.gateway.ha.persistence.JdbcConnectionManager;
import com.lyft.data.gateway.ha.router.BackendStateManager;
import com.lyft.data.gateway.ha.router.GatewayBackendManager;
import com.lyft.data.gateway.ha.router.HaGatewayManager;
import com.lyft.data.gateway.ha.router.HaQueryHistoryManager;
import com.lyft.data.gateway.ha.router.HaResourceGroupsManager;
import com.lyft.data.gateway.ha.router.HaRoutingManager;
import com.lyft.data.gateway.ha.router.QueryHistoryManager;
import com.lyft.data.gateway.ha.router.ResourceGroupsManager;
import com.lyft.data.gateway.ha.router.RoutingGroupSelector;
import com.lyft.data.gateway.ha.router.RoutingManager;
import com.lyft.data.gateway.ha.security.ApiAuthenticator;
import com.lyft.data.gateway.ha.security.AuthorizationManager;
import com.lyft.data.gateway.ha.security.FormAuthenticator;
import com.lyft.data.gateway.ha.security.LbAuthenticator;
import com.lyft.data.gateway.ha.security.LbAuthorizer;
import com.lyft.data.gateway.ha.security.LbFilter;
import com.lyft.data.gateway.ha.security.LbFormAuthManager;
import com.lyft.data.gateway.ha.security.LbOAuthManager;
import com.lyft.data.gateway.ha.security.LbPrincipal;
import com.lyft.data.gateway.ha.security.LbUnauthorizedHandler;
import com.lyft.data.gateway.ha.security.NoopAuthenticator;
import com.lyft.data.gateway.ha.security.NoopAuthorizer;
import com.lyft.data.gateway.ha.security.NoopFilter;
import com.lyft.data.proxyserver.ProxyHandler;
import com.lyft.data.proxyserver.ProxyServer;
import com.lyft.data.proxyserver.ProxyServerConfiguration;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.auth.Authorizer;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.chained.ChainedAuthFilter;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HaGatewayProviderModule extends AppModule<HaGatewayConfiguration, Environment> {

  private final ResourceGroupsManager resourceGroupsManager;
  private final GatewayBackendManager gatewayBackendManager;
  private final QueryHistoryManager queryHistoryManager;
  private final RoutingManager routingManager;
  private final JdbcConnectionManager connectionManager;
  private final LbOAuthManager oauthManager;
  private final LbFormAuthManager formAuthManager;
  private final AuthorizationManager authorizationManager;
  private final BackendStateManager backendStateConnectionManager;
  private final AuthFilter authenticationFilter;

  public HaGatewayProviderModule(HaGatewayConfiguration configuration, Environment environment) {
    super(configuration, environment);
    connectionManager = new JdbcConnectionManager(configuration.getDataStore());
    resourceGroupsManager = new HaResourceGroupsManager(connectionManager);
    gatewayBackendManager = new HaGatewayManager(connectionManager);
    queryHistoryManager = new HaQueryHistoryManager(connectionManager);
    routingManager =
        new HaRoutingManager(gatewayBackendManager, (HaQueryHistoryManager) queryHistoryManager);

    Map<String, UserConfiguration> presetUsers = configuration.getPresetUsers();
    AuthenticationConfiguration authenticationConfiguration = configuration.getAuthentication();

    oauthManager = getOAuthManager(configuration);
    formAuthManager = getFormAuthManager(configuration);

    authorizationManager = new AuthorizationManager(configuration.getAuthorization(),
        presetUsers);
    authenticationFilter = getAuthFilter(configuration);
    backendStateConnectionManager = new BackendStateManager(configuration.getBackendState());

  }

  private LbOAuthManager getOAuthManager(HaGatewayConfiguration configuration) {
    AuthenticationConfiguration authenticationConfiguration = configuration.getAuthentication();
    if (authenticationConfiguration != null
        && authenticationConfiguration.getOauth() != null) {
      return new LbOAuthManager(authenticationConfiguration.getOauth());
    }
    return null;
  }

  private LbFormAuthManager getFormAuthManager(HaGatewayConfiguration configuration) {
    AuthenticationConfiguration authenticationConfiguration = configuration.getAuthentication();
    if (authenticationConfiguration != null
        && authenticationConfiguration.getForm() != null) {
      return new LbFormAuthManager(authenticationConfiguration.getForm(),
          configuration.getPresetUsers());
    }
    return null;
  }

  private ChainedAuthFilter getAuthenticationFilters(AuthenticationConfiguration config,
                                                     Authorizer<LbPrincipal> authorizer) {
    List<AuthFilter> authFilters = new ArrayList<>();
    String defaultType = config.getDefaultType();
    if (oauthManager != null) {
      authFilters.add(new LbFilter.Builder<LbPrincipal>()
          .setAuthenticator(new LbAuthenticator(oauthManager,
              authorizationManager))
          .setAuthorizer(authorizer)
          .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
          .setPrefix("Bearer")
          .buildAuthFilter());
    }

    if (formAuthManager != null) {
      authFilters.add(new LbFilter.Builder<LbPrincipal>()
          .setAuthenticator(new FormAuthenticator(formAuthManager,
              authorizationManager))
          .setAuthorizer(authorizer)
          .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
          .setPrefix("Bearer")
          .buildAuthFilter());

      authFilters.add(new BasicCredentialAuthFilter.Builder<LbPrincipal>()
          .setAuthenticator(new ApiAuthenticator(formAuthManager,
              authorizationManager))
          .setAuthorizer(authorizer)
          .setUnauthorizedHandler(new LbUnauthorizedHandler(defaultType))
          .setPrefix("Basic")
          .buildAuthFilter());
    }

    return new ChainedAuthFilter(authFilters);

  }

  protected ProxyHandler getProxyHandler() {
    Meter requestMeter =
        getEnvironment()
            .metrics()
            .meter(getConfiguration().getRequestRouter().getName() + ".requests");

    // By default, use routing group header to route
    RoutingGroupSelector routingGroupSelector = RoutingGroupSelector.byRoutingGroupHeader();
    // Use rules engine if enabled
    RoutingRulesConfiguration routingRulesConfig = getConfiguration().getRoutingRules();
    if (routingRulesConfig.isRulesEngineEnabled()) {
      String rulesConfigPath = routingRulesConfig.getRulesConfigPath();
      routingGroupSelector = RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);
    }

    return new QueryIdCachingProxyHandler(
        getQueryHistoryManager(),
        getRoutingManager(),
        routingGroupSelector,
        getApplicationPort(),
        requestMeter);
  }

  protected AuthFilter getAuthFilter(HaGatewayConfiguration configuration) {

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
  public ProxyServer provideGateway() {
    ProxyServer gateway = null;
    if (getConfiguration().getRequestRouter() != null) {
      // Setting up request router
      RequestRouterConfiguration routerConfiguration = getConfiguration().getRequestRouter();

      ProxyServerConfiguration routerProxyConfig = new ProxyServerConfiguration();
      routerProxyConfig.setLocalPort(routerConfiguration.getPort());
      routerProxyConfig.setName(routerConfiguration.getName());
      routerProxyConfig.setProxyTo("");
      routerProxyConfig.setSsl(routerConfiguration.isSsl());
      routerProxyConfig.setKeystorePath(routerConfiguration.getKeystorePath());
      routerProxyConfig.setKeystorePass(routerConfiguration.getKeystorePass());
      routerProxyConfig.setForwardKeystore(routerConfiguration.isForwardKeystore());
      routerProxyConfig.setPreserveHost("false");
      ProxyHandler proxyHandler = getProxyHandler();
      gateway = new ProxyServer(routerProxyConfig, proxyHandler);
    }
    return gateway;
  }

  @Provides
  @Singleton
  public ResourceGroupsManager getResourceGroupsManager() {
    return this.resourceGroupsManager;
  }

  @Provides
  @Singleton
  public GatewayBackendManager getGatewayBackendManager() {
    return this.gatewayBackendManager;
  }

  @Provides
  @Singleton
  public QueryHistoryManager getQueryHistoryManager() {
    return this.queryHistoryManager;
  }

  @Provides
  @Singleton
  public RoutingManager getRoutingManager() {
    return this.routingManager;
  }

  @Provides
  @Singleton
  public JdbcConnectionManager getConnectionManager() {
    return this.connectionManager;
  }

  @Provides
  @Singleton
  public LbOAuthManager getAuthenticationManager() {
    return this.oauthManager;
  }

  @Provides
  @Singleton
  public LbFormAuthManager getFormAuthentication() {
    return this.formAuthManager;
  }

  @Provides
  @Singleton
  public AuthorizationManager getAuthorizationManager() {
    return this.authorizationManager;
  }

  @Provides
  @Singleton
  public AuthFilter getAuthenticationFilter() {
    return authenticationFilter;
  }

  @Provides
  @Singleton
  public BackendStateManager getBackendStateConnectionManager() {
    return this.backendStateConnectionManager;
  }
}
