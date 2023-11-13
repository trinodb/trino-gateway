package io.trino.gateway.ha.clustermonitor;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClusterStatsJdbcMonitor implements ClusterStatsMonitor {
  @Nullable
  private final Properties properties;

  private final BackendStateConfiguration backendStateConfiguration;

  private final String STATE_QUERY = "SELECT state, COUNT(*) as count "
      + "FROM runtime.queries "
      + "WHERE user != ? AND date_diff('hour',created,now()) <= 1 "
      + "GROUP BY state";

  public ClusterStatsJdbcMonitor(BackendStateConfiguration backendStateConfiguration) {
    this.backendStateConfiguration = backendStateConfiguration;
    if (backendStateConfiguration != null) {
      properties = new Properties();
      properties.setProperty("user", backendStateConfiguration.getUsername());
      if (backendStateConfiguration.getPassword() != null) {
        properties.setProperty("password", backendStateConfiguration.getPassword());
      }
      properties.setProperty("SSL", String.valueOf(backendStateConfiguration.getSsl()));
      log.info("state check configured");
    } else {
      log.warn("no state check configured");
      properties = null;
    }
  }

  @Override
  public ClusterStats monitor(ProxyBackendConfiguration backend) {
    String url = backend.getProxyTo();
    ClusterStats clusterStats = new ClusterStats();
    clusterStats.setClusterId(backend.getName());
    String jdbcUrl;
    if (backendStateConfiguration == null) {
      return clusterStats;
    }
    try {
      URL parsedUrl = new URL(url);
      jdbcUrl = String
          .format("jdbc:trino://%s:%s/system",
              parsedUrl.getHost(),
              parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort()
          );
      // automatically set ssl config based on url protocol
      properties.setProperty("SSL", String.valueOf(parsedUrl.getProtocol().equals("https")));
    } catch (MalformedURLException e) {
      log.error("could not parse backend url {} ", url);
      return clusterStats;
    }

    try (Connection conn = DriverManager.getConnection(jdbcUrl, properties)) {
      PreparedStatement stmt = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor())
          .callWithTimeout(() -> conn.prepareStatement(STATE_QUERY), 10, TimeUnit.SECONDS);
      stmt.setString(1, backendStateConfiguration.getUsername());
      Map<String, Integer> partialState = new HashMap<>();
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        partialState.put(rs.getString("state"), rs.getInt("count"));
      }
      clusterStats.setHealthy(true);
      clusterStats.setQueuedQueryCount(partialState.getOrDefault("QUEUED", 0));
      clusterStats.setRunningQueryCount(partialState.getOrDefault("RUNNING", 0));
      return clusterStats;
    } catch (TimeoutException e) {
      log.error("timed out fetching status for {} backend, {}", url, e);
    } catch (Exception e) {
      log.error("could not fetch status for {} backend, {}", url, e);
    }
    return clusterStats;
  }
}
