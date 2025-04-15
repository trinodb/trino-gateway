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
package io.trino.gateway.baseapp;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import io.trino.gateway.ha.clustermonitor.ClusterMetricsStatsExporter;
import io.trino.gateway.ha.clustermonitor.ForMonitor;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.handler.ProxyHandlerStats;
import io.trino.gateway.ha.handler.RoutingTargetHandler;
import io.trino.gateway.ha.module.RouterBaseModule;
import io.trino.gateway.ha.module.StochasticRoutingManagerProvider;
import io.trino.gateway.ha.resource.EntityEditorResource;
import io.trino.gateway.ha.resource.GatewayHealthCheckResource;
import io.trino.gateway.ha.resource.GatewayResource;
import io.trino.gateway.ha.resource.GatewayViewResource;
import io.trino.gateway.ha.resource.GatewayWebAppResource;
import io.trino.gateway.ha.resource.HaGatewayResource;
import io.trino.gateway.ha.resource.LoginResource;
import io.trino.gateway.ha.resource.PublicResource;
import io.trino.gateway.ha.resource.TrinoResource;
import io.trino.gateway.ha.router.ForRouter;
import io.trino.gateway.ha.router.RoutingRulesManager;
import io.trino.gateway.ha.security.AuthorizedExceptionMapper;
import io.trino.gateway.proxyserver.ForProxy;
import io.trino.gateway.proxyserver.ProxyRequestHandler;
import io.trino.gateway.proxyserver.RouteToBackendResource;
import io.trino.gateway.proxyserver.RouterPreMatchContainerRequestFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.collect.MoreCollectors.toOptional;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class BaseApp
        implements Module
{
    private static final Logger logger = Logger.get(BaseApp.class);
    private final HaGatewayConfiguration configuration;

    public BaseApp(HaGatewayConfiguration configuration)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");
    }

    private static Module newModule(String clazz, HaGatewayConfiguration configuration)
    {
        try {
            logger.info("Trying to load module [%s]", clazz);
            // Modules must have exactly one constructor. The signature must be:
            // public Module constructor(HaGatewayConfiguration)
            Constructor<?>[] constructors = Class.forName(clazz).getConstructors();
            if (constructors.length != 1) {
                throw new RuntimeException(format("Failed to load module [%s]. Multiple constructors exist.", clazz));
            }
            Constructor<?> constructor = constructors[0];
            if (constructor.getParameterCount() != 1) {
                throw new RuntimeException(format("Failed to load module [%s]. Unsupported constructor.", clazz));
            }
            Object module = constructor.newInstance(configuration);
            return ((Module) module);
        }
        catch (Exception e) {
            logger.error(e, "Could not instantiate module [%s]", clazz);
            System.exit(1);
        }
        return null;
    }

    private static void addDefaultRouterProviderModules(List<Module> modules, HaGatewayConfiguration configuration)
    {
        Optional<Module> routerProvider = modules.stream()
                .filter(module -> module instanceof RouterBaseModule)
                .collect(toOptional());
        if (routerProvider.isEmpty()) {
            logger.warn("Router provider doesn't exist in the config, using the StochasticRoutingManagerProvider");
            String clazz = StochasticRoutingManagerProvider.class.getCanonicalName();
            modules.add(newModule(clazz, configuration));
        }
    }

    public static List<Module> addModules(HaGatewayConfiguration configuration)
    {
        List<Module> modules = new ArrayList<>();
        if (configuration.getModules() == null) {
            logger.info("No modules to load.");
        }
        else {
            for (String clazz : configuration.getModules()) {
                warnLoadingDefaultModules(clazz);
                modules.add(newModule(clazz, configuration));
            }
        }
        addDefaultRouterProviderModules(modules, configuration);

        return modules;
    }

    private static void warnLoadingDefaultModules(String clazz)
    {
        // Remove this check when user finished the migration
        List<String> defaultModules = ImmutableList.of(
                "io.trino.gateway.ha.module.HaGatewayProviderModule",
                "io.trino.gateway.ha.module.ClusterStateListenerModule",
                "io.trino.gateway.ha.module.ClusterStatsMonitorModule");
        if (defaultModules.contains(clazz)) {
            logger.error("Default module [%s] is already being loaded. Please remove it from the config file", clazz);
            System.exit(1);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(HaGatewayConfiguration.class).toInstance(configuration);
        binder.bind(ActiveClusterMonitor.class).in(Scopes.SINGLETON);
        registerAuthFilters(binder);
        registerResources(binder);
        registerProxyResources(binder);
        jaxrsBinder(binder).bind(RoutingTargetHandler.class);
        addManagedApps(configuration, binder);
        jaxrsBinder(binder).bind(AuthorizedExceptionMapper.class);
        binder.bind(ProxyHandlerStats.class).in(Scopes.SINGLETON);
        newExporter(binder).export(ProxyHandlerStats.class).withGeneratedName();
        binder.bind(RoutingRulesManager.class);
        binder.bind(ClusterMetricsStatsExporter.class).in(Scopes.SINGLETON);
    }

    private static void addManagedApps(HaGatewayConfiguration configuration, Binder binder)
    {
        if (configuration.getManagedApps() == null) {
            logger.info("No managed apps found");
            return;
        }
        configuration.getManagedApps().forEach(
                clazz -> {
                    // Remove this check when user finished the migration
                    if (clazz.equals("io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor")) {
                        logger.error("Default class ActiveClusterMonitor is already being loaded. Please remove it from the config file");
                        System.exit(1);
                    }
                    try {
                        Class<?> c = Class.forName(clazz);
                        binder.bind(c).in(Scopes.SINGLETON);
                    }
                    catch (Exception e) {
                        logger.error(e, "Error loading managed app");
                    }
                });
    }

    private static void registerResources(Binder binder)
    {
        jaxrsBinder(binder).bind(EntityEditorResource.class);
        jaxrsBinder(binder).bind(GatewayResource.class);
        jaxrsBinder(binder).bind(GatewayViewResource.class);
        jaxrsBinder(binder).bind(GatewayWebAppResource.class);
        jaxrsBinder(binder).bind(HaGatewayResource.class);
        jaxrsBinder(binder).bind(LoginResource.class);
        jaxrsBinder(binder).bind(PublicResource.class);
        jaxrsBinder(binder).bind(TrinoResource.class);
        jaxrsBinder(binder).bind(WebUIStaticResource.class);
        jaxrsBinder(binder).bind(GatewayHealthCheckResource.class);
    }

    private static void registerAuthFilters(Binder binder)
    {
        jaxrsBinder(binder).bind(RolesAllowedDynamicFeature.class);
    }

    private static void registerProxyResources(Binder binder)
    {
        jaxrsBinder(binder).bind(RouteToBackendResource.class);
        jaxrsBinder(binder).bind(RouterPreMatchContainerRequestFilter.class);
        jaxrsBinder(binder).bind(ProxyRequestHandler.class);
        httpClientBinder(binder).bindHttpClient("proxy", ForProxy.class);
        httpClientBinder(binder).bindHttpClient("monitor", ForMonitor.class);
        httpClientBinder(binder).bindHttpClient("router", ForRouter.class);
    }
}
