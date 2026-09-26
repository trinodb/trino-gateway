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

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.PreDestroy;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class JdbcConnectionManager
{
    private static final Logger log = Logger.get(JdbcConnectionManager.class);

    private final Jdbi jdbi;
    private final DataStoreConfiguration configuration;
    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> cleanupTask;

    private HikariDataSource dataSource;

    @Inject
    public JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration)
    {
        this.jdbi = requireNonNull(jdbi, "jdbi is null");
        this.configuration = requireNonNull(configuration, "configuration is null");
        cleanupTask = startCleanUps();
    }

    public Jdbi getJdbi()
    {
        Integer maxPoolSize = configuration.getMaxPoolSize();
        if (maxPoolSize == null) {
            return jdbi;
        }

        return Jdbi.create(getOrCreateDataSource(maxPoolSize))
                .installPlugin(new SqlObjectPlugin())
                .registerRowMapper(new RecordAndAnnotatedConstructorMapper());
    }

    private ScheduledFuture<?> startCleanUps()
    {
        return executorService.scheduleWithFixedDelay(
                () -> {
                    log.info("Performing query history cleanup task");
                    long created = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(this.configuration.getQueryHistoryHoursRetention());
                    getJdbi().onDemand(QueryHistoryDao.class).deleteOldHistory(created);
                },
                1,
                120,
                TimeUnit.MINUTES);
    }

    private synchronized HikariDataSource getOrCreateDataSource(int maxPoolSize)
    {
        checkArgument(maxPoolSize > 0, "maxPoolSize must be greater than 0");
        if (dataSource != null && !dataSource.isClosed()) {
            return dataSource;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(configuration.getJdbcUrl());
        hikariConfig.setUsername(configuration.getUser());
        hikariConfig.setPassword(configuration.getPassword());
        if (configuration.getDriver() != null) {
            hikariConfig.setDriverClassName(configuration.getDriver());
        }
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        if (configuration.getKeepaliveTime() != null) {
            hikariConfig.setKeepaliveTime(configuration.getKeepaliveTime().toMillis());
        }
        if (configuration.getMaxLifetime() != null) {
            hikariConfig.setMaxLifetime(configuration.getMaxLifetime().toMillis());
        }
        hikariConfig.setPoolName("trino-gateway");

        dataSource = new HikariDataSource(hikariConfig);
        return dataSource;
    }

    @PreDestroy
    public synchronized void close()
    {
        cleanupTask.cancel(true);
        executorService.shutdownNow();

        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            }
            catch (RuntimeException exception) {
                log.warn(exception, "Failed to close datasource");
            }
        }
        dataSource = null;
    }
}
