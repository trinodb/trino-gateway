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

public class ClusterStatsJdbcMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsJdbcMonitor.class);

    private final Properties properties; // TODO Avoid using a mutable field

    private static final String STATE_QUERY = "SELECT state, COUNT(*) as count "
            + "FROM runtime.queries "
            + "WHERE user != ? AND date_diff('hour',created,now()) <= 1 "
            + "GROUP BY state";

    public ClusterStatsJdbcMonitor(BackendStateConfiguration backendStateConfiguration)
    {
        properties = new Properties();
        properties.setProperty("user", backendStateConfiguration.getUsername());
        properties.setProperty("password", backendStateConfiguration.getPassword());
        properties.setProperty("SSL", String.valueOf(backendStateConfiguration.getSsl()));
        log.info("state check configured");
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        String url = backend.getProxyTo();
        ClusterStats.Builder clusterStats = ClusterStats.builder(backend.getName());
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
            log.error("could not parse backend url %s ", url);
            return clusterStats.build(); // TODO Invalid configuration should fail
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, properties)) {
            PreparedStatement stmt = SimpleTimeLimiter.create(Executors.newSingleThreadExecutor())
                    .callWithTimeout(() -> conn.prepareStatement(STATE_QUERY), 10, TimeUnit.SECONDS);
            stmt.setString(1, (String) properties.get("user"));
            Map<String, Integer> partialState = new HashMap<>();
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                partialState.put(rs.getString("state"), rs.getInt("count"));
            }
            return clusterStats
                    .healthy(true)
                    .queuedQueryCount(partialState.getOrDefault("QUEUED", 0))
                    .runningQueryCount(partialState.getOrDefault("RUNNING", 0))
                    .build();
        }
        catch (TimeoutException e) {
            log.error(e, "timed out fetching status for %s backend", url);
        }
        catch (Exception e) {
            log.error(e, "could not fetch status for %s backend", url);
        }
        return clusterStats.build();
    }
}
