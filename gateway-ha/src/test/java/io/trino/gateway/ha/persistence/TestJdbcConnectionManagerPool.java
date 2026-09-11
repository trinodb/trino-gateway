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

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.DatabaseCacheConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestJdbcConnectionManagerPool
{
    @TempDir
    private Path temporaryDirectory;

    @Test
    void testRoutingGroupJdbiReusesPoolAndBlocksWhenExceedingMaxPoolSize()
            throws Exception
    {
        DataStoreConfiguration configuration = createConfiguration("routing-group", 2);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);

        try {
            assertThirdConnectionBlocks(
                    connectionManager.getJdbi("routing-group-database"),
                    connectionManager.getJdbi("routing-group-database"));
        }
        finally {
            connectionManager.close();
        }
    }

    @Test
    void testDefaultJdbiReusesPoolAndBlocksWhenExceedingMaxPoolSize()
            throws Exception
    {
        DataStoreConfiguration configuration = createConfiguration("default", 2);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);

        try {
            assertThirdConnectionBlocks(connectionManager.getJdbi(), connectionManager.getJdbi());
        }
        finally {
            connectionManager.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testRejectsInvalidMaxPoolSize(int maxPoolSize)
    {
        DataStoreConfiguration configuration = createConfiguration("invalid-" + maxPoolSize, maxPoolSize);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);

        try {
            assertThatThrownBy(connectionManager::getJdbi)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("maxPoolSize must be greater than 0");
        }
        finally {
            connectionManager.close();
        }
    }

    @Test
    void testHaQueryHistoryManagerUsesDefaultPool()
            throws Exception
    {
        DataStoreConfiguration configuration = createConfiguration("query-history", 1);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);
        Jdbi jdbi = connectionManager.getJdbi();
        jdbi.useHandle(handle -> handle.execute(
                """
                CREATE TABLE query_history (
                    query_id VARCHAR NOT NULL,
                    query_text VARCHAR NOT NULL,
                    backend_url VARCHAR NOT NULL,
                    user_name VARCHAR,
                    source VARCHAR,
                    created BIGINT,
                    routing_group VARCHAR,
                    external_url VARCHAR
                )
                """));

        QueryHistoryManager queryHistoryManager = new HaQueryHistoryManager(connectionManager, configuration);
        try (ExecutorService executorService = Executors.newSingleThreadExecutor();
                Handle heldHandle = jdbi.open()) {
            Future<List<QueryHistoryManager.QueryDetail>> queryHistory = executorService.submit(() -> queryHistoryManager.fetchQueryHistory(Optional.empty()));

            assertThatThrownBy(() -> queryHistory.get(200, MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            heldHandle.close();
            assertThat(queryHistory.get(3, SECONDS)).isEmpty();
        }
        finally {
            connectionManager.close();
        }
    }

    @Test
    void testHaGatewayManagerUsesDefaultPool()
            throws Exception
    {
        DataStoreConfiguration configuration = createConfiguration("gateway-manager", 1);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);
        Jdbi jdbi = connectionManager.getJdbi();
        jdbi.useHandle(handle -> handle.execute(
                """
                CREATE TABLE gateway_backend (
                    name VARCHAR PRIMARY KEY,
                    routing_group VARCHAR,
                    backend_url VARCHAR,
                    external_url VARCHAR,
                    active BOOLEAN
                )
                """));

        try (ExecutorService executorService = Executors.newSingleThreadExecutor();
                Handle heldHandle = jdbi.open()) {
            Future<HaGatewayManager> gatewayManager = executorService.submit(() -> new HaGatewayManager(
                    connectionManager,
                    new RoutingConfiguration(),
                    new DatabaseCacheConfiguration()));

            assertThatThrownBy(() -> gatewayManager.get(200, MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            heldHandle.close();
            assertThat(gatewayManager.get(3, SECONDS)).isNotNull();
        }
        finally {
            connectionManager.close();
        }
    }

    @Test
    void testDoesNotBlockWhenMaxPoolSizeIsNull()
            throws Exception
    {
        DataStoreConfiguration configuration = createConfiguration("no-pool", null);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);
        Jdbi jdbi = connectionManager.getJdbi();

        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            List<Future<?>> acquired = new ArrayList<>();
            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch acquiredLatch = new CountDownLatch(2);

            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    acquired.add(executorService.submit(() -> {
                        try (Handle _ = jdbi.open()) {
                            acquiredLatch.countDown();
                            assertThat(hold.await(10, SECONDS)).isTrue();
                        }
                        return null;
                    }));
                }

                assertThat(acquiredLatch.await(3, SECONDS)).isTrue();

                Future<Boolean> third = executorService.submit(() -> {
                    try (Handle _ = jdbi.open()) {
                        return true;
                    }
                });
                assertThat(third.get(3, SECONDS)).isTrue();

                int sessions = jdbi.withHandle(handle ->
                        handle.createQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")
                                .mapTo(int.class)
                                .one());
                assertThat(sessions).isGreaterThanOrEqualTo(3);
            }
            finally {
                hold.countDown();
                for (Future<?> future : acquired) {
                    future.get(3, SECONDS);
                }
                connectionManager.close();
            }
        }
    }

    @Test
    void testCloseClosesPool()
    {
        DataStoreConfiguration configuration = createConfiguration("close", 1);
        JdbcConnectionManager connectionManager = createConnectionManager(configuration);
        Jdbi jdbi = connectionManager.getJdbi();

        jdbi.useHandle(_ -> {});
        connectionManager.close();

        assertThatThrownBy(jdbi::open)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("has been closed");
    }

    private static void assertThirdConnectionBlocks(Jdbi firstJdbi, Jdbi secondJdbi)
            throws Exception
    {
        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            List<Future<?>> acquired = new ArrayList<>();
            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch acquiredLatch = new CountDownLatch(2);

            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    acquired.add(executorService.submit(() -> {
                        try (Handle _ = firstJdbi.open()) {
                            acquiredLatch.countDown();
                            assertThat(hold.await(10, SECONDS))
                                    .as("hold latch should be released by the test")
                                    .isTrue();
                        }
                        return null;
                    }));
                }

                assertThat(acquiredLatch.await(3, SECONDS))
                        .as("both connections should be acquired before third attempt")
                        .isTrue();

                Future<Boolean> third = executorService.submit(() -> {
                    try (Handle _ = secondJdbi.open()) {
                        return true;
                    }
                });

                assertThatThrownBy(() -> third.get(200, MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);

                hold.countDown();
                assertThat(third.get(3, SECONDS)).isTrue();
            }
            finally {
                hold.countDown();
                for (Future<?> future : acquired) {
                    future.get(3, SECONDS);
                }
            }
        }
    }

    private DataStoreConfiguration createConfiguration(String databaseName, Integer maxPoolSize)
    {
        String jdbcUrl = "jdbc:h2:" + temporaryDirectory.resolve(databaseName);
        return new DataStoreConfiguration(
                jdbcUrl,
                "sa",
                "sa",
                "org.h2.Driver",
                true,
                4,
                true,
                maxPoolSize);
    }

    private static JdbcConnectionManager createConnectionManager(DataStoreConfiguration configuration)
    {
        return new JdbcConnectionManager(
                Jdbi.create(configuration.getJdbcUrl(), configuration.getUser(), configuration.getPassword()),
                configuration);
    }
}
