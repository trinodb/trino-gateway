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

import io.airlift.units.Duration;
import io.trino.gateway.ha.cache.ValkeyDistributedCache;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.config.ValkeyConfiguration;
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static io.trino.gateway.ha.util.TestcontainersUtils.createValkeyContainer;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestValkeyDistributedCacheIntegration
{
    private final JdbcDatabaseContainer<?> postgresContainer = createPostgreSqlContainer();
    private final GenericContainer<?> valkeyContainer = createValkeyContainer();
    private ValkeyDistributedCache distributedCache;
    private StochasticRoutingManager routingManager;
    private QueryHistoryManager queryHistoryManager;
    private GatewayBackendManager backendManager;

    @BeforeAll
    void setUp()
    {
        // Start containers
        postgresContainer.start();
        valkeyContainer.start();

        // Setup database
        DataStoreConfiguration config = new DataStoreConfiguration(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword(),
                postgresContainer.getDriverClassName(),
                4,
                true);
        FlywayMigration.migrate(config);
        JdbcConnectionManager jdbcConnectionManager = createTestingJdbcConnectionManager(config);
        queryHistoryManager = new HaQueryHistoryManager(jdbcConnectionManager.getJdbi(), config);

        // Setup Valkey cache
        ValkeyConfiguration valkeyConfig = new ValkeyConfiguration();
        valkeyConfig.setEnabled(true);
        valkeyConfig.setHost(valkeyContainer.getHost());
        valkeyConfig.setPort(valkeyContainer.getMappedPort(6379));
        valkeyConfig.setDatabase(0);
        valkeyConfig.setMaxTotal(20);
        valkeyConfig.setMaxIdle(10);
        valkeyConfig.setMinIdle(5);
        valkeyConfig.setTimeout(new Duration(2, TimeUnit.SECONDS));
        valkeyConfig.setCacheTtl(new Duration(30, TimeUnit.MINUTES));

        distributedCache = new ValkeyDistributedCache(valkeyConfig);

        // Setup routing manager with mocked backend manager
        backendManager = Mockito.mock(GatewayBackendManager.class);
        RoutingConfiguration routingConfiguration = new RoutingConfiguration();
        routingManager = new StochasticRoutingManager(backendManager, queryHistoryManager, routingConfiguration, distributedCache);
    }

    @AfterAll
    void tearDown()
    {
        if (distributedCache != null) {
            distributedCache.close();
        }
        valkeyContainer.close();
        postgresContainer.close();
    }

    @Test
    void testValkeyConnectionAndBasicOperations()
    {
        // Verify cache is enabled
        assertThat(distributedCache.isEnabled()).isTrue();

        // Test basic set and get
        String testKey = "test:basic:key";
        String testValue = "test-value";

        distributedCache.set(testKey, testValue);
        Optional<String> retrievedValue = distributedCache.get(testKey);

        assertThat(retrievedValue).isPresent();
        assertThat(retrievedValue.get()).isEqualTo(testValue);

        // Test invalidate
        distributedCache.invalidate(testKey);
        Optional<String> afterInvalidate = distributedCache.get(testKey);
        assertThat(afterInvalidate).isEmpty();
    }

    @Test
    void testUpdateQueryIdCachesAllThreeValues()
    {
        String queryId = "test_query_123";
        String backend = "http://backend1.example.com:8080";
        String routingGroup = "adhoc";
        String externalUrl = "https://external.example.com";

        // Update cache with all 3 values
        routingManager.updateQueryIdCache(queryId, backend, routingGroup, externalUrl);

        // Verify all 3 values are in Valkey
        Optional<String> cachedBackend = distributedCache.get("trino:query:backend:" + queryId);
        Optional<String> cachedRoutingGroup = distributedCache.get("trino:query:routing_group:" + queryId);
        Optional<String> cachedExternalUrl = distributedCache.get("trino:query:external_url:" + queryId);

        assertThat(cachedBackend).isPresent();
        assertThat(cachedBackend.get()).isEqualTo(backend);

        assertThat(cachedRoutingGroup).isPresent();
        assertThat(cachedRoutingGroup.get()).isEqualTo(routingGroup);

        assertThat(cachedExternalUrl).isPresent();
        assertThat(cachedExternalUrl.get()).isEqualTo(externalUrl);
    }

    @Test
    void testRoutingGroupL2Caching()
    {
        String queryId = "routing_group_test_query";
        String routingGroup = "etl";

        // Set routing group (should cache in L1 and L2)
        routingManager.setRoutingGroupForQueryId(queryId, routingGroup);

        // Verify it's in L2 (Valkey)
        Optional<String> cachedValue = distributedCache.get("trino:query:routing_group:" + queryId);
        assertThat(cachedValue).isPresent();
        assertThat(cachedValue.get()).isEqualTo(routingGroup);

        // Clear L1 cache by creating new routing manager instance
        StochasticRoutingManager newRoutingManager = new StochasticRoutingManager(
                backendManager, queryHistoryManager, new RoutingConfiguration(), distributedCache);

        // Should retrieve from L2 (Valkey)
        String retrievedRoutingGroup = newRoutingManager.findRoutingGroupForQueryId(queryId);
        assertThat(retrievedRoutingGroup).isEqualTo(routingGroup);
    }

    @Test
    void testExternalUrlL2Caching()
    {
        String queryId = "external_url_test_query";
        String externalUrl = "https://external-gateway.example.com";

        // Create query detail and submit it
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId(queryId);
        queryDetail.setBackendUrl("http://backend.example.com:8080");
        queryDetail.setExternalUrl(externalUrl);
        queryDetail.setUser("test-user");
        queryDetail.setQueryText("SELECT 1");
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(queryDetail);

        // Update cache
        routingManager.updateQueryIdCache(queryId, queryDetail.getBackendUrl(), "adhoc", externalUrl);

        // Verify it's in L2 (Valkey)
        Optional<String> cachedValue = distributedCache.get("trino:query:external_url:" + queryId);
        assertThat(cachedValue).isPresent();
        assertThat(cachedValue.get()).isEqualTo(externalUrl);

        // Clear L1 cache by creating new routing manager instance
        StochasticRoutingManager newRoutingManager = new StochasticRoutingManager(
                backendManager, queryHistoryManager, new RoutingConfiguration(), distributedCache);

        // Should retrieve from L2 (Valkey)
        String retrievedExternalUrl = newRoutingManager.findExternalUrlForQueryId(queryId);
        assertThat(retrievedExternalUrl).isEqualTo(externalUrl);
    }

    @Test
    void testThreeTierCacheLookupForBackend()
    {
        String queryId = "three_tier_backend_test";
        String backend = "http://backend2.example.com:8080";

        // Populate L2 (Valkey) only
        distributedCache.set("trino:query:backend:" + queryId, backend);

        // Create new routing manager (empty L1 cache)
        StochasticRoutingManager newRoutingManager = new StochasticRoutingManager(
                backendManager, queryHistoryManager, new RoutingConfiguration(), distributedCache);

        // L1 miss → L2 hit scenario
        String retrievedBackend = newRoutingManager.findBackendForQueryId(queryId);
        assertThat(retrievedBackend).isEqualTo(backend);
    }

    @Test
    void testCacheBackfillFromDatabase()
    {
        String queryId = "backfill_test_query";
        String backend = "http://backend3.example.com:8080";
        String routingGroup = "scheduled";
        String externalUrl = "https://scheduled-gateway.example.com";

        // Submit query detail to L3 (database) only
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId(queryId);
        queryDetail.setBackendUrl(backend);
        queryDetail.setRoutingGroup(routingGroup);
        queryDetail.setExternalUrl(externalUrl);
        queryDetail.setUser("test-user");
        queryDetail.setQueryText("SELECT 1");
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(queryDetail);

        // Create new routing manager (empty L1 cache) and clear L2
        distributedCache.invalidate("trino:query:backend:" + queryId);
        distributedCache.invalidate("trino:query:routing_group:" + queryId);
        distributedCache.invalidate("trino:query:external_url:" + queryId);

        StochasticRoutingManager newRoutingManager = new StochasticRoutingManager(
                backendManager, queryHistoryManager, new RoutingConfiguration(), distributedCache);

        // L1 miss → L2 miss → L3 hit → should backfill L2
        String retrievedBackend = newRoutingManager.findBackendForQueryId(queryId);
        assertThat(retrievedBackend).isEqualTo(backend);

        // Verify L2 was backfilled
        Optional<String> cachedBackend = distributedCache.get("trino:query:backend:" + queryId);
        assertThat(cachedBackend).isPresent();
        assertThat(cachedBackend.get()).isEqualTo(backend);

        // Test routing group backfill
        String retrievedRoutingGroup = newRoutingManager.findRoutingGroupForQueryId(queryId);
        assertThat(retrievedRoutingGroup).isEqualTo(routingGroup);

        Optional<String> cachedRoutingGroup = distributedCache.get("trino:query:routing_group:" + queryId);
        assertThat(cachedRoutingGroup).isPresent();
        assertThat(cachedRoutingGroup.get()).isEqualTo(routingGroup);

        // Test external URL backfill
        String retrievedExternalUrl = newRoutingManager.findExternalUrlForQueryId(queryId);
        assertThat(retrievedExternalUrl).isEqualTo(externalUrl);

        Optional<String> cachedExternalUrl = distributedCache.get("trino:query:external_url:" + queryId);
        assertThat(cachedExternalUrl).isPresent();
        assertThat(cachedExternalUrl.get()).isEqualTo(externalUrl);
    }

    @Test
    void testMultipleQueryIdsWithDifferentValues()
    {
        String[] queryIds = {"multi_test_1", "multi_test_2", "multi_test_3"};
        String[] backends = {
                "http://backend1.example.com:8080",
                "http://backend2.example.com:8080",
                "http://backend3.example.com:8080"
        };
        String[] routingGroups = {"adhoc", "etl", "scheduled"};
        String[] externalUrls = {
                "https://gateway1.example.com",
                "https://gateway2.example.com",
                "https://gateway3.example.com"
        };

        // Cache all query IDs
        for (int i = 0; i < queryIds.length; i++) {
            routingManager.updateQueryIdCache(queryIds[i], backends[i], routingGroups[i], externalUrls[i]);
        }

        // Verify all values are correctly stored
        for (int i = 0; i < queryIds.length; i++) {
            Optional<String> cachedBackend = distributedCache.get("trino:query:backend:" + queryIds[i]);
            Optional<String> cachedRoutingGroup = distributedCache.get("trino:query:routing_group:" + queryIds[i]);
            Optional<String> cachedExternalUrl = distributedCache.get("trino:query:external_url:" + queryIds[i]);

            assertThat(cachedBackend).isPresent();
            assertThat(cachedBackend.get()).isEqualTo(backends[i]);

            assertThat(cachedRoutingGroup).isPresent();
            assertThat(cachedRoutingGroup.get()).isEqualTo(routingGroups[i]);

            assertThat(cachedExternalUrl).isPresent();
            assertThat(cachedExternalUrl.get()).isEqualTo(externalUrls[i]);
        }
    }

    @Test
    void testCacheOverwrite()
    {
        String queryId = "overwrite_test_query";
        String initialBackend = "http://initial-backend.example.com:8080";
        String updatedBackend = "http://updated-backend.example.com:8080";
        String routingGroup = "adhoc";
        String externalUrl = "https://gateway.example.com";

        // Set initial value
        routingManager.updateQueryIdCache(queryId, initialBackend, routingGroup, externalUrl);
        Optional<String> cached = distributedCache.get("trino:query:backend:" + queryId);
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(initialBackend);

        // Overwrite with new value
        routingManager.updateQueryIdCache(queryId, updatedBackend, routingGroup, externalUrl);
        Optional<String> updatedCached = distributedCache.get("trino:query:backend:" + queryId);
        assertThat(updatedCached).isPresent();
        assertThat(updatedCached.get()).isEqualTo(updatedBackend);
    }

    @Test
    void testEmptyStringValues()
    {
        String queryId = "empty_string_test";
        String backend = "http://backend.example.com:8080";
        String emptyRoutingGroup = "";
        String emptyExternalUrl = "";

        // Cache with empty strings
        routingManager.updateQueryIdCache(queryId, backend, emptyRoutingGroup, emptyExternalUrl);

        // Verify empty strings are stored correctly
        Optional<String> cachedRoutingGroup = distributedCache.get("trino:query:routing_group:" + queryId);
        Optional<String> cachedExternalUrl = distributedCache.get("trino:query:external_url:" + queryId);

        assertThat(cachedRoutingGroup).isPresent();
        assertThat(cachedRoutingGroup.get()).isEqualTo(emptyRoutingGroup);

        assertThat(cachedExternalUrl).isPresent();
        assertThat(cachedExternalUrl.get()).isEqualTo(emptyExternalUrl);
    }
}
