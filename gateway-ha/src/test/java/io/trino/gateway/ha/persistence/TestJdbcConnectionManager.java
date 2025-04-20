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

import static org.assertj.core.api.Assertions.assertThat;

final class TestJdbcConnectionManager
{
    @Test
    void testBuildJdbcUrlWithH2AndNoRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:h2:/mydb";
        String expectedJdbcUrl = "jdbc:h2:/mydb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(null);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithH2AndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:h2:/mydb";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:h2:/newdb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithMySQLAndNoRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:mysql://localhost:3306/mydb";
        String expectedJdbcUrl = "jdbc:mysql://localhost:3306/mydb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(null);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithMySQLAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:mysql://localhost:3306/mydb";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:mysql://localhost:3306/newdb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithMySQLAndParametersAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Seoul";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:mysql://localhost:3306/newdb?useSSL=false&serverTimezone=Asia/Seoul";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndNoRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        String expectedJdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(null);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:postgresql://localhost:5432/newdb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithPostgreSQLAndParametersAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:postgresql://localhost:5432/mydb?ssl=false&serverTimezone=Asia/Seoul";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:postgresql://localhost:5432/newdb?ssl=false&serverTimezone=Asia/Seoul";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithOracleAndNoRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/mydb";
        String expectedJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/mydb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(null);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithOracleAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/mydb";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/newdb";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }

    @Test
    void testBuildJdbcUrlWithOracleAndParametersAndRoutingGroupDatabase()
    {
        String inputJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/mydb?sessionTimeZone=Asia/Seoul";
        String routingGroupDatabase = "newdb";
        String expectedJdbcUrl = "jdbc:oracle:thin:@//localhost:1521/newdb?sessionTimeZone=Asia/Seoul";
        DataStoreConfiguration db = new DataStoreConfiguration(inputJdbcUrl, "sa", "sa", "", 4, true);
        JdbcConnectionManager connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);

        String resultJdbcUrl = connectionManager.buildJdbcUrl(routingGroupDatabase);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }
}
