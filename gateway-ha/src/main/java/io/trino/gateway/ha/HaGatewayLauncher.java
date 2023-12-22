package io.trino.gateway.ha;

import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.views.common.ViewBundle;
import io.trino.gateway.baseapp.BaseApp;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

public class HaGatewayLauncher
        extends BaseApp<HaGatewayConfiguration>
{
    public HaGatewayLauncher(String... basePackages)
    {
        super(basePackages);
    }

    @Override
    public void initialize(Bootstrap<HaGatewayConfiguration> bootstrap)
    {
        super.initialize(bootstrap);
        bootstrap.addBundle(new ViewBundle<>());
        bootstrap.addBundle(new AssetsBundle("/assets", "/assets", null, "assets"));
    }

    public static void main(String[] args)
            throws Exception
    {
        /* base package is scanned for any Resource class to be loaded by default. */
        String basePackage = "io.trino";
        new HaGatewayLauncher(basePackage).run(args);
    }
}
