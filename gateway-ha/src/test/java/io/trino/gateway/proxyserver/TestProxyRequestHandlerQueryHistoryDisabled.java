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
package io.trino.gateway.proxyserver;

import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.jdbc.TrinoResultSet;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static io.trino.gateway.ha.util.TestcontainersUtils.createTrinoContainer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@Testcontainers
@TestInstance(PER_CLASS)
final class TestProxyRequestHandlerQueryHistoryDisabled
{
    @Container
    private static final TrinoContainer TRINO = createTrinoContainer()
            .withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");

    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final int routerPort = 22001 + (int) (Math.random() * 1000);
    private Jdbi jdbi;

    @BeforeAll
    void setup()
            throws Exception
    {
        File testConfigFile = buildGatewayConfig(POSTGRESQL, routerPort, "test-config-with-query-history-disabled.yml");

        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        setUpBackend("trino", "http://localhost:" + TRINO.getMappedPort(8080), "externalUrl", true, "adhoc", routerPort);

        jdbi = Jdbi.create(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }

    @Test
    void testQueryHistoryNotRecordedWhenDisabled()
            throws Exception
    {
        String jdbcUrl = "jdbc:trino://localhost:" + routerPort;

        // Execute query using Trino JDBC driver and get query ID
        String queryId;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "test-user", null);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);

            // Get query ID from TrinoResultSet
            TrinoResultSet trinoResultSet = resultSet.unwrap(TrinoResultSet.class);
            queryId = trinoResultSet.getQueryId();
            assertThat(queryId).isNotNull();
        }

        // Wait a bit for async query history submission (if it were to happen)
        sleepUninterruptibly(2, SECONDS);

        // Verify that query history was NOT recorded in the database
        List<Map<String, Object>> queryHistory = jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM query_history WHERE query_id = :queryId")
                        .bind("queryId", queryId)
                        .mapToMap()
                        .list());

        assertThat(queryHistory).isEmpty();
    }
}
