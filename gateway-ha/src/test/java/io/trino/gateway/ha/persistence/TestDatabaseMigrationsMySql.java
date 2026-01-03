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
import org.testcontainers.containers.MySQLContainer;

final class TestDatabaseMigrationsMySql
        extends BaseTestDatabaseMigrations
{
    public TestDatabaseMigrationsMySql()
    {
        super(new MySQLContainer<>("mysql:8.0.36"), "test");
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
        String propertiesTable = """
                CREATE TABLE resource_groups_global_properties (
                    name VARCHAR(128) NOT NULL PRIMARY KEY,
                    value VARCHAR(512) NULL,
                    CHECK (name in ('cpu_quota_period', 'a_name', 'a_value'))
                );""";
        String resourceGroupsTable = """
                CREATE TABLE resource_groups (
                    resource_group_id BIGINT NOT NULL AUTO_INCREMENT,
                    name VARCHAR(250) NOT NULL,
                    soft_memory_limit VARCHAR(128) NOT NULL,
                    max_queued INT NOT NULL,
                    soft_concurrency_limit INT NULL,
                    hard_concurrency_limit INT NOT NULL,
                    scheduling_policy VARCHAR(128) NULL,
                    scheduling_weight INT NULL,
                    jmx_export BOOLEAN NULL,
                    soft_cpu_limit VARCHAR(128) NULL,
                    hard_cpu_limit VARCHAR(128) NULL,
                    parent BIGINT NULL,
                    environment VARCHAR(128) NULL,
                    PRIMARY KEY (resource_group_id),
                    FOREIGN KEY (parent) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE
                );""";
        String selectorsTable = """
                CREATE TABLE selectors (
                     resource_group_id BIGINT NOT NULL,
                     priority BIGINT NOT NULL,
                     user_regex VARCHAR(512),
                     source_regex VARCHAR(512),
                     query_type VARCHAR(512),
                     client_tags VARCHAR(512),
                     selector_resource_estimate VARCHAR(1024),
                     FOREIGN KEY (resource_group_id) REFERENCES resource_groups (resource_group_id) ON DELETE CASCADE
                );""";
        String exactMatchSourceSelectorsTable = """
                CREATE TABLE exact_match_source_selectors (
                    resource_group_id VARCHAR(256) NOT NULL,
                    update_time DATETIME NOT NULL,
                    source VARCHAR(512) NOT NULL,
                    environment VARCHAR(128),
                    query_type VARCHAR(512),
                    PRIMARY KEY (environment, source(128), query_type),
                    UNIQUE (source(128), environment, query_type(128), resource_group_id)
                );""";
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
