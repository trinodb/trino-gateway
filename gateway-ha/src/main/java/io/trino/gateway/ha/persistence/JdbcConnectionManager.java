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
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.Nullable;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class JdbcConnectionManager
{
    private static final Logger log = Logger.get(JdbcConnectionManager.class);

    private final Jdbi jdbi;
    private final DataStoreConfiguration configuration;
    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    public JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration)
    {
        this.jdbi = requireNonNull(jdbi, "jdbi is null")
                .installPlugin(new SqlObjectPlugin())
                .registerRowMapper(new RecordAndAnnotatedConstructorMapper());
        this.configuration = configuration;
        startCleanUps();
    }

    public Jdbi getJdbi()
    {
        return jdbi;
    }

    public Jdbi getJdbi(@Nullable String routingGroupDatabase)
    {
        if (routingGroupDatabase == null) {
            return jdbi;
        }

        return Jdbi.create(buildJdbcUrl(routingGroupDatabase), configuration.getUser(), configuration.getPassword())
                .installPlugin(new SqlObjectPlugin())
                .registerRowMapper(new RecordAndAnnotatedConstructorMapper());
    }

    private String buildJdbcUrl(@Nullable String routingGroupDatabase)
    {
        String jdbcUrl = configuration.getJdbcUrl();
        if (routingGroupDatabase != null) {
            jdbcUrl = jdbcUrl.substring(0, jdbcUrl.lastIndexOf('/') + 1) + routingGroupDatabase;
        }
        return jdbcUrl;
    }

    private void startCleanUps()
    {
        executorService.scheduleWithFixedDelay(
                () -> {
                    log.info("Performing query history cleanup task");
                    long created = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(this.configuration.getQueryHistoryHoursRetention());
                    jdbi.onDemand(QueryHistoryDao.class).deleteOldHistory(created);
                },
                1,
                120,
                TimeUnit.MINUTES);
    }
}
