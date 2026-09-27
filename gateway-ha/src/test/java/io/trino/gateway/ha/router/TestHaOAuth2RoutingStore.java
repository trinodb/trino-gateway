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
package io.trino.gateway.ha.router;

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingPostgresContainer;
import static io.trino.gateway.ha.TestingJdbcConnectionManager.dataStoreConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestHaOAuth2RoutingStore
{
    private final PostgreSQLContainer postgres = createTestingPostgresContainer();

    private DataStoreConfiguration dataStoreConfig;
    private JdbcConnectionManager connectionManager;
    private JdbcConnectionManager otherPodConnectionManager;
    private OAuth2RoutingStore store;

    @BeforeAll
    void setUp()
    {
        dataStoreConfig = dataStoreConfig(postgres);
        connectionManager = new JdbcConnectionManager(dataStoreConfig);
        store = new HaOAuth2RoutingStore(connectionManager.getJdbi());
    }

    @AfterAll
    void tearDown()
    {
        // Each JdbcConnectionManager starts a non-daemon scheduled-cleanup thread in its constructor;
        // shut them down so they do not outlive the test and keep the JVM alive.
        if (connectionManager != null) {
            connectionManager.close();
        }
        if (otherPodConnectionManager != null) {
            otherPodConnectionManager.close();
        }
        postgres.close();
    }

    @Test
    void testSetFindRemove()
    {
        assertThat(store.findBackend("pin-x")).isEmpty();

        store.setBackend("pin-x", "http://coord-a:8080");
        assertThat(store.findBackend("pin-x")).hasValue("http://coord-a:8080");

        // Idempotent re-pin replaces the row rather than failing on the primary key.
        store.setBackend("pin-x", "http://coord-b:8080");
        assertThat(store.findBackend("pin-x")).hasValue("http://coord-b:8080");

        // A forced re-auth drops the pin.
        store.removeBackend("pin-x");
        assertThat(store.findBackend("pin-x")).isEmpty();
    }

    @Test
    void testPinIsVisibleAcrossPods()
    {
        // A pin written by one pod must be readable by another pod sharing the DB.
        store.setBackend("pin-shared", "http://coord-c:8080");

        otherPodConnectionManager = new JdbcConnectionManager(dataStoreConfig);
        OAuth2RoutingStore otherPod = new HaOAuth2RoutingStore(otherPodConnectionManager.getJdbi());
        assertThat(otherPod.findBackend("pin-shared")).hasValue("http://coord-c:8080");
    }

    @Test
    void testDescribeForLogNeverIncludesExceptionMessage()
    {
        // Jdbi exception messages can contain the bound pin key
        String pinKey = "pin-that-must-never-appear-in-a-log-line";
        SQLException sqlException = new SQLException("statement failed, arguments: [" + pinKey + "]", "23505");
        RuntimeException wrapped = new RuntimeException("insert failed for pin_key=" + pinKey, sqlException);

        String described = HaOAuth2RoutingStore.describeForLog(wrapped);

        assertThat(described).doesNotContain(pinKey)
                .contains("RuntimeException")
                .contains("23505");

        RuntimeException noCause = new RuntimeException("insert failed for pin_key=" + pinKey);
        assertThat(HaOAuth2RoutingStore.describeForLog(noCause))
                .doesNotContain(pinKey)
                .isEqualTo("RuntimeException");
    }

    @Test
    void testPinsWithConnectionPool()
    {
        DataStoreConfiguration pooledConfig = dataStoreConfig(postgres);
        pooledConfig.setMaxPoolSize(2);
        try (JdbcConnectionManager pooledConnectionManager = new JdbcConnectionManager(pooledConfig)) {
            OAuth2RoutingStore pooledStore = new HaOAuth2RoutingStore(pooledConnectionManager.getJdbi());

            pooledStore.setBackend("pin-pooled", "http://coord-e:8080");
            assertThat(pooledStore.findBackend("pin-pooled")).hasValue("http://coord-e:8080");
            assertThat(store.findBackend("pin-pooled")).hasValue("http://coord-e:8080");

            pooledStore.removeBackend("pin-pooled");
            assertThat(pooledStore.findBackend("pin-pooled")).isEmpty();
        }
    }
}
