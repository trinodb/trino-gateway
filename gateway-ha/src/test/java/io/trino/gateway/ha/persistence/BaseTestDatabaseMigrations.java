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
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
@Isolated
public abstract class BaseTestDatabaseMigrations
{
    protected final JdbcDatabaseContainer<?> container = startContainer();
    protected final Jdbi jdbi = Jdbi.create(container.getJdbcUrl(), container.getUsername(), container.getPassword());

    protected abstract JdbcDatabaseContainer<?> startContainer();

    protected abstract String getDriver();

    protected abstract void createGatewaySchema();

    @AfterAll
    public final void close()
    {
        container.close();
    }

    @Test
    public void testMigrationWithEmptyDatabase()
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                getDriver(),
                4,
                true);
        FlywayMigration.migrate(config);
        verifyGatewaySchema(0);

        dropAllTables();
    }

    @Test
    public void testMigrationWithNonemptyDatabase()
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                getDriver(),
                4,
                true);
        String t1Create = "CREATE TABLE t1 (id INT)";
        String t2Create = "CREATE TABLE t2 (id INT)";
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(t1Create);
        jdbiHandle.execute(t2Create);
        FlywayMigration.migrate(config);
        verifyGatewaySchema(0);
        String t1Drop = "DROP TABLE t1";
        String t2Drop = "DROP TABLE t2";
        jdbiHandle.execute(t1Drop);
        jdbiHandle.execute(t2Drop);
        jdbiHandle.close();

        dropAllTables();
    }

    @Test
    public void testMigrationWithExistingGatewaySchema()
    {
        createGatewaySchema();
        // add a row to one of the existing tables before migration
        jdbi.withHandle(handle ->
                handle.execute("INSERT INTO resource_groups_global_properties VALUES ('a_name', 'a_value')"));
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                getDriver(),
                4,
                true);
        FlywayMigration.migrate(config);
        verifyGatewaySchema(1);
        dropAllTables();
    }

    protected void verifyGatewaySchema(int expectedPropertiesCount)
    {
        verifyResultSetCount("SELECT name FROM gateway_backend", 0);
        verifyResultSetCount("SELECT query_id FROM query_history", 0);
        verifyResultSetCount("SELECT name FROM resource_groups_global_properties", expectedPropertiesCount);
        verifyResultSetCount("SELECT name FROM resource_groups", 0);
        verifyResultSetCount("SELECT user_regex FROM selectors", 0);
        verifyResultSetCount("SELECT environment FROM exact_match_source_selectors", 0);
    }

    private void verifyResultSetCount(String sql, int expectedCount)
    {
        List<String> results = jdbi.withHandle(handle ->
                handle.createQuery(sql).mapTo(String.class).list());
        assertThat(results).hasSize(expectedCount);
    }

    protected void dropAllTables()
    {
        String gatewayBackendTable = "DROP TABLE IF EXISTS gateway_backend";
        String queryHistoryTable = "DROP TABLE IF EXISTS query_history";
        String propertiesTable = "DROP TABLE IF EXISTS resource_groups_global_properties";
        String resourceGroupsTable = "DROP TABLE IF EXISTS resource_groups";
        String selectorsTable = "DROP TABLE IF EXISTS selectors";
        String exactMatchTable = "DROP TABLE IF EXISTS exact_match_source_selectors";
        String flywayHistoryTable = "DROP TABLE IF EXISTS flyway_schema_history";
        Handle jdbiHandle = jdbi.open();
        String sql = String.format("SELECT 1 FROM information_schema.tables WHERE table_schema = '%s'", getTestSchema());
        verifyResultSetCount(sql, 7);
        jdbiHandle.execute(gatewayBackendTable);
        jdbiHandle.execute(queryHistoryTable);
        jdbiHandle.execute(propertiesTable);
        jdbiHandle.execute(selectorsTable);
        jdbiHandle.execute(resourceGroupsTable);
        jdbiHandle.execute(exactMatchTable);
        jdbiHandle.execute(flywayHistoryTable);
        verifyResultSetCount(sql, 0);
        jdbiHandle.close();
    }

    protected String getTestSchema()
    {
        return "public";
    }
}
