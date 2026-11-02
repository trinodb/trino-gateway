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

import com.google.common.collect.ImmutableList;
import org.jdbi.v3.core.Handle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.oracle.OracleContainer;

import java.util.List;

import static io.trino.gateway.ha.HaGatewayTestUtils.getOracleContainer;

// Oracle support arrived together with Flyway, so there is no pre-Flyway schema to migrate from
final class TestDatabaseMigrationsOracle
        extends BaseTestDatabaseMigrations
{
    @Container
    private static final OracleContainer ORACLE = getOracleContainer();

    public TestDatabaseMigrationsOracle()
    {
        super(ORACLE, "TEST");
    }

    @Override
    protected void dropAllTables()
    {
        /*
         * Flyway configuration items including table names are case-sensitive.
         * For this reason, if you remove the double quotes on flyway_schema_history,
         * you will get a table not found error.
         */
        List<String> tables = ImmutableList.of("gateway_backend", "query_history", "oauth2_routing", "gateway_audit_logs", "\"flyway_schema_history\"");
        Handle jdbiHandle = jdbi.open();
        String sql = "SELECT 1 FROM all_tables WHERE owner = '%s'".formatted(schema);
        verifyResultSetCount(sql, 5);
        tables.forEach(table -> jdbiHandle.execute("DROP TABLE " + table));
        verifyResultSetCount(sql, 0);
        jdbiHandle.close();
    }
}
