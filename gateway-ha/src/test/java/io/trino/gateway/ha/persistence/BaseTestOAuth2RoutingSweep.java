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
 * Runs {@link JdbcConnectionManager#sweepOAuth2Pins(int)} against a real database, since each dialect has its own delete-batch SQL.
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
                container.getJdbcUrl(), container.getUsername(), container.getPassword(), container.getDriverClassName(), true, 4, true, null);
        dataStoreConfig.setOauth2RoutingRetention("1m");
        FlywayMigration.migrate(dataStoreConfig);
        connectionManager = new JdbcConnectionManager(dataStoreConfig);
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
        long expired = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        IntStream.range(0, 7).forEach(i -> dao.insert(pinKey("expired-" + i), "http://coord:8080", expired));
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
     * Returns a 64-char sha256 hex key, as stored in {@code pin_key} ({@code CHAR(64)}). A short literal
     * is blank-padded on Oracle and then never matches a {@code VARCHAR2} bind variable on lookup.
     */
    protected static String pinKey(String seed)
    {
        return Hashing.sha256().hashString(seed, StandardCharsets.UTF_8).toString();
    }
}
