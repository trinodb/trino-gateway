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
package io.trino.gateway.ha;

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.module.HaGatewayProviderModule;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class TestingJdbcConnectionManager
{
    private TestingJdbcConnectionManager() {}

    public static JdbcConnectionManager createTestingJdbcConnectionManager()
    {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:14-alpine")
                .withDatabaseName("testdb")
                .withInitScript("gateway-ha-persistence-postgres.sql");
        postgres.start();

        DataStoreConfiguration db = new DataStoreConfiguration(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDriverClassName(),
                4,
                false);
        Jdbi jdbi = HaGatewayProviderModule.createJdbi(db);

        return new JdbcConnectionManager(jdbi, db);
    }

    public static DataStoreConfiguration dataStoreConfig()
    {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:14-alpine")
                .withDatabaseName("testdb")
                .withInitScript("gateway-ha-persistence-postgres.sql");
        postgres.start();

        return new DataStoreConfiguration(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDriverClassName(),
                4,
                false);
    }

    public static JdbcConnectionManager createTestingJdbcConnectionManager(DataStoreConfiguration config)
    {
        Jdbi jdbi = HaGatewayProviderModule.createJdbi(config);
        return new JdbcConnectionManager(jdbi, config);
    }

    public static JdbcConnectionManager createTestingJdbcConnectionManager(JdbcDatabaseContainer<?> container, DataStoreConfiguration config)
    {
        Jdbi jdbi = Jdbi.create(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return new JdbcConnectionManager(jdbi, config);
    }
}
