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
