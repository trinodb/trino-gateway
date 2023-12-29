package io.trino.gateway.baseapp;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthFilter;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.servlets.tasks.Task;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Supports Guice in Dropwizard.
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
    private static final Logger logger = LoggerFactory.getLogger(BaseApp.class);

    private final Reflections reflections;
    private final List<Module> appModules = Lists.newArrayList();
    private Injector injector;

    protected BaseApp(String... basePackages)
    {
        final ConfigurationBuilder confBuilder = new ConfigurationBuilder();
        final FilterBuilder filterBuilder = new FilterBuilder();

        if (basePackages.length == 0) {
            basePackages = new String[] {};
        }

        logger.info("op=create auto_scan_packages={}", basePackages);

        for (String basePkg : basePackages) {
            confBuilder.addUrls(ClasspathHelper.forPackage(basePkg));
            filterBuilder.include(FilterBuilder.prefix(basePkg));
        }

        confBuilder
                .filterInputsBy(filterBuilder)
                .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner());

        this.reflections = new Reflections(confBuilder);
    }

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
        this.injector = configureGuice(configuration, environment);
        logger.info("op=configure_guice injector={}", injector);
        logger.info("op=configure_app_custom completed");
    }

    private Injector configureGuice(T configuration, Environment environment)
            throws Exception
    {
        appModules.add(new MetricRegistryModule(environment.metrics()));
        Injector injector = Guice.createInjector(ImmutableList.copyOf(appModules));
        injector.injectMembers(this);
        registerWithInjector(configuration, environment, injector);
        return injector;
    }

    private void registerWithInjector(T configuration, Environment environment, Injector injector)
    {
        logger.info("op=register_start configuration={}", configuration.toString());
        registerAuthFilters(environment, injector);
        registerHealthChecks(environment, injector);
        registerProviders(environment, injector);
        registerTasks(environment, injector);
        registerResources(environment, injector);
        logger.info("op=register_end configuration={}", configuration.toString());
    }

    private void registerTasks(Environment environment, Injector injector)
    {
        final Set<Class<? extends Task>> classes = reflections.getSubTypesOf(Task.class);
        classes.forEach(
                c -> {
                    environment.admin().addTask(injector.getInstance(c));
                    logger.info("op=register type=task item={}", c);
                });
    }

    private void registerHealthChecks(Environment environment, Injector injector)
    {
        final Set<Class<? extends HealthCheck>> classes = reflections.getSubTypesOf(HealthCheck.class);
        classes.forEach(
                c -> {
                    environment.healthChecks().register(c.getSimpleName(), injector.getInstance(c));
                    logger.info("op=register type=healthcheck item={}", c);
                });
    }

    private void registerProviders(Environment environment, Injector injector)
    {
        final Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Provider.class);
        classes.forEach(
                c -> {
                    environment.jersey().register(injector.getInstance(c));
                    logger.info("op=register type=provider item={}", c);
                });
    }

    private void registerResources(Environment environment, Injector injector)
    {
        final Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Path.class);
        classes.forEach(
                c -> {
                    environment.jersey().register(injector.getInstance(c));
                    logger.info("op=register type=resource item={}", c);
                });
    }

    private void registerAuthFilters(Environment environment, Injector injector)
    {
        environment
                .jersey()
                .register(new AuthDynamicFeature((injector.getInstance(AuthFilter.class))));
        logger.info("op=register type=auth filter item={}", AuthFilter.class);
        environment.jersey().register(RolesAllowedDynamicFeature.class);
    }
}
