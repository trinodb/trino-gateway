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
import com.google.common.collect.MoreCollectors;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.module.RouterBaseModule;
import io.trino.gateway.ha.module.StochasticRoutingManagerProvider;
import io.trino.gateway.ha.resource.EntityEditorResource;
import io.trino.gateway.ha.resource.GatewayResource;
import io.trino.gateway.ha.resource.GatewayViewResource;
import io.trino.gateway.ha.resource.GatewayWebAppResource;
import io.trino.gateway.ha.resource.HaGatewayResource;
import io.trino.gateway.ha.resource.LoginResource;
import io.trino.gateway.ha.resource.PublicResource;
import io.trino.gateway.ha.resource.TrinoResource;
import io.trino.gateway.ha.security.AuthorizedExceptionMapper;
import io.trino.gateway.ha.security.ResourceSecurityDynamicFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class BaseApp
        implements Module
{
    private static final Logger logger = Logger.get(BaseApp.class);
    private final ImmutableList.Builder<Module> appModules = ImmutableList.builder();
    private final HaGatewayConfiguration haGatewayConfiguration;

    public BaseApp(HaGatewayConfiguration haGatewayConfiguration)
    {
        this.haGatewayConfiguration = requireNonNull(haGatewayConfiguration);
    }

    private Module newModule(String clazz, HaGatewayConfiguration configuration)
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

    private void validateModules(List<Module> modules, HaGatewayConfiguration configuration)
    {
        Optional<Module> routerProvider = modules.stream()
                .filter(module -> module instanceof RouterBaseModule)
                .collect(MoreCollectors.toOptional());
        if (routerProvider.isEmpty()) {
            logger.warn("Router provider doesn't exist in the config, using the StochasticRoutingManagerProvider");
            String clazz = StochasticRoutingManagerProvider.class.getCanonicalName();
            modules.add(newModule(clazz, configuration));
        }
    }

    protected List<Module> addModules(HaGatewayConfiguration configuration)
    {
        List<Module> modules = new ArrayList<>();
        if (configuration.getModules() == null) {
            logger.warn("No modules to load.");
            return modules;
        }
        for (String clazz : configuration.getModules()) {
            modules.add(newModule(clazz, configuration));
        }

        validateModules(modules, configuration);

        return modules;
    }

    @Override
    public void configure(Binder binder)
    {
        registerWithInjector(this.haGatewayConfiguration, binder);
    }

    private static void registerWithInjector(HaGatewayConfiguration configuration, Binder binder)
    {
        registerAuthFilters(binder);
        registerProviders(binder);
        addManagedApps(configuration, binder);
        registerResources(binder);
    }

    private static void addManagedApps(HaGatewayConfiguration configuration, Binder binder)
    {
        if (configuration.getManagedApps() == null) {
            logger.error("No managed apps found");
            return;
        }
        configuration.getManagedApps().forEach(
                clazz -> {
                    try {
                        Class c = Class.forName(clazz);
                        binder.bind(c).in(Scopes.SINGLETON);
                    }
                    catch (Exception e) {
                        logger.error(e, "Error loading managed app");
                    }
                });
    }

    private static void registerProviders(Binder binder)
    {
        jaxrsBinder(binder).bind(AuthorizedExceptionMapper.class);
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
    }

    private static void registerAuthFilters(Binder binder)
    {
        jaxrsBinder(binder).bind(ResourceSecurityDynamicFeature.class);
        jaxrsBinder(binder).bind(RolesAllowedDynamicFeature.class);
    }
}
