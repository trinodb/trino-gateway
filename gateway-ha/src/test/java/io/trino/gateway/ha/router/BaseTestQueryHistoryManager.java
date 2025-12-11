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
import io.trino.gateway.ha.config.WriteBufferConfiguration;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseTestQueryHistoryManager
{
    protected final JdbcDatabaseContainer<?> container = startContainer();
    private static final String BACKEND_URL = "http://localhost:9999";
    private static final String SQL_WORKBENCH = "sqlWorkbench";
    private static final String USER = "test@ea.com";
    private static final String SELECT_1 = "select 1";
    private static final String OTHER_USER = "other-user";
    private static final String ROUTING_GROUP = "routing-group";
    private static final String EXTERNAL_URL = "https://example.com";
    private QueryHistoryManager queryHistoryManager;

    protected abstract JdbcDatabaseContainer<?> startContainer();

    @BeforeAll
    void setUp()
    {
        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getDriverClassName(),
                4,
                true);
        FlywayMigration.migrate(config);
        JdbcConnectionManager jdbcConnectionManager = createTestingJdbcConnectionManager(container, config);
        queryHistoryManager = new HaQueryHistoryManager(jdbcConnectionManager.getJdbi(), container.getJdbcUrl().startsWith("jdbc:oracle"), writeBufferConfig);
    }

    @AfterAll
    public final void close()
    {
        container.close();
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

        //Add a query from other user
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
    void testTimestampParsing()
    {
        long result = 30338640;

        // postgres: minute -> {Double@9333} 3.033864E7
        String postgresTimestamp = "3.033864E7";
        long parsedLongTimestamp = (long) Float.parseFloat(postgresTimestamp);
        assertThat(parsedLongTimestamp).isEqualTo(result);

        // mysql: minute -> {BigDecimal@9775} "30338640"
        String mysqlTimestamp = "30338640";
        long parsedLongTimestamp2 = (long) Float.parseFloat(mysqlTimestamp);
        assertThat(parsedLongTimestamp2).isEqualTo(result);
    }

    private static void stubInsertThenSucceed(QueryHistoryDao delegate, RuntimeException first)
    {
        doThrow(first)
                .doNothing()
                .when(delegate)
                .insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void buffersOnConnectionExceptionAndFlushesOnStop()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        stubInsertThenSucceed(delegate, new ConnectionException(new SQLException("network error", "08006")));

        Jdbi mockJdbi = mock(Jdbi.class);
        when(mockJdbi.onDemand(QueryHistoryDao.class)).thenReturn(delegate);

        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        writeBufferConfig.setEnabled(true);
        HaQueryHistoryManager manager = new HaQueryHistoryManager(mockJdbi, container.getJdbcUrl().startsWith("jdbc:oracle"), writeBufferConfig);
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
    void rethrowsNonConnectionException()
    {
        QueryHistoryDao delegate = mock(QueryHistoryDao.class);
        doThrow(new IllegalStateException("bad input"))
                .when(delegate)
                .insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());

        Jdbi mockJdbi = mock(Jdbi.class);
        when(mockJdbi.onDemand(QueryHistoryDao.class)).thenReturn(delegate);
        WriteBufferConfiguration writeBufferConfig = new WriteBufferConfiguration();
        writeBufferConfig.setEnabled(true);
        HaQueryHistoryManager manager = new HaQueryHistoryManager(mockJdbi, container.getJdbcUrl().startsWith("jdbc:oracle"), writeBufferConfig);

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
