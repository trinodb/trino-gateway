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
package io.trino.gateway.ha.clustermonitor;

import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class TestClusterStatsJdbcMonitor
{
    private static final Logger log = LoggerFactory.getLogger(TestClusterStatsJdbcMonitor.class);

    ClusterStatsJdbcMonitor clusterStatsJdbcMonitor;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;

    private Properties properties;

    private static Stream<Arguments> provideProtocolAndPortAndSsl()
    {
        return Stream.of(
                Arguments.of("https", "90", "jdbc:trino://trino.example.com:90/system", "true"),
                Arguments.of("http", "90", "jdbc:trino://trino.example.com:90/system", "false"),
                Arguments.of("https", "", "jdbc:trino://trino.example.com:443/system", "true"),
                Arguments.of("http", "", "jdbc:trino://trino.example.com:80/system", "false"));
    }

    @BeforeEach
    public void initMocks()
    {
        log.info("initializing test");
        MockitoAnnotations.openMocks(this);
    }

    @AfterAll
    public void resetMocks()
    {
        log.info("resetting mocks");
        Mockito.reset(connection);
        Mockito.reset(preparedStatement);
        Mockito.reset(resultSet);
    }

    @BeforeAll
    public void setUp()
    {
        BackendStateConfiguration backendStateConfiguration = new BackendStateConfiguration();
        backendStateConfiguration.setUsername("Trino");
        properties = new Properties();
        properties.setProperty("user", backendStateConfiguration.getUsername());
        properties.setProperty("password", backendStateConfiguration.getPassword());
        properties.setProperty("SSL", String.valueOf(backendStateConfiguration.getSsl()));
        clusterStatsJdbcMonitor = new ClusterStatsJdbcMonitor(backendStateConfiguration);
    }

    @ParameterizedTest
    @MethodSource("provideProtocolAndPortAndSsl")
    public void testProtocolAndSsl(String scheme, String port, String expectedJdbcUrl, String expectedSsl)
            throws java.sql.SQLException
    {
        try (MockedStatic<DriverManager> m = Mockito.mockStatic(java.sql.DriverManager.class)) {
            m.when(() -> DriverManager.getConnection(anyString(), any())).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            ProxyBackendConfiguration proxyBackend = new ProxyBackendConfiguration();
            proxyBackend.setProxyTo(String.format("%s://trino.example.com:%s", scheme, port));
            proxyBackend.setName("abc");

            clusterStatsJdbcMonitor.monitor(proxyBackend);

            properties.setProperty("SSL", expectedSsl);

            m.verify(() -> DriverManager.getConnection(expectedJdbcUrl, properties));
        }
    }
}
