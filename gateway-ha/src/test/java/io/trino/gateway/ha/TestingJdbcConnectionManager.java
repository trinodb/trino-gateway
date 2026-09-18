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
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;

public final class TestingJdbcConnectionManager
{
    private TestingJdbcConnectionManager() {}

    public static PostgreSQLContainer createTestingPostgresContainer()
    {
        PostgreSQLContainer postgres = createPostgreSqlContainer()
                .withDatabaseName("testdb");
        postgres.start();
        return postgres;
    }

    public static DataStoreConfiguration dataStoreConfig(PostgreSQLContainer postgres)
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDriverClassName(),
                true,
                4,
                true);
        FlywayMigration.migrate(config);
        return config;
    }

    public static JdbcConnectionManager createTestingJdbcConnectionManager(DataStoreConfiguration config)
    {
        Jdbi jdbi = HaGatewayProviderModule.createJdbi(config);
        return new JdbcConnectionManager(jdbi, config);
    }
}
