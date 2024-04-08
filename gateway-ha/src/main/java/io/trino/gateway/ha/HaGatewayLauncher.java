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

import io.airlift.configuration.ConfigurationFactory;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.trino.gateway.baseapp.BaseApp;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

import java.util.Map;

import static io.airlift.configuration.ConfigurationLoader.getSystemProperties;

public class HaGatewayLauncher
        extends BaseApp<HaGatewayConfiguration>
{
    @Override
    public void initialize(Bootstrap<HaGatewayConfiguration> bootstrap)
    {
        super.initialize(bootstrap);
        bootstrap.addBundle(new AssetsBundle("/static/assets", "/assets", null, "assets"));
        bootstrap.addBundle(new AssetsBundle("/static", "/logo.svg", "logo.svg", "logo.svg"));
    }

    public static void main(String[] args)
            throws Exception
    {
        Logging.initialize();
        Map<String, String> properties = getSystemProperties();
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        LoggingConfiguration configuration = configurationFactory.build(LoggingConfiguration.class);
        Logging logging = Logging.initialize();
        logging.configure(configuration);

        new HaGatewayLauncher().run(args);
    }
}
