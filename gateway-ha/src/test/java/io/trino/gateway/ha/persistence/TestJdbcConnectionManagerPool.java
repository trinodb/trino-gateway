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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

final class TestJdbcConnectionManagerPool
{
    @Test
    void blocksWhenExceedingMaxPoolSize()
            throws Exception
    {
        String dbPath = Path.of(System.getProperty("java.io.tmpdir"), "h2db-pool-" + System.currentTimeMillis()).toString();
        String jdbcUrl = "jdbc:h2:" + dbPath;

        DataStoreConfiguration cfg = new DataStoreConfiguration(
                jdbcUrl, "sa", "sa", "org.h2.Driver",
                4, true,
                2);

        JdbcConnectionManager cm = new JdbcConnectionManager(Jdbi.create(jdbcUrl, "sa", "sa"), cfg);
        Jdbi jdbi = cm.getJdbi("testdb");

        try (ExecutorService es = Executors.newFixedThreadPool(3)) {
            List<Future<Connection>> acquired = new ArrayList<>();

            CountDownLatch hold = new CountDownLatch(1);
            CountDownLatch acquiredLatch = new CountDownLatch(2);

            // Open exactly maxPoolSize connections and keep them open
            for (int i = 0; i < 2; i++) {
                acquired.add(es.submit(() -> {
                    try (var h = jdbi.open()) {
                        acquiredLatch.countDown();
                        boolean released = hold.await(10, TimeUnit.SECONDS);
                        assertThat(released).as("hold latch should be released by the test").isTrue();
                    }
                    return null;
                }));
            }

            // Wait until both connections are actually acquired (avoid race)
            boolean bothAcquired = acquiredLatch.await(3, TimeUnit.SECONDS);
            assertThat(bothAcquired).as("both connections should be acquired before third attempt").isTrue();

            // Third attempt should block since the pool is full
            Future<Boolean> third = es.submit(() -> {
                var h = jdbi.open();
                h.close();
                return true;
            });

            boolean completedIn200ms = false;
            try {
                third.get(200, TimeUnit.MILLISECONDS);
                completedIn200ms = true; // if this happens when connection was not blocked, which is wrong
            }
            catch (TimeoutException expected) {
                // expected, means the request was blocked on the pool
            }

            assertThat(completedIn200ms)
                    .as("third getJdbi().open() should be blocked by maxPoolSize=2")
                    .isFalse();

            // Release the first two connections, the third one should complete now
            hold.countDown();
            assertThat(third.get(3, TimeUnit.SECONDS)).isTrue();

            // Wait for the first two to finish gracefully
            for (Future<Connection> f : acquired) {
                f.get(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void doesNotBlockWhenMaxPoolSizeIsNull()
            throws Exception
    {
        String dbPath = Path.of(System.getProperty("java.io.tmpdir"), "h2db-nopool-" + System.currentTimeMillis()).toString();
        String jdbcUrl = "jdbc:h2:" + dbPath;

        // maxPoolSize == null  ->  no pool path
        DataStoreConfiguration cfg = new DataStoreConfiguration(
                jdbcUrl, "sa", "sa", "org.h2.Driver",
                4, true);

        JdbcConnectionManager cm = new JdbcConnectionManager(Jdbi.create(jdbcUrl, "sa", "sa"), cfg);
        Jdbi jdbi = cm.getJdbi("testdb");

        try (ExecutorService es = Executors.newFixedThreadPool(3)) {
            try {
                CountDownLatch hold = new CountDownLatch(1);
                CountDownLatch acquiredLatch = new CountDownLatch(2);

                // Open two connections and keep them open
                for (int i = 0; i < 2; i++) {
                    es.submit(() -> {
                        try (var h = jdbi.open()) {
                            acquiredLatch.countDown();
                            boolean released = hold.await(10, TimeUnit.SECONDS);
                            assertThat(released).isTrue();
                        }
                        return null;
                    });
                }

                // Wait until both connections are really open (avoid race conditions)
                boolean bothAcquired = acquiredLatch.await(3, TimeUnit.SECONDS);
                assertThat(bothAcquired).isTrue();

                // Third connection attempt should NOT block since no pool is used
                Future<Boolean> third = es.submit(() -> {
                    var h = jdbi.open();
                    h.close();
                    return true;
                });

                boolean completedIn200ms;
                try {
                    third.get(200, TimeUnit.MILLISECONDS);
                    completedIn200ms = true;   // not blocked - expected behavior
                }
                catch (TimeoutException ignore) {
                    completedIn200ms = false;  // blocked - incorrect for no-pool case
                }

                assertThat(completedIn200ms)
                        .as("third getJdbi().open() should NOT block when no pool is configured")
                        .isTrue();

                // check H2 session count to confirm multiple physical connections were opened
                int sessions = jdbi.withHandle(h ->
                        h.createQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")
                                .mapTo(int.class)
                                .one());
                assertThat(sessions).isGreaterThanOrEqualTo(3);

                // Release the first two connections
                hold.countDown();
                assertThat(third.get(3, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                es.shutdownNow();
            }
        }
    }
}
