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

import org.jdbi.v3.core.Handle;
import org.testcontainers.mysql.MySQLContainer;

final class TestDatabaseMigrationsMySql
        extends BaseTestDatabaseMigrations
{
    public TestDatabaseMigrationsMySql()
    {
        super(new MySQLContainer("mysql:8.0.36"), "test");
    }

    @Override
    protected void createGatewaySchema()
    {
        String gatewayBackendTable = """
                CREATE TABLE gateway_backend (
                     name VARCHAR(256) PRIMARY KEY,
                     routing_group VARCHAR (256),
                     backend_url VARCHAR (256),
                     external_url VARCHAR (256),
                     active BOOLEAN
                );""";
        String queryHistoryTable = """
                CREATE TABLE query_history (
                     query_id VARCHAR(256) PRIMARY KEY,
                     query_text VARCHAR (256),
                     created bigint,
                     backend_url VARCHAR (256),
                     user_name VARCHAR(256),
                     source VARCHAR(256)
                );""";
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(gatewayBackendTable);
        jdbiHandle.execute(queryHistoryTable);
        jdbiHandle.close();
    }
}
