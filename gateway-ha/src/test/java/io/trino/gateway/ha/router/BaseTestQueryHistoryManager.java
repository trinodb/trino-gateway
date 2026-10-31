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
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.WriteBufferConfiguration;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseTestQueryHistoryManager
{
    private static final String BACKEND_URL = "http://localhost:9999";
    private static final String SQL_WORKBENCH = "sqlWorkbench";
    private static final String USER = "test@ea.com";
    private static final String SELECT_1 = "select 1";
    private static final String OTHER_USER = "other-user";
    private static final String ROUTING_GROUP = "routing-group";
    private static final String EXTERNAL_URL = "https://example.com";

    private final JdbcDatabaseContainer<?> container;
    private DataStoreConfiguration config;
    private QueryHistoryManager queryHistoryManager;
    private Jdbi jdbi;

    protected BaseTestQueryHistoryManager(JdbcDatabaseContainer<?> container)
    {
        this.container = requireNonNull(container, "container is null");
    }

    @BeforeAll
    void setUp()
    {
        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getDriverClassName(),
                true,
                4,
                true,
                null);
        FlywayMigration.migrate(config);
        JdbcConnectionManager jdbcConnectionManager = new JdbcConnectionManager(config);
        jdbi = jdbcConnectionManager.getJdbi();
        queryHistoryManager = new HaQueryHistoryManager(jdbi, config, writeBufferConfig);
    }

    @BeforeEach
    void cleanUp()
    {
        jdbi.useHandle(handle -> handle.execute("DELETE FROM query_history"));
    }

    @Test
    void testSubmitAndFetchQueryHistory()
    {
        List<QueryHistoryManager.QueryDetail> queryDetails =
                queryHistoryManager.fetchQueryHistory(Optional.empty());
        assertThat(queryDetails).isEmpty();
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setBackendUrl(BACKEND_URL);
        queryDetail.setSource(SQL_WORKBENCH);
        queryDetail.setUser(USER);
        queryDetail.setQueryText(SELECT_1);
        for (int i = 0; i < 2; i++) {
            queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
            queryDetail.setCaptureTime(System.currentTimeMillis());
            queryHistoryManager.submitQueryDetail(queryDetail);
        }

        // Add a query from other user
        queryDetail.setUser(OTHER_USER);
        queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(queryDetail);

        queryDetails = queryHistoryManager.fetchQueryHistory(Optional.empty());
        assertThat(queryDetails.get(0).getCaptureTime() > queryDetails.get(1).getCaptureTime()).isTrue();
        // All queries when user is empty
        assertThat(queryDetails).hasSize(3);

        queryDetails = queryHistoryManager.fetchQueryHistory(Optional.of(OTHER_USER));
        // Only 1 query when user is 'other-user'
        assertThat(queryDetails).hasSize(1);
    }

    @Test
    void testSubmitQueryDetailTruncatesQueryText()
    {
        DataStoreConfiguration config = new DataStoreConfiguration();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setQueryHistoryMaxQueryTextLength(10);
        QueryHistoryManager truncatingManager = new HaQueryHistoryManager(jdbi, config, new WriteBufferConfiguration());

        QueryHistoryManager.QueryDetail longQuery = new QueryHistoryManager.QueryDetail();
        longQuery.setQueryId("truncation_long");
        longQuery.setQueryText("select 1234567890");
        longQuery.setBackendUrl("http://localhost:9999");
        longQuery.setUser("test@ea.com");
        longQuery.setCaptureTime(System.currentTimeMillis());
        truncatingManager.submitQueryDetail(longQuery);

        QueryHistoryManager.QueryDetail shortQuery = new QueryHistoryManager.QueryDetail();
        shortQuery.setQueryId("truncation_short");
        shortQuery.setQueryText("select 1");
        shortQuery.setBackendUrl("http://localhost:9999");
        shortQuery.setUser("test@ea.com");
        shortQuery.setCaptureTime(System.currentTimeMillis() + 1);
        truncatingManager.submitQueryDetail(shortQuery);

        // The submitted detail is not modified, only the persisted text is truncated
        assertThat(longQuery.getQueryText()).isEqualTo("select 1234567890");

        assertThat(truncatingManager.fetchQueryHistory(Optional.empty()))
                .extracting(QueryHistoryManager.QueryDetail::getQueryText)
                .containsExactly(
                        "select 1",
                        "select 123\n-- [truncated by trino-gateway: 10 of 17 chars]");
    }

    @Test
    void testTruncateQueryTextBoundaries()
    {
        DataStoreConfiguration config = new DataStoreConfiguration();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setQueryHistoryMaxQueryTextLength(8);
        QueryHistoryManager truncatingManager = new HaQueryHistoryManager(jdbi, config, new WriteBufferConfiguration());

        QueryHistoryManager.QueryDetail exact = new QueryHistoryManager.QueryDetail();
        exact.setQueryId("truncation_exact");
        exact.setQueryText("select 1"); // length == 8, not truncated
        exact.setBackendUrl("http://localhost:9999");
        exact.setUser("test@ea.com");
        exact.setCaptureTime(System.currentTimeMillis());
        truncatingManager.submitQueryDetail(exact);

        QueryHistoryManager.QueryDetail surrogate = new QueryHistoryManager.QueryDetail();
        surrogate.setQueryId("truncation_surrogate");
        surrogate.setQueryText("select \uD83D\uDE00 x"); // emoji pair at index 7-8
        surrogate.setBackendUrl("http://localhost:9999");
        surrogate.setUser("test@ea.com");
        surrogate.setCaptureTime(System.currentTimeMillis() + 1);
        truncatingManager.submitQueryDetail(surrogate);

        assertThat(truncatingManager.fetchQueryHistory(Optional.empty()))
                .extracting(QueryHistoryManager.QueryDetail::getQueryText)
                .containsExactly(
                        "select \n-- [truncated by trino-gateway: 7 of 11 chars]",
                        "select 1");
    }

    @Test
    void testInvalidMaxQueryTextLength()
    {
        DataStoreConfiguration config = new DataStoreConfiguration();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setQueryHistoryMaxQueryTextLength(0);
        assertThatThrownBy(() -> new HaQueryHistoryManager(jdbi, config, new WriteBufferConfiguration()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queryHistoryMaxQueryTextLength must be greater than 0");
    }

    @Test
    void testFindDistribution()
    {
        long currentTime = System.currentTimeMillis();
        List<DistributionResponse.LineChart> resList = queryHistoryManager.findDistribution(currentTime);
        // Should return empty list
        assertThat(resList).isEmpty();

        QueryHistoryManager.QueryDetail queryDetail = createQueryDetail();
        queryHistoryManager.submitQueryDetail(queryDetail);

        // Should return 1 entry
        resList = queryHistoryManager.findDistribution(currentTime);
        assertThat(resList).hasSize(1);
    }

    @Test
    void testSqlInjectionInFindQueryHistory()
    {
        // Use unique user names to avoid interference with other tests
        QueryHistoryManager.QueryDetail detail1 = new QueryHistoryManager.QueryDetail();
        detail1.setBackendUrl("http://backend1:8080");
        detail1.setSource("injection-source");
        detail1.setUser("injection-alice");
        detail1.setQueryText("select 1");
        detail1.setQueryId("injection_test_1");
        detail1.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(detail1);

        QueryHistoryManager.QueryDetail detail2 = new QueryHistoryManager.QueryDetail();
        detail2.setBackendUrl("http://backend2:8080");
        detail2.setSource("injection-source");
        detail2.setUser("injection-bob");
        detail2.setQueryText("select 2");
        detail2.setQueryId("injection_test_2");
        detail2.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(detail2);

        // Attempt SQL injection via externalUrl field to bypass user filter
        QueryHistoryRequest injectionRequest = new QueryHistoryRequest(
                1, 100, "injection-alice", "' OR 1=1 AND '1'='1", null, null);
        TableData<QueryHistoryManager.QueryDetail> result = queryHistoryManager.findQueryHistory(injectionRequest);
        // Injected value is treated as literal — no externalUrl matches, so no rows returned
        assertThat(result.getRows()).isEmpty();

        // Attempt SQL injection via source field
        QueryHistoryRequest sourceInjection = new QueryHistoryRequest(
                1, 100, "injection-alice", null, null, "' OR '1'='1");
        TableData<QueryHistoryManager.QueryDetail> sourceResult = queryHistoryManager.findQueryHistory(sourceInjection);
        assertThat(sourceResult.getRows()).isEmpty();

        // Attempt SQL injection via queryId field
        QueryHistoryRequest queryIdInjection = new QueryHistoryRequest(
                1, 100, "injection-alice", null, "' OR '1'='1", null);
        TableData<QueryHistoryManager.QueryDetail> queryIdResult = queryHistoryManager.findQueryHistory(queryIdInjection);
        assertThat(queryIdResult.getRows()).isEmpty();

        // Verify normal query still works
        QueryHistoryRequest normalRequest = new QueryHistoryRequest(
                1, 100, "injection-alice", null, null, null);
        TableData<QueryHistoryManager.QueryDetail> normalResult = queryHistoryManager.findQueryHistory(normalRequest);
        assertThat(normalResult.getRows()).hasSize(1);
        assertThat(normalResult.getRows().get(0).getUser()).isEqualTo("injection-alice");
    }

    @Test
    void testTimestampParsing()
    {
        // This ensures odd-minute values remain precision when converted from different formats.
        long expectedMinuteBucket = 30338641;

        // postgres: minute -> {Double@9333} 3.0338641E7
        String postgresTimestamp = "3.0338641E7";
        long parsedPostgresMinute = new BigDecimal(postgresTimestamp).longValue();
        assertThat(parsedPostgresMinute).isEqualTo(expectedMinuteBucket);

        // mysql: minute -> {BigDecimal@9775} "30338641"
        String mysqlTimestamp = "30338641";
        long parsedMysqlMinute = new BigDecimal(mysqlTimestamp).longValue();
        assertThat(parsedMysqlMinute).isEqualTo(expectedMinuteBucket);
    }

    private static void stubInsertThenSucceed(QueryHistoryDao delegate, RuntimeException first)
    {
        doThrow(first)
                .doNothing()
                .when(delegate)
                .insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void testBuffersOnConnectionExceptionAndFlushesOnStop()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        stubInsertThenSucceed(delegate, new ConnectionException(new SQLException("network error", "08006")));

        Jdbi mockJdbi = mock(Jdbi.class);
        when(mockJdbi.onDemand(QueryHistoryDao.class)).thenReturn(delegate);

        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        writeBufferConfig.setEnabled(true);
        HaQueryHistoryManager manager = new HaQueryHistoryManager(mockJdbi, config, writeBufferConfig);
        // First call buffers (no throw), then stop() flushes once and succeeds
        QueryHistoryManager.QueryDetail queryDetail = createQueryDetail();
        try {
            manager.submitQueryDetail(queryDetail);
            manager.stop();
            verify(delegate, times(2)).insertHistory(anyString(), eq(SELECT_1), eq(BACKEND_URL), eq(USER), eq(SQL_WORKBENCH), anyLong(), anyString(), anyString());
        }
        finally {
            // idempotent
            manager.stop();
        }
    }

    @Test
    void testRethrowsNonConnectionException()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        doThrow(new IllegalStateException("bad input"))
                .when(delegate)
                .insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());

        Jdbi mockJdbi = mock(Jdbi.class);
        when(mockJdbi.onDemand(QueryHistoryDao.class)).thenReturn(delegate);
        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        writeBufferConfig.setEnabled(true);
        HaQueryHistoryManager manager = new HaQueryHistoryManager(mockJdbi, config, writeBufferConfig);

        QueryHistoryManager.QueryDetail queryDetail = createQueryDetail();
        try {
            assertThatThrownBy(() -> manager.submitQueryDetail(queryDetail))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bad input");

            // Should not have buffered, so no retry on stop
            manager.stop();
            verify(delegate, times(1)).insertHistory(anyString(), eq(SELECT_1), eq(BACKEND_URL), eq(USER), eq(SQL_WORKBENCH), anyLong(), anyString(), anyString());
        }
        finally {
            manager.stop();
        }
    }

    @Test
    void testScheduledFlushWritesBufferedEntries()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        stubInsertThenSucceed(delegate, new ConnectionException(new SQLException("network error", "08006")));
        HaQueryHistoryManager manager = createBufferingManager(delegate, config, new Duration(100, MILLISECONDS));
        try {
            manager.submitQueryDetail(createQueryDetail());
            // The scheduled flush retries the buffered entry without a call to stop()
            verify(delegate, timeout(10_000).times(2)).insertHistory(anyString(), eq(SELECT_1), eq(BACKEND_URL), eq(USER), eq(SQL_WORKBENCH), anyLong(), anyString(), anyString());
        }
        finally {
            manager.stop();
        }
    }

    @Test
    void testBuffersOnConnectionSqlState()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        // An SQLState in the 08 class without a Jdbi ConnectionException in the cause chain
        stubInsertThenSucceed(delegate, new UnableToExecuteStatementException(new SQLException("connection refused", "08001"), null));
        HaQueryHistoryManager manager = createBufferingManager(delegate, config, new Duration(1, HOURS));
        try {
            manager.submitQueryDetail(createQueryDetail());
            manager.stop();
            verify(delegate, times(2)).insertHistory(anyString(), eq(SELECT_1), eq(BACKEND_URL), eq(USER), eq(SQL_WORKBENCH), anyLong(), anyString(), anyString());
        }
        finally {
            manager.stop();
        }
    }

    @Test
    void testFailedBufferedEntryDoesNotBlockLaterEntries()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        doThrow(new ConnectionException(new SQLException("network error", "08006")))
                .doThrow(new IllegalStateException("duplicate key"))
                .doNothing()
                .when(delegate)
                .insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        HaQueryHistoryManager manager = createBufferingManager(delegate, config, new Duration(1, HOURS));
        QueryHistoryManager.QueryDetail first = createQueryDetail();
        first.setQueryId("buffered_first");
        QueryHistoryManager.QueryDetail second = createQueryDetail();
        second.setQueryId("buffered_second");
        try {
            manager.submitQueryDetail(first);
            manager.submitQueryDetail(second);
            // The buffer is not empty, so the second entry is buffered without an insert attempt
            verify(delegate, never()).insertHistory(eq("buffered_second"), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());

            manager.stop();
            // The first entry fails with a non-connection error on flush and is dropped,
            // and the second entry behind it is still written
            verify(delegate, times(2)).insertHistory(eq("buffered_first"), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
            verify(delegate, times(1)).insertHistory(eq("buffered_second"), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        }
        finally {
            manager.stop();
        }
    }

    @Test
    void testBuffersTruncatedQueryText()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        stubInsertThenSucceed(delegate, new ConnectionException(new SQLException("network error", "08006")));
        DataStoreConfiguration truncatingConfig = new DataStoreConfiguration();
        truncatingConfig.setJdbcUrl(container.getJdbcUrl());
        truncatingConfig.setQueryHistoryMaxQueryTextLength(6);
        HaQueryHistoryManager manager = createBufferingManager(delegate, truncatingConfig, new Duration(1, HOURS));
        String truncatedText = "select\n-- [truncated by trino-gateway: 6 of 8 chars]";
        QueryHistoryManager.QueryDetail queryDetail = createQueryDetail();
        try {
            manager.submitQueryDetail(queryDetail);
            manager.stop();
            // Both the failed insert and the flush of the buffered entry use the truncated text
            verify(delegate, times(2)).insertHistory(anyString(), eq(truncatedText), eq(BACKEND_URL), eq(USER), eq(SQL_WORKBENCH), anyLong(), anyString(), anyString());
            assertThat(queryDetail.getQueryText()).isEqualTo(SELECT_1);
        }
        finally {
            manager.stop();
        }
    }

    private static HaQueryHistoryManager createBufferingManager(QueryHistoryDao delegate, DataStoreConfiguration config, Duration flushInterval)
    {
        Jdbi mockJdbi = mock(Jdbi.class);
        when(mockJdbi.onDemand(QueryHistoryDao.class)).thenReturn(delegate);
        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        writeBufferConfig.setEnabled(true);
        writeBufferConfig.setFlushInterval(flushInterval);
        return new HaQueryHistoryManager(mockJdbi, config, writeBufferConfig);
    }

    private static QueryHistoryManager.QueryDetail createQueryDetail()
    {
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
        queryDetail.setQueryText(SELECT_1);
        queryDetail.setBackendUrl(BACKEND_URL);
        queryDetail.setUser(USER);
        queryDetail.setSource(SQL_WORKBENCH);
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryDetail.setRoutingGroup(ROUTING_GROUP);
        queryDetail.setExternalUrl(EXTERNAL_URL);
        return queryDetail;
    }
}
