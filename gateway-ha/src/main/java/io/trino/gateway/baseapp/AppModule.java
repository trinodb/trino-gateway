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

import com.google.inject.AbstractModule;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.SimpleServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;

import java.util.stream.Stream;

public abstract class AppModule<T extends AppConfiguration, E>
        extends AbstractModule
{
    private final T configuration;
    private final E environment;

    public AppModule(T config, E env)
    {
        this.configuration = config;
        this.environment = env;
    }

    @Override
    protected void configure() {}

    protected int getApplicationPort()
    {
        Stream<ConnectorFactory> connectors =
                configuration.getServerFactory() instanceof DefaultServerFactory
                        ? ((DefaultServerFactory) configuration.getServerFactory())
                        .getApplicationConnectors().stream()
                        : Stream.of((SimpleServerFactory) configuration.getServerFactory())
                        .map(SimpleServerFactory::getConnector);

        return connectors
              .filter(connector -> connector instanceof HttpConnectorFactory || connector instanceof HttpsConnectorFactory)
              .map(connector -> {
                  if (connector instanceof HttpConnectorFactory) {
                      return ((HttpConnectorFactory) connector).getPort();
                  } else if (connector instanceof HttpsConnectorFactory) {
                      return ((HttpsConnectorFactory) connector).getPort();
                  } else {
                      throw new IllegalStateException("Unknown connector type");
                  }
              })
              .mapToInt(Integer::intValue)
              .findFirst()
              .orElseThrow(IllegalStateException::new);
    }

    public T getConfiguration()
    {
        return this.configuration;
    }

    public E getEnvironment()
    {
        return this.environment;
    }
}
