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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

final class TestJdbcConnectionManager
{
    @Test
    void testBuildJdbcUrlWithH2AndNoRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:h2:/mydb");
        assertThat(connectionManager.buildJdbcUrl(null)).isEqualTo("jdbc:h2:/mydb");
    }

    @Test
    void testBuildJdbcUrlWithH2AndRoutingGroupDatabase()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:h2:/mydb");
        assertThat(connectionManager.buildJdbcUrl("newdb")).isEqualTo("jdbc:h2:/newdb");
    }

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

        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create("jdbc:h2:/mydb", "sa", "sa"), dataStoreConfiguration);
        assertThatThrownBy(() -> connectionManager.buildJdbcUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JDBC URL cannot be null");
    }

    @Test
    void testBuildJdbcUrlWithNoSlashThrowsException()
    {
        JdbcConnectionManager connectionManager = createConnectionManager("jdbc:h2:mem:test");
        assertThatThrownBy(() -> connectionManager.buildJdbcUrl("newdb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JDBC URL: no '/' found in jdbc:h2:mem:test");
    }

    private static JdbcConnectionManager createConnectionManager(String jdbcUrl)
    {
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "", 4, true);
        return new JdbcConnectionManager(Jdbi.create(jdbcUrl, "sa", "sa"), db);
    }
}
