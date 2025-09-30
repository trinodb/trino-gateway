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
package io.trino.gateway.ha.clustermonitor;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
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
import java.util.concurrent.TimeoutException;

import com.google.common.collect.ImmutableMap;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ClusterStatsJdbcMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsJdbcMonitor.class);

    private final ImmutableMap<String, String> properties; // Replaced mutable Properties with ImmutableMap
    private final JdbcMonitorConfiguration jdbcConfig;
    private final Duration queryTimeout;

    private static final String STATE_QUERY = "SELECT state, COUNT(*) as count "
            + "FROM runtime.queries "
            + "WHERE user != ? AND date_diff('hour',created,now()) <= 1 "
            + "GROUP BY state";

    public ClusterStatsJdbcMonitor(BackendStateConfiguration backendStateConfiguration, MonitorConfiguration monitorConfiguration)
    {
        this.jdbcConfig = JdbcMonitorConfiguration.from(backendStateConfiguration, monitorConfiguration);
        queryTimeout = monitorConfiguration.getQueryTimeout();

        // Create immutable properties map to avoid mutable field anti-pattern
        Properties baseProperties = jdbcConfig.toProperties();
        ImmutableMap.Builder<String, String> propertiesBuilder = ImmutableMap.builder();
        for (String key : baseProperties.stringPropertyNames()) {
            propertiesBuilder.put(key, baseProperties.getProperty(key));
        }
        this.properties = propertiesBuilder.build();

        log.info("state check configured");
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        String url = backend.getProxyTo();
        ClusterStats.Builder clusterStats = ClusterStatsMonitor.getClusterStatsBuilder(backend);
        String jdbcUrl;
        Properties connectionProperties;
        try {
            URL parsedUrl = new URL(url);
            jdbcUrl = String
                    .format("jdbc:trino://%s:%s/system",
                            parsedUrl.getHost(),
                            parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort());

            // Create connection properties from immutable map
            connectionProperties = new Properties();
            connectionProperties.putAll(properties);
            connectionProperties.setProperty("SSL", String.valueOf(parsedUrl.getProtocol().equals("https")));
        }
        catch (MalformedURLException e) {
            log.error("Invalid backend URL configuration: %s", url);
            throw new IllegalArgumentException("Invalid backend URL: " + url, e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, connectionProperties);
                PreparedStatement statement = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor()).callWithTimeout(
                        () -> conn.prepareStatement(STATE_QUERY), 10, SECONDS)) {
            statement.setString(1, jdbcConfig.getUsername());
            statement.setQueryTimeout((int) queryTimeout.roundTo(SECONDS));
            Map<String, Integer> partialState = new HashMap<>();
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                partialState.put(rs.getString("state"), rs.getInt("count"));
            }
            return clusterStats
                    // at this point we can set cluster to trinoStatus because otherwise
                    // it wouldn't have gotten worker stats
                    .trinoStatus(TrinoStatus.HEALTHY)
                    .queuedQueryCount(partialState.getOrDefault("QUEUED", 0))
                    .runningQueryCount(partialState.getOrDefault("RUNNING", 0))
                    .build();
        }
        catch (TimeoutException e) {
            log.error(e, "Timed out fetching status for %s backend", url);
        }
        catch (Exception e) {
            log.error(e, "Could not fetch status for %s backend", url);
        }
        return clusterStats.build();
    }
}
