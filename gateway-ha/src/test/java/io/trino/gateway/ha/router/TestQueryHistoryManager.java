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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trino.gateway.ha.HaGatewayTestUtils;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryHistoryManager {
  private QueryHistoryManager queryHistoryManager;

  @BeforeAll
  public void setUp() {
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    File tempH2DbDir = new File(baseDir, "h2db-" + System.currentTimeMillis());
    tempH2DbDir.deleteOnExit();
    HaGatewayTestUtils.seedRequiredData(
        new HaGatewayTestUtils.TestConfig("", tempH2DbDir.getAbsolutePath()));
    String jdbcUrl = "jdbc:h2:" + tempH2DbDir.getAbsolutePath();
    DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4);
    JdbcConnectionManager connectionManager = new JdbcConnectionManager(db);
    queryHistoryManager = new HaQueryHistoryManager(connectionManager) {
    };
  }


  @Test
  public void testSubmitAndFetchQueryHistory() {
    List<QueryHistoryManager.QueryDetail> queryDetails =
        queryHistoryManager.fetchQueryHistory(Optional.empty());
    assertEquals(0, queryDetails.size());
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
    assertTrue(queryDetails.get(0).getCaptureTime() > queryDetails.get(1).getCaptureTime());
    // All queries when user is empty
    assertEquals(3, queryDetails.size());

    queryDetails = queryHistoryManager.fetchQueryHistory(Optional.of("other-user"));
    // Only 1 query when user is 'other-user'
    assertEquals(1, queryDetails.size());

  }
}
