package io.trino.gateway.baseapp;
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
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.HttpConnectorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAppModule
{
    @Test
    public void testGetApplicationPort()
    {
        AppConfiguration mockConfig = Mockito.mock(AppConfiguration.class);
        DefaultServerFactory mockServerFactory = Mockito.mock(DefaultServerFactory.class);
        HttpConnectorFactory mockConnector = Mockito.mock(HttpConnectorFactory.class);

        when(mockConfig.getServerFactory()).thenReturn(mockServerFactory);
        when(mockServerFactory.getApplicationConnectors()).thenReturn(List.of(mockConnector));
        when(mockConnector.getPort()).thenReturn(8090);

        AppModule<AppConfiguration, Object> appModule = new AppModule<>(mockConfig, new Object()) {};

        int port = appModule.getApplicationPort();

        assertEquals(8090, port);
    }

    @Test
    public void testGetApplicationPortThrowsException()
    {
        DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
        defaultServerFactory.setApplicationConnectors(Collections.emptyList());

        AppConfiguration appConfiguration = new AppConfiguration();
        appConfiguration.setServerFactory(defaultServerFactory);

        Environment environment = new Environment("test");

        AppModule<AppConfiguration, Environment> appModule = new AppModule<>(appConfiguration, environment) {};

        assertThrows(IllegalStateException.class, appModule::getApplicationPort);
    }
}
