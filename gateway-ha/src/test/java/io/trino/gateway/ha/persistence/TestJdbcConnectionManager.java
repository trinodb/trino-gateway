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
package io.trino.gateway.ha.persistence;

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

final class TestJdbcConnectionManager
{
    @Test
    void testBuildJdbcUrlWithMySQLAndNoRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:mysql://localhost:3306/mydb");
        assertThat(connectionManager.buildJdbcUrl(null)).isEqualTo("jdbc:mysql://localhost:3306/mydb");
    }

    @Test
    void testBuildJdbcUrlWithMySQLAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:mysql://localhost:3306/mydb");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:mysql://localhost:3306/newdb");
    }

    @Test
    void testBuildJdbcUrlWithMySQLAndParametersAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Seoul");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:mysql://localhost:3306/newdb?useSSL=false&serverTimezone=Asia/Seoul");
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndNoRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:postgresql://localhost:5432/mydb");
        assertThat(connectionManager.buildJdbcUrl(null)).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:postgresql://localhost:5432/mydb");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:postgresql://localhost:5432/newdb");
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndParametersAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:postgresql://localhost:5432/mydb?ssl=false&serverTimezone=Asia/Seoul");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:postgresql://localhost:5432/newdb?ssl=false&serverTimezone=Asia/Seoul");
    }

    @Test
    void testBuildJdbcUrlWithOracleAndNoRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:oracle:thin:@//localhost:1521/mydb");
        assertThat(connectionManager.buildJdbcUrl(null)).isEqualTo("jdbc:oracle:thin:@//localhost:1521/mydb");
    }

    @Test
    void testBuildJdbcUrlWithOracleAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:oracle:thin:@//localhost:1521/mydb");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:oracle:thin:@//localhost:1521/newdb");
    }

    @Test
    void testBuildJdbcUrlWithOracleAndParametersAndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:oracle:thin:@//localhost:1521/mydb?sessionTimeZone=Asia/Seoul");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:oracle:thin:@//localhost:1521/newdb?sessionTimeZone=Asia/Seoul");
    }

    @Test
    void testBuildJdbcUrlWithNullJdbcUrlThrowsException()
    {
        // Mock the behavior of DataStoreConfiguration.getJdbcUrl
        DataStoreConfiguration dataStoreConfiguration = Mockito.mock(DataStoreConfiguration.class);
        when(dataStoreConfiguration.getJdbcUrl()).thenReturn(null);

        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create("jdbc:postgresql://localhost:5432/mydb", "postgres", "postgres"), dataStoreConfiguration);
        assertThatThrownBy(() -> connectionManager.buildJdbcUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JDBC URL cannot be null");
    }

    @Test
    void testBuildJdbcUrlWithNoSlashThrowsException()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:postgresql:mydb");
        assertThatThrownBy(() -> connectionManager.buildJdbcUrl("newdb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JDBC URL: no '/' found in jdbc:postgresql:mydb");
    }

    @Test
    void testOauth2RoutingSweepScheduledWhenEnabled()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.getRouting().setOauth2RoutingEnabled(true);
        DataStoreConfiguration db = new DataStoreConfiguration("jdbc:postgresql://localhost:5432/mydb", "sa", "sa", "", true, 4, true);
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(db.getJdbcUrl(), "sa", "sa"), db, configuration)) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isTrue();
        }
    }

    @Test
    void testOauth2RoutingSweepNotScheduledWhenDisabled()
    {
        // With the feature off, nothing ever writes to oauth2_routing; the sweep must not be
        // scheduled at all, rather than run every 5 minutes against a table that -- with
        // runMigrationsEnabled=false -- may not even exist.
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.getRouting().setOauth2RoutingEnabled(false);
        DataStoreConfiguration db = new DataStoreConfiguration("jdbc:postgresql://localhost:5432/mydb", "sa", "sa", "", true, 4, true);
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(db.getJdbcUrl(), "sa", "sa"), db, configuration)) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isFalse();
        }
    }

    @Test
    void testConstructsFineWithUnknownJdbcPrefixWhenOauth2RoutingDisabled()
    {
        // An unrecognized JDBC URL prefix must never prevent startup when the feature that would
        // ever use it is off: the dialect is resolved lazily, only once the sweep is actually
        // scheduled, and it is never scheduled here.
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.getRouting().setOauth2RoutingEnabled(false);
        DataStoreConfiguration db = new DataStoreConfiguration("jdbc:h2:mem:test", "sa", "sa", "", true, 4, true);
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(db.getJdbcUrl(), "sa", "sa"), db, configuration)) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isFalse();
        }
    }

    @Test
    void testFailsAtStartupWithUnknownJdbcPrefixWhenOauth2RoutingEnabled()
    {
        // With the feature on, an unrecognized prefix must fail clearly at startup -- the same way
        // FlywayMigration.migrate does -- rather than only failing once the sweep first runs, 5
        // minutes later.
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.getRouting().setOauth2RoutingEnabled(true);
        DataStoreConfiguration db = new DataStoreConfiguration("jdbc:h2:mem:test", "sa", "sa", "", true, 4, true);
        assertThatThrownBy(() -> new JdbcConnectionManager(Jdbi.create(db.getJdbcUrl(), "sa", "sa"), db, configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JDBC URL: jdbc:h2:mem:test. Only PostgreSQL, MySQL, and Oracle are supported.");
    }

    private static JdbcConnectionManager createConnectionManager(String jdbcUrl)
    {
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "", true, 4, true);
        return new JdbcConnectionManager(Jdbi.create(jdbcUrl, "sa", "sa"), db);
    }
}
