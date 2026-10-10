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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.persistence.dao.OAuth2RoutingDao;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.Nullable;
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
        implements AutoCloseable
{
    private static final Logger log = Logger.get(JdbcConnectionManager.class);

    private static final int OAUTH2_ROUTING_SWEEP_BATCH_SIZE = 10_000;

    private final Jdbi jdbi;
    private final DataStoreConfiguration configuration;
    @Nullable
    private final JdbcUrlDialect oauth2RoutingDialect;
    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> cleanupTask;
    @Nullable
    private final ScheduledFuture<?> oauth2RoutingSweepTask;

    private HikariDataSource dataSource;

    @Inject
    public JdbcConnectionManager(DataStoreConfiguration configuration, HaGatewayConfiguration haGatewayConfiguration)
    {
        this(configuration, haGatewayConfiguration.getRouting().isOauth2RoutingEnabled());
    }

    @VisibleForTesting
    public JdbcConnectionManager(DataStoreConfiguration configuration)
    {
        this(configuration, true);
    }

    private JdbcConnectionManager(DataStoreConfiguration configuration, boolean oauth2RoutingEnabled)
    {
        this.configuration = requireNonNull(configuration, "configuration is null");
        // Only resolved when OAuth2 routing is enabled, so other JDBC URLs still work with migrations disabled
        oauth2RoutingDialect = oauth2RoutingEnabled ? JdbcUrlDialect.forJdbcUrl(configuration.getJdbcUrl()) : null;
        jdbi = Jdbi.create(configuration.getJdbcUrl(), configuration.getUser(), configuration.getPassword())
                .installPlugin(new SqlObjectPlugin())
                .registerRowMapper(new RecordAndAnnotatedConstructorMapper());
        cleanupTask = startCleanUps();
        oauth2RoutingSweepTask = oauth2RoutingEnabled ? startOAuth2RoutingSweep() : null;
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

    @VisibleForTesting
    boolean isOauth2RoutingSweepScheduled()
    {
        return oauth2RoutingSweepTask != null;
    }

    private ScheduledFuture<?> startCleanUps()
    {
        return executorService.scheduleWithFixedDelay(
                () -> {
                    // scheduleWithFixedDelay suppresses all future runs once a task throws. Resolving the
                    // Jdbi inside the try block keeps a failure to create the connection pool isolated too.
                    try {
                        log.info("Performing query history cleanup task");
                        long created = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(this.configuration.getQueryHistoryHoursRetention());
                        getJdbi().onDemand(QueryHistoryDao.class).deleteOldHistory(created);
                    }
                    catch (RuntimeException e) {
                        log.warn(e, "Query history cleanup failed; will retry on next run");
                    }
                },
                1,
                120,
                TimeUnit.MINUTES);
    }

    private ScheduledFuture<?> startOAuth2RoutingSweep()
    {
        return executorService.scheduleWithFixedDelay(
                () -> {
                    try {
                        sweepOAuth2Pins(OAUTH2_ROUTING_SWEEP_BATCH_SIZE);
                    }
                    catch (RuntimeException e) {
                        log.warn(e, "OAuth2 routing sweep failed; will retry on next run");
                    }
                },
                1,
                5,
                TimeUnit.MINUTES);
    }

    /**
     * Deletes expired pins in batches of at most {@code batchSize} rows, so a backlog is not removed in
     * one long transaction.
     */
    @VisibleForTesting
    int sweepOAuth2Pins(int batchSize)
    {
        long cutoff = System.currentTimeMillis() - this.configuration.getOauth2RoutingRetention().toMillis();
        OAuth2RoutingDao dao = getJdbi().onDemand(OAuth2RoutingDao.class);
        int totalDeleted = 0;
        int deletedInBatch;
        do {
            deletedInBatch = switch (requireNonNull(oauth2RoutingDialect, "OAuth2 routing is disabled")) {
                case MYSQL -> dao.deleteOldPinsBatchMysql(cutoff, batchSize);
                case POSTGRESQL -> dao.deleteOldPinsBatchPostgres(cutoff, batchSize);
                case ORACLE -> dao.deleteOldPinsBatchOracle(cutoff, batchSize);
            };
            totalDeleted += deletedInBatch;
        }
        while (deletedInBatch == batchSize);
        if (totalDeleted > 0) {
            log.info("OAuth2 routing sweep deleted %s expired pin(s)", totalDeleted);
        }
        return totalDeleted;
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
    @Override
    public synchronized void close()
    {
        cleanupTask.cancel(true);
        if (oauth2RoutingSweepTask != null) {
            oauth2RoutingSweepTask.cancel(true);
        }
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
