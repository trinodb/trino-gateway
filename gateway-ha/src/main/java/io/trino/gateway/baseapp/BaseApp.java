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

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.log.Logger;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.servlets.tasks.Task;
import io.trino.gateway.ha.log.GatewayRequestLogFactory;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Supports Guice in Dropwizard.
 *
 * <p>To use it, create a subclass and provide a list of modules you want to use with the {@link
 * #addModules} method.
 *
 * <p>Packages supplied in the constructor will be scanned for Resources, Tasks, Providers,
 * Healthchecks and Managed classes, and added to the environment.
 *
 * <p>GuiceApplication also makes {@link com.codahale.metrics.MetricRegistry} available for
 * injection.
 */
public abstract class BaseApp<T extends AppConfiguration>
        extends Application<T>
{
    private static final Logger logger = Logger.get(BaseApp.class);

    private final Reflections reflections;
    private final ImmutableList.Builder<Module> appModules = ImmutableList.builder();

    protected BaseApp(String... basePackages)
    {
        final ConfigurationBuilder confBuilder = new ConfigurationBuilder();
        final FilterBuilder filterBuilder = new FilterBuilder();

        if (basePackages.length == 0) {
            basePackages = new String[] {};
        }

        logger.info("op=create auto_scan_packages=%s", basePackages);

        for (String basePkg : basePackages) {
            confBuilder.addUrls(ClasspathHelper.forPackage(basePkg));
            filterBuilder.include(FilterBuilder.prefix(basePkg));
        }

        confBuilder
                .filterInputsBy(filterBuilder)
                .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner());

        this.reflections = new Reflections(confBuilder);
    }

    @Override // Using Airlift logger
    protected void bootstrapLogging() {}

    /**
     * Initializes the application bootstrap.
     *
     * @param bootstrap the application bootstrap
     */
    @Override
    public void initialize(Bootstrap<T> bootstrap)
    {
        super.initialize(bootstrap);
    }

    /**
     * When the application runs, this is called after the bundles are run.
     *
     * @param configuration the parsed {@link Configuration} object
     * @param environment the application's {@link Environment}
     * @throws Exception if something goes wrong
     */
    @Override
    public void run(T configuration, Environment environment)
            throws Exception
    {
        ((DefaultServerFactory) configuration.getServerFactory()).setRequestLogFactory(new GatewayRequestLogFactory());
        configureGuice(configuration, environment);
    }

    private void configureGuice(T configuration, Environment environment)
    {
        appModules.add(new MetricRegistryModule(environment.metrics()));
        appModules.addAll(addModules(configuration, environment));
        Injector injector = Guice.createInjector(appModules.build());
        injector.injectMembers(this);
        registerWithInjector(configuration, environment, injector);
    }

    private void registerWithInjector(T configuration, Environment environment, Injector injector)
    {
        logger.info("op=register_start configuration=%s", configuration.toString());
        registerAuthFilters(environment, injector);
        registerHealthChecks(environment, injector);
        registerProviders(environment, injector);
        registerTasks(environment, injector);
        addManagedApps(configuration, environment, injector);
        registerResources(environment, injector);
        logger.info("op=register_end configuration=%s", configuration.toString());
    }

    /**
     * Supply a list of modules to be used by Guice.
     *
     * @param configuration the app configuration
     * @return a list of modules to be provisioned by Guice
     */
    protected List<AppModule> addModules(T configuration, Environment environment)
    {
        List<AppModule> modules = new ArrayList<>();
        if (configuration.getModules() == null) {
            logger.warn("No modules to load.");
            return modules;
        }
        for (String clazz : configuration.getModules()) {
            try {
                logger.info("Trying to load module [%s]", clazz);
                Object ob =
                        Class.forName(clazz)
                                .getConstructor(configuration.getClass(), Environment.class)
                                .newInstance(configuration, environment);
                modules.add((AppModule) ob);
            }
            catch (Exception e) {
                logger.error(e, "Could not instantiate module [%s]", clazz);
            }
        }
        return modules;
    }

    /**
     * Supply a list of managed apps.
     */
    protected List<Managed> addManagedApps(
            T configuration, Environment environment, Injector injector)
    {
        List<Managed> managedApps = new ArrayList<>();
        if (configuration.getManagedApps() == null) {
            logger.error("No managed apps found");
            return managedApps;
        }
        configuration
                .getManagedApps()
                .forEach(
                        clazz -> {
                            try {
                                Class c = Class.forName(clazz);
                                LifecycleEnvironment lifecycle = environment.lifecycle();
                                lifecycle.manage((Managed) injector.getInstance(c));
                                logger.info("op=register type=managed item=%s", c);
                            }
                            catch (Exception e) {
                                logger.error(e, "Error loading managed app");
                            }
                        });
        return managedApps;
    }

    private void registerTasks(Environment environment, Injector injector)
    {
        final Set<Class<? extends Task>> classes = reflections.getSubTypesOf(Task.class);
        classes.forEach(
                c -> {
                    environment.admin().addTask(injector.getInstance(c));
                    logger.info("op=register type=task item=%s", c);
                });
    }

    private void registerHealthChecks(Environment environment, Injector injector)
    {
        final Set<Class<? extends HealthCheck>> classes = reflections.getSubTypesOf(HealthCheck.class);
        classes.forEach(
                c -> {
                    environment.healthChecks().register(c.getSimpleName(), injector.getInstance(c));
                    logger.info("op=register type=healthcheck item=%s", c);
                });
    }

    private void registerProviders(Environment environment, Injector injector)
    {
        final Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Provider.class);
        classes.forEach(
                c -> {
                    environment.jersey().register(injector.getInstance(c));
                    logger.info("op=register type=provider item=%s", c);
                });
    }

    private void registerResources(Environment environment, Injector injector)
    {
        final Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Path.class);
        classes.forEach(
                c -> {
                    environment.jersey().register(injector.getInstance(c));
                    logger.info("op=register type=resource item=%s", c);
                });
    }

    private void registerAuthFilters(Environment environment, Injector injector)
    {
        environment
                .jersey()
                .register(new AuthDynamicFeature(injector.getInstance(AuthFilter.class)));
        logger.info("op=register type=auth filter item=%s", AuthFilter.class);
        environment.jersey().register(RolesAllowedDynamicFeature.class);
    }
}
