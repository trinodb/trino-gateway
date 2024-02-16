package io.trino.gateway.baseapp;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jetty.HttpConnectorFactory;
import org.junit.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.testcontainers.shaded.com.github.dockerjava.core.dockerfile.DockerfileStatement;

import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestAppModule {

    @Test
    public void testGetApplicationPort() {

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

    @Test(expected = IllegalStateException.class)
    public void testGetApplicationPortThrowsException() {

        DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
        defaultServerFactory.setApplicationConnectors(Collections.emptyList());

        AppConfiguration appConfiguration = new AppConfiguration();
        appConfiguration.setServerFactory(defaultServerFactory);

        Environment environment = new Environment("test");

        AppModule<AppConfiguration, Environment> appModule = new AppModule<>(appConfiguration, environment) {};

        appModule.getApplicationPort();
    }
}

