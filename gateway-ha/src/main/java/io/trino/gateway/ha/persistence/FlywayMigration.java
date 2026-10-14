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

import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public class FlywayMigration
{
    private static final Logger log = Logger.get(FlywayMigration.class);

    private FlywayMigration() {}

    private static String getLocation(String configDbUrl)
    {
        if (configDbUrl.startsWith("jdbc:postgresql")) {
            return "postgresql";
        }
        if (configDbUrl.startsWith("jdbc:mysql")) {
            return "mysql";
        }
        if (configDbUrl.startsWith("jdbc:oracle")) {
            return "oracle";
        }
        throw new IllegalArgumentException("Invalid JDBC URL: %s. Only PostgreSQL, MySQL, and Oracle are supported.".formatted(configDbUrl));
    }

    public static void migrate(DataStoreConfiguration config)
    {
        if (!config.isRunMigrationsEnabled()) {
            log.info("Skip migrations as automatic migrations are disabled");
            return;
        }
        log.info("Performing migrations...");
        Flyway flyway = Flyway.configure()
                .dataSource(config.getJdbcUrl(), config.getUser(), config.getPassword())
                .locations(getLocation(config.getJdbcUrl()))
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        // V1 was changed after release to stop creating the resource group tables, so databases
        // created by Trino Gateway 18 or older recorded a different checksum. Repair updates the
        // recorded checksums instead of failing validation, and never re-runs an applied migration.
        // Do not change a released migration again, add a new one instead.
        flyway.repair();
        MigrateResult migrations = flyway.migrate();
        log.info("Performed %s migrations", migrations.migrationsExecuted);
    }
}
