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
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.persistence.dao.OAuth2RoutingDao;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class JdbcConnectionManager
        implements AutoCloseable
{
    private static final Logger log = Logger.get(JdbcConnectionManager.class);

    // Deletes at most this many expired pins per DELETE statement; the sweep loops (re-running the
    // batch delete) until a batch comes back smaller than this, so an unbounded backlog (e.g. after
    // the sweep was down for a while) cannot hold one enormous transaction/lock for a long time.
    private static final int OAUTH2_ROUTING_SWEEP_BATCH_SIZE = 10_000;

    private final Jdbi jdbi;
    private final DataStoreConfiguration configuration;
    private final boolean oauth2RoutingEnabled;
    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    // Resolved lazily, only if/when the OAuth2 routing sweep is actually scheduled (see
    // startCleanUps): with the feature off, an unrecognized JDBC URL prefix must never prevent
    // startup, since nothing would ever use this. Null until then.
    @Nullable
    private JdbcUrlDialect oauth2RoutingDialect;

    // Non-null only once the OAuth2 routing sweep has actually been scheduled (see startCleanUps);
    // isOauth2RoutingSweepScheduled() asserts on this directly, rather than echoing back the
    // constructor's oauth2RoutingEnabled argument, so a test of the gating logic can actually fail if
    // that logic breaks.
    @Nullable
    private ScheduledFuture<?> oauth2RoutingSweepFuture;

    @Inject
    public JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration, HaGatewayConfiguration haGatewayConfiguration)
    {
        this(jdbi, configuration, haGatewayConfiguration.getRouting().isOauth2RoutingEnabled());
    }

    /**
     * For callers that only care about non-OAuth2 behavior (most existing tests): schedules the
     * OAuth2 sweep unconditionally, matching this class's behavior before the sweep became
     * conditional on {@code routing.oauth2RoutingEnabled}.
     */
    @VisibleForTesting
    public JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration)
    {
        this(jdbi, configuration, true);
    }

    private JdbcConnectionManager(Jdbi jdbi, DataStoreConfiguration configuration, boolean oauth2RoutingEnabled)
    {
        this.jdbi = requireNonNull(jdbi, "jdbi is null");
        this.configuration = configuration;
        this.oauth2RoutingEnabled = oauth2RoutingEnabled;
        startCleanUps();
    }

    public Jdbi getJdbi()
    {
        return jdbi;
    }

    @VisibleForTesting
    boolean isOauth2RoutingSweepScheduled()
    {
        return oauth2RoutingSweepFuture != null;
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

    @VisibleForTesting
    String buildJdbcUrl(@Nullable String routingGroupDatabase)
    {
        String jdbcUrl = configuration.getJdbcUrl();
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("JDBC URL cannot be null");
        }
        if (routingGroupDatabase == null) {
            return jdbcUrl;
        }
        try {
            int index = jdbcUrl.indexOf("/") + 1;
            if (index == 0) {
                throw new IllegalArgumentException("Invalid JDBC URL: no '/' found in " + jdbcUrl);
            }

            URI newUri = getUriWithRoutingGroupDatabase(routingGroupDatabase, index, jdbcUrl);
            return jdbcUrl.substring(0, index) + newUri;
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static URI getUriWithRoutingGroupDatabase(String routingGroupDatabase, int index, String jdbcUrl)
            throws URISyntaxException
    {
        URI uri = new URI(jdbcUrl.substring(index));
        return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                Path.of(uri.getPath()).resolveSibling(routingGroupDatabase).toString(),
                uri.getQuery(),
                uri.getFragment());
    }

    private void startCleanUps()
    {
        executorService.scheduleWithFixedDelay(
                () -> {
                    // Isolated in its own try-catch: scheduleWithFixedDelay silently suppresses all
                    // future runs once a task throws, so a failure here (e.g. a table not yet created
                    // during a rolling deploy) must not propagate out of the scheduled task.
                    try {
                        log.info("Performing query history cleanup task");
                        long created = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(this.configuration.getQueryHistoryHoursRetention());
                        jdbi.onDemand(QueryHistoryDao.class).deleteOldHistory(created);
                    }
                    catch (RuntimeException e) {
                        log.warn(e, "Query history cleanup failed; will retry on next run");
                    }
                },
                1,
                120,
                TimeUnit.MINUTES);

        if (!oauth2RoutingEnabled) {
            // Nothing ever writes to oauth2_routing with the feature off; skip scheduling the sweep
            // entirely rather than have it run every 5 minutes against a table that (with
            // runMigrationsEnabled=false) may not even exist, logging a spurious warning each time.
            // In particular, do not resolve oauth2RoutingDialect below: an unrecognized JDBC URL
            // prefix must never prevent startup for a gateway that never enabled this feature.
            return;
        }

        // Resolved here, not eagerly in the constructor: only reachable once the feature is on and
        // the sweep is about to be scheduled, so an unrecognized prefix still fails clearly at
        // startup (like FlywayMigration.migrate) when the feature is on, but never even runs -- let
        // alone throws -- when it is off.
        this.oauth2RoutingDialect = JdbcUrlDialect.forJdbcUrl(configuration.getJdbcUrl());

        // The OAuth2 pin table gets its own, much shorter-cadence sweep instead of piggy-backing on
        // the 2-hour query-history cleanup above: pins are short-lived (retention is minutes, see
        // DataStoreConfiguration.oauth2RoutingRetention) and, being written from an unauthenticated
        // code path (a proxied 401 challenge), are the more likely of the two tables to accumulate a
        // backlog that should not be left sitting for up to 2 hours.
        oauth2RoutingSweepFuture = executorService.scheduleWithFixedDelay(
                () -> {
                    try {
                        sweepOAuth2Pins();
                    }
                    catch (RuntimeException e) {
                        log.warn(e, "OAuth2 routing sweep failed; will retry on next run");
                    }
                },
                1,
                5,
                TimeUnit.MINUTES);
    }

    private void sweepOAuth2Pins()
    {
        sweepOAuth2Pins(OAUTH2_ROUTING_SWEEP_BATCH_SIZE);
    }

    /**
     * Deletes pins older than the configured retention, one batch of at most {@code batchSize} rows
     * per statement, looping until a batch comes back short of {@code batchSize} (meaning nothing
     * older than the cutoff is left). Package-private with a caller-supplied batch size purely so the
     * loop can be exercised with a small batch size in tests without seeding tens of thousands of rows;
     * the real sweep always calls the no-arg overload above, which fixes the batch size at
     * {@value #OAUTH2_ROUTING_SWEEP_BATCH_SIZE}.
     */
    @VisibleForTesting
    int sweepOAuth2Pins(int batchSize)
    {
        long cutoff = System.currentTimeMillis() - this.configuration.getOauth2RoutingRetention().toMillis();
        OAuth2RoutingDao dao = jdbi.onDemand(OAuth2RoutingDao.class);
        int totalDeleted = 0;
        int deletedInBatch;
        do {
            deletedInBatch = switch (oauth2RoutingDialect) {
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

    @PreDestroy
    @Override
    public void close()
    {
        executorService.shutdownNow();
    }
}
