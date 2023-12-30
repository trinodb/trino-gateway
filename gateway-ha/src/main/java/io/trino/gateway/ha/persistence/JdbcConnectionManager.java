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
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import jakarta.annotation.Nullable;
import org.javalite.activejdbc.Base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JdbcConnectionManager {
  private static final Logger log = LoggerFactory.getLogger(JdbcConnectionManager.class);
  private final DataStoreConfiguration configuration;
  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor();

  public JdbcConnectionManager(DataStoreConfiguration configuration) {
    this.configuration = configuration;
    startCleanUps();
  }

  public void open() {
    this.open(null);
  }

  public void open(@Nullable String routingGroupDatabase) {
    String jdbcUrl = configuration.getJdbcUrl();
    if (routingGroupDatabase != null) {
      jdbcUrl = jdbcUrl.substring(0, jdbcUrl.lastIndexOf('/') + 1) + routingGroupDatabase;
    }
    log.debug("Jdbc url is " + jdbcUrl);
    Base.open(
            configuration.getDriver(),
            jdbcUrl,
            configuration.getUser(),
            configuration.getPassword());
    log.debug("Connection opened");
  }

  public void close() {
    Base.close();
    log.debug("Connection closed");
  }

  private void startCleanUps() {
    executorService.scheduleWithFixedDelay(
        () -> {
          log.info("Performing query history cleanup task");
          try {
            this.open();
            QueryHistory.delete(
                "created < ?",
                  System.currentTimeMillis() - TimeUnit.HOURS.toMillis(
                        this.configuration.getQueryHistoryHoursRetention()));
          } finally {
            this.close();
          }
        },
        1,
        120,
        TimeUnit.MINUTES);
  }
}
