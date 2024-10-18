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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static io.airlift.http.client.HttpStatus.fromStatusCode;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.JMX_PATH;

public class ClusterStatsJmxMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsJmxMonitor.class);

    private final String username;
    private final String password;

    public ClusterStatsJmxMonitor(BackendStateConfiguration backendStateConfiguration)
    {
        this.username = backendStateConfiguration.getUsername();
        this.password = backendStateConfiguration.getPassword();
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        log.info("Monitoring cluster stats for backend: %s", backend.getProxyTo());
        ClusterStats.Builder clusterStats = ClusterStatsMonitor.getClusterStatsBuilder(backend);

        // Fetch DiscoveryNodeManager stats
        String discoveryResponse = queryJmx(backend, "trino.metadata:name=DiscoveryNodeManager");
        if (discoveryResponse != null) {
            processDiscoveryNodeManagerStats(discoveryResponse, clusterStats);
        }

        // Fetch QueryManager stats
        String queryResponse = queryJmx(backend, "trino.execution:name=QueryManager");
        if (queryResponse != null) {
            processQueryManagerStats(queryResponse, clusterStats);
        }

        // Set additional fields
        clusterStats.proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup());

        ClusterStats stats = clusterStats.build();
        log.debug("Completed monitoring for backend: %s. Stats: %s", backend.getProxyTo(), stats);
        return stats;
    }

    private void processDiscoveryNodeManagerStats(String response, ClusterStats.Builder clusterStats)
    {
        try {
            JsonNode rootNode = new ObjectMapper().readTree(response);
            JsonNode attributes = rootNode.get("attributes");
            if (attributes.isArray()) {
                for (JsonNode attribute : attributes) {
                    if ("ActiveNodeCount".equals(attribute.get("name").asText())) {
                        int activeNodes = attribute.get("value").asInt();
                        clusterStats.numWorkerNodes(activeNodes)
                                .healthy(activeNodes - 1 > 0);
                        log.debug("Processed DiscoveryNodeManager: ActiveNodeCount = %d, Health = %s",
                                activeNodes, activeNodes - 1 > 0 ? "Healthy" : "Unhealthy");
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            log.error(e, "Error parsing DiscoveryNodeManager stats");
        }
    }

    private void processQueryManagerStats(String response, ClusterStats.Builder clusterStats)
    {
        try {
            JsonNode rootNode = new ObjectMapper().readTree(response);
            JsonNode attributes = rootNode.get("attributes");
            if (attributes.isArray()) {
                int queuedQueries = 0;
                int runningQueries = 0;
                for (JsonNode attribute : attributes) {
                    String name = attribute.get("name").asText();
                    if ("QueuedQueries".equals(name)) {
                        queuedQueries = attribute.get("value").asInt();
                    }
                    else if ("RunningQueries".equals(name)) {
                        runningQueries = attribute.get("value").asInt();
                    }
                }
                clusterStats.queuedQueryCount(queuedQueries).runningQueryCount(runningQueries);
                log.debug("Processed QueryManager: QueuedQueries = %d, RunningQueries = %d",
                        queuedQueries, runningQueries);
            }
        }
        catch (Exception e) {
            log.error(e, "Error parsing QueryManager stats");
        }
    }

    private String queryJmx(ProxyBackendConfiguration backend, String mbeanName)
    {
        String jmxUrl = backend.getProxyTo() + JMX_PATH + "/" + mbeanName;
        log.debug("Querying JMX at URL: %s", jmxUrl);
        OkHttpClient client = new OkHttpClient.Builder().build();

        Request request = new Request.Builder()
                .url(jmxUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        Call call = client.newCall(request);

        try (Response res = call.execute()) {
            if (fromStatusCode(res.code()) == io.airlift.http.client.HttpStatus.OK) {
                log.debug("Successful JMX response for %s", mbeanName);
                return res.body().string();
            }
            else {
                log.error("Failed to fetch JMX data for %s, response code: %d", mbeanName, res.code());
                return null;
            }
        }
        catch (IOException e) {
            log.error(e, "Error querying JMX for %s", mbeanName);
            return null;
        }
    }
}
