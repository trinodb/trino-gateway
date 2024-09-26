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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import io.airlift.bootstrap.ApplicationConfigurationException;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jmx.JmxHttpModule;
import io.airlift.jmx.JmxModule;
import io.airlift.json.JsonModule;
import io.airlift.log.LogJmxModule;
import io.airlift.log.Logger;
import io.airlift.node.NodeModule;
import io.airlift.openmetrics.JmxOpenMetricsModule;
import io.airlift.units.Duration;
import io.trino.gateway.baseapp.BaseApp;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import org.weakref.jmx.guice.MBeanModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.trino.gateway.baseapp.BaseApp.addModules;
import static io.trino.gateway.ha.util.ConfigurationUtils.replaceEnvironmentVariables;
import static java.lang.String.format;

public class HaGatewayLauncher
{
    private static final Logger logger = Logger.get(HaGatewayLauncher.class);

    private void start(List<Module> additionalModules, HaGatewayConfiguration configuration)
    {
        long startTime = System.nanoTime();

        ImmutableList.Builder<Module> modules = ImmutableList.builder();
        modules.add(
                new NodeModule(),
                new EventModule(),
                new HttpServerModule(),
                new JmxModule(),
                new JmxHttpModule(),
                new JmxOpenMetricsModule(),
                new LogJmxModule(),
                new MBeanModule(),
                new JsonModule(),
                new JaxrsModule(),
                new BaseApp(configuration));
        modules.addAll(additionalModules);

        Bootstrap app = new Bootstrap(modules.build())
                .setRequiredConfigurationProperties(configuration.getServerConfig());
        try {
            app.initialize();
        }
        catch (ApplicationConfigurationException e) {
            StringBuilder message = new StringBuilder();
            message.append("Configuration is invalid\n");
            message.append("==========\n");
            addMessages(message, "Errors", ImmutableList.copyOf(e.getErrors()));
            addMessages(message, "Warnings", ImmutableList.copyOf(e.getWarnings()));
            message.append("\n");
            message.append("==========");
            logger.error("%s", message);
            System.exit(100);
        }
        catch (Throwable e) {
            logger.error(e);
            System.exit(100);
        }
        logger.info("Server startup completed in %s", Duration.nanosSince(startTime).convertToMostSuccinctTimeUnit());
        logger.info("======== SERVER STARTED ========");
    }

    private static void addMessages(StringBuilder output, String type, List<Object> messages)
    {
        if (messages.isEmpty()) {
            return;
        }
        output.append("\n").append(type).append(":\n\n");
        for (int index = 0; index < messages.size(); index++) {
            output.append(format("%s) %s\n", index + 1, messages.get(index)));
        }
    }

    public static void main(String[] args)
            throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument (path of configuration file)");
        }
        String config = Files.readString(Path.of(args[0]));
        HaGatewayConfiguration haGatewayConfiguration = objectMapper.readValue(replaceEnvironmentVariables(config), HaGatewayConfiguration.class);
        List<Module> modules = addModules(haGatewayConfiguration);
        new HaGatewayLauncher().start(modules, haGatewayConfiguration);
    }
}
