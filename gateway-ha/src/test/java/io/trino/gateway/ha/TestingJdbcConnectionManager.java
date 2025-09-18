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
import io.trino.gateway.ha.persistence.DefaultJdbcPropertiesProvider;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.File;
import java.nio.file.Path;

public final class TestingJdbcConnectionManager
{
    private TestingJdbcConnectionManager() {}

    public static JdbcConnectionManager createTestingJdbcConnectionManager()
    {
        File tempH2DbDir = Path.of(System.getProperty("java.io.tmpdir"), "h2db-" + System.currentTimeMillis()).toFile();
        tempH2DbDir.deleteOnExit();
        String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath();
        HaGatewayTestUtils.seedRequiredData(tempH2DbDir.getAbsolutePath());
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4, false);
        Jdbi jdbi = Jdbi.create(jdbcUrl, "sa", "sa");
        return new JdbcConnectionManager(jdbi, db, new DefaultJdbcPropertiesProvider());
    }

    public static JdbcConnectionManager createTestingJdbcConnectionManager(JdbcDatabaseContainer<?> container, DataStoreConfiguration config)
    {
        Jdbi jdbi = Jdbi.create(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return new JdbcConnectionManager(jdbi, config, new DefaultJdbcPropertiesProvider());
    }
}
