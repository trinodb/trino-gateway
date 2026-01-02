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
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.TestingJdbcConnectionManager.createTestingJdbcConnectionManager;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
abstract class BaseTestQueryHistoryManager
{
    protected final JdbcDatabaseContainer<?> container = startContainer();
    private QueryHistoryManager queryHistoryManager;

    protected abstract JdbcDatabaseContainer<?> startContainer();

    @BeforeAll
    void setUp()
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                container.getDriverClassName(),
                4,
                true);
        FlywayMigration.migrate(config);
        JdbcConnectionManager jdbcConnectionManager = createTestingJdbcConnectionManager(config);
        queryHistoryManager = new HaQueryHistoryManager(jdbcConnectionManager.getJdbi(), config);
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
        queryDetail.setBackendUrl("http://localhost:9999");
        queryDetail.setSource("sqlWorkbench");
        queryDetail.setUser("test@ea.com");
        queryDetail.setQueryText("select 1");
        for (int i = 0; i < 2; i++) {
            queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
            queryDetail.setCaptureTime(System.currentTimeMillis());
            queryHistoryManager.submitQueryDetail(queryDetail);
        }

        //Add a query from other user
        queryDetail.setUser("other-user");
        queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(queryDetail);

        queryDetails = queryHistoryManager.fetchQueryHistory(Optional.empty());
        assertThat(queryDetails.get(0).getCaptureTime() > queryDetails.get(1).getCaptureTime()).isTrue();
        // All queries when user is empty
        assertThat(queryDetails).hasSize(3);

        queryDetails = queryHistoryManager.fetchQueryHistory(Optional.of("other-user"));
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

        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setBackendUrl("http://localhost:9999");
        queryDetail.setSource("sqlWorkbench");
        queryDetail.setUser("test@ea.com");
        queryDetail.setQueryText("select 1");
        queryDetail.setQueryId(String.valueOf(System.currentTimeMillis()));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        queryHistoryManager.submitQueryDetail(queryDetail);

        // Should return 1 entry
        resList = queryHistoryManager.findDistribution(currentTime);
        assertThat(resList).hasSize(1);
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
}
