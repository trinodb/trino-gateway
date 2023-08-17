package com.lyft.data.gateway.ha.router;

import com.lyft.data.gateway.ha.config.BackendStateConfiguration;
import com.lyft.data.gateway.ha.config.ProxyBackendConfiguration;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public class BackendStateManager {
  @Nullable
  private final Properties properties;
  @Nullable
  private final BackendStateConfiguration configuration;

  private static final String[] STATE_TYPES = {"QUEUED", "RUNNING"};
  private static final String STATE_QUERY;

  static {
    STATE_QUERY = "SELECT state, COUNT(*) as count "
          + "FROM runtime.queries "
          + "WHERE user != ? AND date_diff('hour',created,now()) <= 1 "
          + "GROUP BY state";
  }

  public BackendStateManager(BackendStateConfiguration configuration) {
    this.configuration = configuration;
    if (configuration != null) {
      properties = new Properties();
      properties.setProperty("user", configuration.getUsername());
      if (configuration.getPassword() != null) {
        properties.setProperty("password", configuration.getPassword());
      }
      properties.setProperty("SSL", String.valueOf(configuration.getSsl()));
      log.info("state check configured");
    } else {
      log.warn("no state check configured");
      properties = null;
    }
  }

  public Optional<BackendState> getBackendState(ProxyBackendConfiguration backend) {
    String url = backend.getProxyTo();
    String name = backend.getName();
    String jdbcUrl;
    if (configuration == null) {
      return Optional.empty();
    }
    try {
      URL parsedUrl = new URL(url);
      jdbcUrl = String
            .format("jdbc:trino://%s:%s/system", parsedUrl.getHost(), parsedUrl.getPort());
    } catch (MalformedURLException e) {
      log.error("could not parse backend url {} ", url);
      return Optional.empty();
    }
    try (Connection conn = DriverManager.getConnection(jdbcUrl, properties)) {
      PreparedStatement stmt = conn.prepareStatement(STATE_QUERY);
      stmt.setString(1, configuration.getUsername());
      Map<String, Integer> partialState = new HashMap<>();
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        partialState.put(rs.getString("state"), rs.getInt("count"));
      }
      Map<String, Integer> state = Arrays
            .stream(STATE_TYPES)
            .collect(Collectors.toMap(t -> t,t -> partialState.getOrDefault(t,0)));
      return Optional.of(new BackendState(name, state));
    } catch (SQLException e) {
      log.error("could not fetch status for {} backend, {}", url, e);
    }
    return Optional.empty();
  }

  @Data
  public static class BackendState {
    private final String name;
    private final Map<String, Integer> state;

    public BackendState(String name, Map<String, Integer> state) {
      this.name = name;
      this.state = state;
    }
  }
}
