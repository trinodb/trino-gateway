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
import org.testcontainers.containers.PostgreSQLContainer;

final class TestDatabaseMigrationsPostgreSql
        extends BaseTestDatabaseMigrations
{
    public TestDatabaseMigrationsPostgreSql()
    {
        super(new PostgreSQLContainer<>("postgres:11"), "public");
    }

    @Override
    protected void createGatewaySchema()
    {
        String gatewayBackendTable = "CREATE TABLE gateway_backend (\n" +
                "     name VARCHAR(256) PRIMARY KEY,\n" +
                "     routing_group VARCHAR (256),\n" +
                "     backend_url VARCHAR (256),\n" +
                "     external_url VARCHAR (256),\n" +
                "     active BOOLEAN\n" +
                ");";
        String queryHistoryTable = "CREATE TABLE query_history (\n" +
                "     query_id VARCHAR(256) PRIMARY KEY,\n" +
                "     query_text VARCHAR (256),\n" +
                "     created bigint,\n" +
                "     backend_url VARCHAR (256),\n" +
                "     user_name VARCHAR(256),\n" +
                "     source VARCHAR(256)\n" +
                ");";
        String propertiesTable = "CREATE TABLE resource_groups_global_properties (\n" +
                "    name VARCHAR(128) NOT NULL PRIMARY KEY,\n" +
                "    value VARCHAR(512) NULL,\n" +
                "    CHECK (name in ('cpu_quota_period', 'a_name', 'a_value'))\n" +
                ");";
        String resourceGroupsTable = "CREATE TABLE resource_groups (\n" +
                "    resource_group_id SERIAL,\n" +
                "    name VARCHAR(250) NOT NULL,\n" +
                "    soft_memory_limit VARCHAR(128) NOT NULL,\n" +
                "    max_queued INT NOT NULL,\n" +
                "    soft_concurrency_limit INT NULL,\n" +
                "    hard_concurrency_limit INT NOT NULL,\n" +
                "    scheduling_policy VARCHAR(128) NULL,\n" +
                "    scheduling_weight INT NULL,\n" +
                "    jmx_export BOOLEAN NULL,\n" +
                "    soft_cpu_limit VARCHAR(128) NULL,\n" +
                "    hard_cpu_limit VARCHAR(128) NULL,\n" +
                "    parent BIGINT NULL,\n" +
                "    environment VARCHAR(128) NULL,\n" +
                "    PRIMARY KEY (resource_group_id),\n" +
                "    FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE\n" +
                ");";
        String selectorsTable = "CREATE TABLE selectors (\n" +
                "     resource_group_id BIGINT NOT NULL,\n" +
                "     priority BIGINT NOT NULL,\n" +
                "     user_regex VARCHAR(512),\n" +
                "     source_regex VARCHAR(512),\n" +
                "     query_type VARCHAR(512),\n" +
                "     client_tags VARCHAR(512),\n" +
                "     selector_resource_estimate VARCHAR(1024),\n" +
                "     FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE\n" +
                ");";
        String exactMatchSourceSelectorsTable = "CREATE TABLE exact_match_source_selectors (\n" +
                "    resource_group_id VARCHAR(256) NOT NULL,\n" +
                "    update_time TIMESTAMP NOT NULL,\n" +
                "    source VARCHAR(512) NOT NULL,\n" +
                "    environment VARCHAR(128),\n" +
                "    query_type VARCHAR(512),\n" +
                "    PRIMARY KEY (environment, source, query_type),\n" +
                "    UNIQUE (source, environment, query_type, resource_group_id)\n" +
                ");";
        Handle jdbiHandle = jdbi.open();
        jdbiHandle.execute(gatewayBackendTable);
        jdbiHandle.execute(queryHistoryTable);
        jdbiHandle.execute(propertiesTable);
        jdbiHandle.execute(resourceGroupsTable);
        jdbiHandle.execute(selectorsTable);
        jdbiHandle.execute(exactMatchSourceSelectorsTable);
        jdbiHandle.close();
    }
}
