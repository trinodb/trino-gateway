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

import java.io.File;
import java.nio.file.Path;

public final class TestingJdbcConnectionManager
{
    private TestingJdbcConnectionManager() {}

    public static DataStoreConfiguration dataStoreConfig()
    {
        File tempH2DbDir = Path.of(System.getProperty("java.io.tmpdir"), "h2db-" + System.currentTimeMillis()).toFile();
        tempH2DbDir.deleteOnExit();
        String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath() + ";NON_KEYWORDS=NAME,VALUE";
        HaGatewayTestUtils.seedRequiredData(tempH2DbDir.getAbsolutePath());
        return new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4, false);
    }

    public static JdbcConnectionManager createTestingJdbcConnectionManager(DataStoreConfiguration config)
    {
        Jdbi jdbi = HaGatewayProviderModule.createJdbi(config);
        return new JdbcConnectionManager(jdbi, config);
    }
}
