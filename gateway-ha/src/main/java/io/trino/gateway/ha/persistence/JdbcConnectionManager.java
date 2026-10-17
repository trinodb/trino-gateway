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
import io.airlift.units.Duration;
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
    private static final int OAUTH2_ROUTING_SWEEP_MAX_BATCHES = 100;
    // Default of http-server.authentication.oauth2.challenge-timeout in Trino
    private static final Duration TRINO_OAUTH2_CHALLENGE_TIMEOUT = new Duration(15, TimeUnit.MINUTES);

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
        this(configuration, false);
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
        Duration retention = configuration.getOauth2RoutingRetention();
        if (retention.compareTo(TRINO_OAUTH2_CHALLENGE_TIMEOUT) <= 0) {
            log.warn("dataStore.oauth2RoutingRetention %s is not above the default Trino OAuth2 challenge timeout of %s, so a pin can be removed while its login is in progress",
                    retention,
                    TRINO_OAUTH2_CHALLENGE_TIMEOUT);
        }
        return executorService.scheduleWithFixedDelay(
                () -> {
                    try {
                        long cutoff = System.currentTimeMillis() - configuration.getOauth2RoutingRetention().toMillis();
                        sweepOAuth2Pins(cutoff, OAUTH2_ROUTING_SWEEP_BATCH_SIZE, OAUTH2_ROUTING_SWEEP_MAX_BATCHES);
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
     * Deletes pins created before {@code cutoff} in batches of at most {@code batchSize} rows, so a
     * backlog is not removed in one long transaction. Stops after {@code maxBatches} batches and leaves
     * the rest for the next run, so the sweep does not hold the executor shared with the query history
     * cleanup.
     */
    @VisibleForTesting
    int sweepOAuth2Pins(long cutoff, int batchSize, int maxBatches)
    {
        OAuth2RoutingDao dao = getJdbi().onDemand(OAuth2RoutingDao.class);
        int totalDeleted = 0;
        int batches = 0;
        int deletedInBatch;
        do {
            deletedInBatch = switch (requireNonNull(oauth2RoutingDialect, "OAuth2 routing is disabled")) {
                case MYSQL -> dao.deleteOldPinsBatchMysql(cutoff, batchSize);
                case POSTGRESQL -> dao.deleteOldPinsBatchPostgres(cutoff, batchSize);
                case ORACLE -> dao.deleteOldPinsBatchOracle(cutoff, batchSize);
            };
            totalDeleted += deletedInBatch;
            batches++;
        }
        while (deletedInBatch == batchSize && batches < maxBatches);
        if (totalDeleted > 0) {
            log.info("OAuth2 routing sweep deleted %s expired pin(s)", totalDeleted);
        }
        if (batches == maxBatches && deletedInBatch == batchSize) {
            log.warn("OAuth2 routing sweep stopped after %s batches, expired pins are left for the next run", batches);
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
