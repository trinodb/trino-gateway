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
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseExternalUrlQueryHistoryTest
{
    private final JdbcDatabaseContainer<?> container;
    private final QueryHistoryManager queryHistoryManager;

    protected BaseExternalUrlQueryHistoryTest(JdbcDatabaseContainer<?> container)
    {
        this.container = requireNonNull(container, "container is null");
        this.container.start();

        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getDriverClassName(),
                4,
                true);
        FlywayMigration.migrate(config);
        JdbcConnectionManager jdbcConnectionManager = createTestingJdbcConnectionManager(container, config);
        queryHistoryManager = new HaQueryHistoryManager(jdbcConnectionManager.getJdbi(), container.getJdbcUrl().startsWith("jdbc:oracle"));
    }

    @AfterAll
    public final void close()
    {
        container.close();
    }

    @Test
    void testSubmitQueryWithExternalUrl()
    {
        // Clear existing data
        List<QueryHistoryManager.QueryDetail> queryDetails = queryHistoryManager.fetchQueryHistory(Optional.empty());
        assertThat(queryDetails).isEmpty();

        // Create and submit query with external URL
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId("test-query-123");
        queryDetail.setQueryText("SELECT * FROM test_table");
        queryDetail.setBackendUrl("http://localhost:8080");
        queryDetail.setUser("test-user");
        queryDetail.setSource("sqlWorkbench");
        queryDetail.setRoutingGroup("adhoc");
        queryDetail.setExternalUrl("https://external-gateway.example.com");
        queryDetail.setCaptureTime(System.currentTimeMillis());

        queryHistoryManager.submitQueryDetail(queryDetail);

        // Verify the query was stored with external URL
        queryDetails = queryHistoryManager.fetchQueryHistory(Optional.empty());
        assertThat(queryDetails).hasSize(1);

        QueryHistoryManager.QueryDetail retrievedQuery = queryDetails.getFirst();
        assertThat(retrievedQuery.getQueryId()).isEqualTo("test-query-123");
        assertThat(retrievedQuery.getExternalUrl()).isEqualTo("https://external-gateway.example.com");
    }

    @Test
    void testGetExternalUrlByQueryId()
    {
        // Create and submit query with external URL
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId("external-url-test-456");
        queryDetail.setQueryText("SELECT count(*) FROM users");
        queryDetail.setBackendUrl("http://backend:8080");
        queryDetail.setUser("admin");
        queryDetail.setSource("trino-cli");
        queryDetail.setRoutingGroup("analytics");
        queryDetail.setExternalUrl("https://analytics-gateway.company.com");
        queryDetail.setCaptureTime(System.currentTimeMillis());

        queryHistoryManager.submitQueryDetail(queryDetail);

        // Test retrieving external URL by query ID
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId("external-url-test-456");
        assertThat(externalUrl).isEqualTo("https://analytics-gateway.company.com");
    }

    @Test
    void testGetExternalUrlForNonExistentQuery()
    {
        // Test retrieving external URL for non-existent query
        String externalUrl = queryHistoryManager.getExternalUrlForQueryId("non-existent-query");
        assertThat(externalUrl).isNull();
    }

    @Test
    void testSubmitQueryWithNullExternalUrl()
    {
        // Create and submit query with null external URL
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId("null-external-url-test");
        queryDetail.setQueryText("SELECT 1");
        queryDetail.setBackendUrl("http://localhost:8080");
        queryDetail.setUser("test-user");
        queryDetail.setSource("sqlWorkbench");
        queryDetail.setRoutingGroup("adhoc");
        queryDetail.setExternalUrl(null);
        queryDetail.setCaptureTime(System.currentTimeMillis());

        queryHistoryManager.submitQueryDetail(queryDetail);

        // Verify the query was stored with null external URL
        List<QueryHistoryManager.QueryDetail> queryDetails = queryHistoryManager.fetchQueryHistory(Optional.empty());
        QueryHistoryManager.QueryDetail retrievedQuery = queryDetails.stream()
                .filter(q -> "null-external-url-test".equals(q.getQueryId()))
                .findFirst()
                .orElse(null);

        assertThat(retrievedQuery).isNotNull();
        assertThat(retrievedQuery.getExternalUrl()).isNull();
    }

    @Test
    void testMultipleQueriesWithDifferentExternalUrls()
    {
        // Submit multiple queries with different external URLs
        for (int i = 1; i <= 3; i++) {
            QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
            queryDetail.setQueryId("multi-external-url-test-" + i);
            queryDetail.setQueryText("SELECT " + i);
            queryDetail.setBackendUrl("http://backend-" + i + ":8080");
            queryDetail.setUser("user-" + i);
            queryDetail.setSource("source-" + i);
            queryDetail.setRoutingGroup("group-" + i);
            queryDetail.setExternalUrl("https://external-" + i + ".example.com");
            queryDetail.setCaptureTime(System.currentTimeMillis());

            queryHistoryManager.submitQueryDetail(queryDetail);
        }

        // Verify all queries were stored with correct external URLs
        for (int i = 1; i <= 3; i++) {
            String externalUrl = queryHistoryManager.getExternalUrlForQueryId("multi-external-url-test-" + i);
            assertThat(externalUrl).isEqualTo("https://external-" + i + ".example.com");
        }
    }

    @Test
    void testQueryDetailEqualsAndHashCodeWithExternalUrl()
    {
        QueryHistoryManager.QueryDetail queryDetail1 = new QueryHistoryManager.QueryDetail();
        queryDetail1.setQueryId("equals-test-1");
        queryDetail1.setQueryText("SELECT 1");
        queryDetail1.setBackendUrl("http://localhost:8080");
        queryDetail1.setUser("test-user");
        queryDetail1.setSource("sqlWorkbench");
        queryDetail1.setRoutingGroup("adhoc");
        queryDetail1.setExternalUrl("https://external.example.com");
        queryDetail1.setCaptureTime(System.currentTimeMillis());

        QueryHistoryManager.QueryDetail queryDetail2 = new QueryHistoryManager.QueryDetail();
        queryDetail2.setQueryId("equals-test-1");
        queryDetail2.setQueryText("SELECT 1");
        queryDetail2.setBackendUrl("http://localhost:8080");
        queryDetail2.setUser("test-user");
        queryDetail2.setSource("sqlWorkbench");
        queryDetail2.setRoutingGroup("adhoc");
        queryDetail2.setExternalUrl("https://external.example.com");
        queryDetail2.setCaptureTime(System.currentTimeMillis());

        // Test equals
        assertThat(queryDetail1).isEqualTo(queryDetail2);
        assertThat(queryDetail1.hashCode()).isEqualTo(queryDetail2.hashCode());

        // Test with different external URL
        queryDetail2.setExternalUrl("https://different.example.com");
        assertThat(queryDetail1).isNotEqualTo(queryDetail2);
    }
}
