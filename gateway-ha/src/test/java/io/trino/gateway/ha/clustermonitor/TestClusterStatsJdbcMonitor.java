package io.trino.gateway.ha.clustermonitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class TestClusterStatsJdbcMonitor {
    ClusterStatsJdbcMonitor clusterStatsJdbcMonitor;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    private java.util.Properties properties;

    @BeforeEach
    public void initMocks() {
        log.info("initializing test");
        MockitoAnnotations.openMocks(this);
    }

    @AfterAll
    public void resetMocks() {
        log.info("resetting mocks");
        Mockito.reset(connection);
        Mockito.reset(preparedStatement);
        Mockito.reset(resultSet);
    }

    @BeforeAll
    public void setUp() {
        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("Trino");
        properties = new java.util.Properties();
        properties.setProperty("user", backendStateConfiguration.getUsername());
        properties.setProperty("password", backendStateConfiguration.getPassword());
        properties.setProperty("SSL", String.valueOf(backendStateConfiguration.getSsl()));
        clusterStatsJdbcMonitor = new ClusterStatsJdbcMonitor(backendStateConfiguration);
    }

    private static Stream<Arguments> provideSchemeAndPort(){
        return Stream.of(
            Arguments.of("https", "90", "jdbc:trino://trino.example.com:90/system"),
            Arguments.of("http", "90", "jdbc:trino://trino.example.com:90/system"),
            Arguments.of("https", "", "jdbc:trino://trino.example.com:443/system"),
            Arguments.of("http", "", "jdbc:trino://trino.example.com:80/system")
        );
    }
    @ParameterizedTest
    @MethodSource("provideSchemeAndPort")
    public void testProtocol(String scheme, String port, String expectedJdbcUrl) throws java.sql.SQLException {
        try(MockedStatic<DriverManager> m = Mockito.mockStatic(java.sql.DriverManager.class)) {
            m.when(() -> DriverManager.getConnection(anyString(), any())).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setProxyTo(String.format("%s://trino.example.com:%s", scheme, port));
            proxyBackend.setName("abc");

            clusterStatsJdbcMonitor.monitor(proxyBackend);

            m.verify(() -> DriverManager.getConnection(expectedJdbcUrl, properties));
        }
    }
}

