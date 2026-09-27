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

import com.google.common.hash.Hashing;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.module.HaGatewayProviderModule;
import io.trino.gateway.ha.persistence.dao.OAuth2RoutingDao;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Exercises {@link JdbcConnectionManager#sweepOAuth2Pins(int)} directly (bypassing the 5-minute
 * schedule) against a real database container, one dialect per subclass: {@code deleteOldPinsBatchMysql}
 * ({@code DELETE ... LIMIT}, MySQL-only syntax), {@code deleteOldPinsBatchPostgres} ({@code LIMIT}
 * via a sub-select), and {@code deleteOldPinsBatchOracle} ({@code ROWNUM <=}, since Oracle has neither)
 * each need their own live verification. A small batch size stands in for the real
 * {@code OAUTH2_ROUTING_SWEEP_BATCH_SIZE} (10,000) so the multi-batch loop can be exercised without
 * seeding tens of thousands of rows.
 */
@TestInstance(PER_CLASS)
public abstract class BaseTestOAuth2RoutingSweep
{
    private final JdbcDatabaseContainer<?> container;
    private JdbcConnectionManager connectionManager;
    private OAuth2RoutingDao dao;

    protected BaseTestOAuth2RoutingSweep(JdbcDatabaseContainer<?> container)
    {
        this.container = requireNonNull(container, "container is null");
        this.container.start();
    }

    @BeforeAll
    final void setUp()
    {
        DataStoreConfiguration dataStoreConfig = new DataStoreConfiguration(
                container.getJdbcUrl(), container.getUsername(), container.getPassword(), container.getDriverClassName(), true, 4, true);
        dataStoreConfig.setOauth2RoutingRetention("1m");
        FlywayMigration.migrate(dataStoreConfig);
        connectionManager = new JdbcConnectionManager(HaGatewayProviderModule.createJdbi(dataStoreConfig), dataStoreConfig);
        dao = connectionManager.getJdbi().onDemand(OAuth2RoutingDao.class);
    }

    @AfterEach
    final void clearTable()
    {
        connectionManager.getJdbi().useHandle(handle -> handle.execute("DELETE FROM oauth2_routing"));
    }

    @AfterAll
    final void tearDown()
    {
        if (connectionManager != null) {
            connectionManager.close();
        }
        container.close();
    }

    @Test
    void testSweepDeletesOnlyRowsOlderThanCutoff()
    {
        long now = System.currentTimeMillis();
        dao.insert(pinKey("old"), "http://coord-old:8080", now - Duration.ofMinutes(2).toMillis());
        dao.insert(pinKey("fresh"), "http://coord-fresh:8080", now);

        int deleted = connectionManager.sweepOAuth2Pins(10_000);

        assertThat(deleted).isEqualTo(1);
        assertThat(dao.findBackendByPinKey(pinKey("old"))).isNull();
        assertThat(dao.findBackendByPinKey(pinKey("fresh"))).isEqualTo("http://coord-fresh:8080");
    }

    @Test
    void testSweepLoopsUntilBatchComesBackShort()
    {
        // 7 expired rows at batch size 3: the loop must run 3 batches (3, 3, 1) rather than stopping
        // after the first full batch, and delete all 7.
        long expired = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        IntStream.range(0, 7).forEach(i -> dao.insert(pinKey("expired-" + i), "http://coord:8080", expired));
        // One row that must survive: not older than the cutoff.
        dao.insert(pinKey("survivor"), "http://coord:8080", System.currentTimeMillis());

        int deleted = connectionManager.sweepOAuth2Pins(3);

        assertThat(deleted).isEqualTo(7);
        IntStream.range(0, 7).forEach(i -> assertThat(dao.findBackendByPinKey(pinKey("expired-" + i))).isNull());
        assertThat(dao.findBackendByPinKey(pinKey("survivor"))).isEqualTo("http://coord:8080");
    }

    @Test
    void testSweepIsNoopWhenNothingExpired()
    {
        dao.insert(pinKey("fresh"), "http://coord:8080", System.currentTimeMillis());

        assertThat(connectionManager.sweepOAuth2Pins(3)).isEqualTo(0);
        assertThat(dao.findBackendByPinKey(pinKey("fresh"))).isEqualTo("http://coord:8080");
    }

    /**
     * A stand-in for a real 64-char sha256-hex pin key (see {@code OAuth2RoutingUtils.pinKeyForAuthIdHash}),
     * deterministic per {@code seed}. {@code pin_key} is {@code CHAR(64)}: on some dialects (notably
     * Oracle, where equality between a {@code CHAR} column and a shorter {@code VARCHAR2} bind variable
     * uses nonpadded comparison semantics) a short literal like {@code "pin-old"} would be blank-padded
     * on storage but never match on lookup, which every real pin key -- always exactly 64 characters --
     * never hits.
     */
    protected static String pinKey(String seed)
    {
        return Hashing.sha256().hashString(seed, StandardCharsets.UTF_8).toString();
    }
}
