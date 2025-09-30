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

import static java.util.concurrent.TimeUnit.SECONDS;

public class ClusterStatsJdbcMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsJdbcMonitor.class);

    private final Properties properties; // TODO Avoid using a mutable field
    private final Duration queryTimeout;

    private static final String STATE_QUERY = "SELECT state, COUNT(*) as count "
            + "FROM runtime.queries "
            + "WHERE user != ? AND date_diff('hour',created,now()) <= 1 "
            + "GROUP BY state";

    public ClusterStatsJdbcMonitor(BackendStateConfiguration backendStateConfiguration, MonitorConfiguration monitorConfiguration)
    {
        properties = new Properties();
        properties.setProperty("user", backendStateConfiguration.getUsername());
        properties.setProperty("password", backendStateConfiguration.getPassword());
        properties.setProperty("SSL", String.valueOf(backendStateConfiguration.getSsl()));
        // explicitPrepare is a valid property for Trino versions >= 431. To avoid compatibility
        // issues with versions < 431, this property is left unset when explicitPrepare=true, which is the default
        if (!monitorConfiguration.isExplicitPrepare()) {
            properties.setProperty("explicitPrepare", "false");
        }
        queryTimeout = monitorConfiguration.getQueryTimeout();
        log.info("state check configured");
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        String url = backend.getProxyTo();
        ClusterStats.Builder clusterStats = ClusterStatsMonitor.getClusterStatsBuilder(backend);
        String jdbcUrl;
        try {
            URL parsedUrl = new URL(url);
            jdbcUrl = String
                    .format("jdbc:trino://%s:%s/system",
                            parsedUrl.getHost(),
                            parsedUrl.getPort() == -1 ? parsedUrl.getDefaultPort() : parsedUrl.getPort());
            // automatically set ssl config based on url protocol
            properties.setProperty("SSL", String.valueOf(parsedUrl.getProtocol().equals("https")));
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid backend URL: " + url, e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, properties);
                PreparedStatement statement = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor()).callWithTimeout(
                        () -> conn.prepareStatement(STATE_QUERY), 10, SECONDS)) {
            statement.setString(1, (String) properties.get("user"));
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
