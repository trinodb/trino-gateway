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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoClusterStatsObserver;
import io.trino.gateway.ha.config.ExplainRoutingConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static java.util.Objects.requireNonNull;

public class ExplainRoutingGroupSelector
        implements RoutingGroupSelector, TrinoClusterStatsObserver
{
    private static final Logger log = Logger.get(ExplainRoutingGroupSelector.class);

    private static final String TRINO_USER_HEADER = "X-Trino-User";
    private static final String TRINO_CATALOG_HEADER = "X-Trino-Catalog";
    private static final String TRINO_SCHEMA_HEADER = "X-Trino-Schema";

    private final ExplainRoutingConfiguration config;
    private final ExplainRoutingConfiguration.ExplainConfiguration explainConfig;
    private final ExplainRoutingConfiguration.MetricsThresholds thresholds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, ClusterGroupLoad> loadByRoutingGroup = ImmutableMap.of();

    @Inject
    public ExplainRoutingGroupSelector(HaGatewayConfiguration configuration)
    {
        this(requireNonNull(configuration, "configuration is null").getRoutingRules().getExplainRoutingConfiguration(), HttpClient.newHttpClient());
    }

    public ExplainRoutingGroupSelector(ExplainRoutingConfiguration config)
    {
        this(config, HttpClient.newHttpClient());
    }

    @VisibleForTesting
    ExplainRoutingGroupSelector(ExplainRoutingConfiguration config, HttpClient httpClient)
    {
        this.config = requireNonNull(config, "config is null");
        this.explainConfig = requireNonNull(config.getExplain(), "config.explain is null");
        this.thresholds = requireNonNull(config.getMetricsThresholds(), "config.metricsThresholds is null");
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
    }

    @Override
    public void observe(List<ClusterStats> stats)
    {
        Map<String, MutableClusterGroupLoad> mutable = new HashMap<>();
        for (ClusterStats cluster : stats) {
            String group = cluster.routingGroup();
            if (group == null || group.isBlank()) {
                continue;
            }
            MutableClusterGroupLoad aggregate = mutable.computeIfAbsent(group, key -> new MutableClusterGroupLoad());
            aggregate.runningQueries += cluster.runningQueryCount();
            aggregate.workerNodes += cluster.numWorkerNodes();
            if (config.getCpuMetricName() != null) {
                aggregate.cpuMetricSum += cluster.customMetrics().getOrDefault(config.getCpuMetricName(), 0);
                aggregate.cpuMetricSamples += 1;
            }
            if (config.getMemoryMetricName() != null) {
                aggregate.memoryMetricSum += cluster.customMetrics().getOrDefault(config.getMemoryMetricName(), 0);
                aggregate.memoryMetricSamples += 1;
            }
        }

        Map<String, ClusterGroupLoad> immutable = new HashMap<>();
        for (Map.Entry<String, MutableClusterGroupLoad> entry : mutable.entrySet()) {
            MutableClusterGroupLoad aggregate = entry.getValue();
            double cpu = aggregate.cpuMetricSamples == 0 ? 0 : ((double) aggregate.cpuMetricSum / aggregate.cpuMetricSamples);
            double memory = aggregate.memoryMetricSamples == 0 ? 0 : ((double) aggregate.memoryMetricSum / aggregate.memoryMetricSamples);
            immutable.put(entry.getKey(), new ClusterGroupLoad(aggregate.runningQueries, aggregate.workerNodes, cpu, memory));
        }
        loadByRoutingGroup = ImmutableMap.copyOf(immutable);
    }

    @Override
    public RoutingSelectorResponse findRoutingDestination(HttpServletRequest request)
    {
        TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
        if (trinoQueryProperties == null || !trinoQueryProperties.isNewQuerySubmission()) {
            return new RoutingSelectorResponse(config.getFallbackRoutingGroup());
        }

        String normalizedQueryType = normalizeQueryType(trinoQueryProperties.getResourceGroupQueryType());
        Set<String> eligibleTypes = explainConfig.getQueryTypes();

        if (!eligibleTypes.isEmpty() && !eligibleTypes.contains(normalizedQueryType)) {
            return new RoutingSelectorResponse(config.getDefaultRoutingGroup());
        }

        String sql = trinoQueryProperties.getBody();
        if (sql == null || sql.isBlank()) {
            return new RoutingSelectorResponse(config.getFallbackRoutingGroup());
        }

        QueryExplainStats stats = runExplainAndExtractStats(request, sql);
        String target = isLargeQuery(stats) ? config.getLargeRoutingGroup() : config.getSmallRoutingGroup();
        return new RoutingSelectorResponse(adjustForClusterLoad(target));
    }

    private String adjustForClusterLoad(String candidateRoutingGroup)
    {
        if (!Objects.equals(candidateRoutingGroup, config.getSmallRoutingGroup())) {
            return candidateRoutingGroup;
        }

        ClusterGroupLoad smallGroupLoad = loadByRoutingGroup.get(config.getSmallRoutingGroup());
        if (smallGroupLoad == null) {
            return candidateRoutingGroup;
        }

        boolean smallCpuHot = config.getCpuMetricName() != null && smallGroupLoad.cpuMetric() > config.getMaxSmallClusterCpuPercent();
        boolean smallMemoryHot = config.getMemoryMetricName() != null && smallGroupLoad.memoryMetric() > config.getMaxSmallClusterMemoryPercent();

        if (smallCpuHot || smallMemoryHot) {
            return config.getLargeRoutingGroup();
        }
        return candidateRoutingGroup;
    }

    private boolean isLargeQuery(QueryExplainStats stats)
    {
        return stats.stageCount() >= thresholds.getMinStageCount()
                || stats.nodeCount() >= thresholds.getMinNodeCount()
                || stats.joinCount() >= thresholds.getMinJoinCount()
                || stats.tableScanCount() >= thresholds.getMinTableScanCount()
                || stats.remoteExchangeCount() >= thresholds.getMinRemoteExchangeCount()
                || stats.aggregateCount() >= thresholds.getMinAggregateCount()
                || stats.cpuCost() >= thresholds.getMinCpuCost()
                || stats.memoryCost() >= thresholds.getMinMemoryCost()
                || stats.networkCost() >= thresholds.getMinNetworkCost()
                || stats.outputRowCount() >= thresholds.getMinOutputRowCount()
                || stats.outputSizeInBytes() >= thresholds.getMinOutputSizeInBytes();
    }

    private QueryExplainStats runExplainAndExtractStats(HttpServletRequest request, String sql)
    {
        if (explainConfig.getEndpoint() == null || explainConfig.getEndpoint().isBlank()) {
            return QueryExplainStats.empty();
        }

        String explainQuery = String.format("EXPLAIN (TYPE %s, FORMAT %s) %s", explainConfig.getType(), explainConfig.getFormat(), sql);
        try {
            JsonNode responseNode = executeExplain(request, explainQuery);
            Optional<String> explainJson = extractExplainResult(responseNode, request);
            return explainJson.map(this::parseExplainStats).orElseGet(QueryExplainStats::empty);
        }
        catch (Exception e) {
            log.warn(e, "Failed to run EXPLAIN; falling back to default group");
            return QueryExplainStats.empty();
        }
    }

    private JsonNode executeExplain(HttpServletRequest request, String explainQuery)
            throws IOException, InterruptedException
    {
        HttpRequest explainRequest = buildPostRequest(request, explainQuery, URI.create(explainConfig.getEndpoint()));
        String responseBody = httpClient.send(explainRequest, HttpResponse.BodyHandlers.ofString()).body();
        return objectMapper.readTree(responseBody);
    }

    private Optional<String> extractExplainResult(JsonNode responseNode, HttpServletRequest request)
            throws IOException, InterruptedException
    {
        JsonNode current = responseNode;
        for (int attempt = 0; attempt <= explainConfig.getMaxPolls(); attempt++) {
            if (current != null && current.has("data") && current.get("data").isArray() && !current.get("data").isEmpty()) {
                JsonNode row = current.get("data").get(0);
                if (row != null && row.isArray() && !row.isEmpty() && row.get(0).isTextual()) {
                    return Optional.of(row.get(0).asText());
                }
            }
            if (current != null && current.has("error") && !current.get("error").isNull()) {
                return Optional.empty();
            }
            if (attempt == explainConfig.getMaxPolls() || current == null || !current.has("nextUri")) {
                return Optional.empty();
            }

            URI nextUri = URI.create(current.get("nextUri").asText());
            HttpRequest pollRequest = buildGetRequest(request, nextUri);
            String pollResponse = httpClient.send(pollRequest, HttpResponse.BodyHandlers.ofString()).body();
            current = objectMapper.readTree(pollResponse);
            sleepForPollInterval();
        }
        return Optional.empty();
    }

    private QueryExplainStats parseExplainStats(String explainJson)
    {
        try {
            JsonNode explainNode = objectMapper.readTree(explainJson);
            return QueryExplainStats.of(
                    countText(explainJson, "\"stageId\""),
                    countText(explainJson, "\"nodeType\""),
                    countText(explainJson, "Join"),
                    countText(explainJson, "TableScan"),
                    countText(explainJson, "Remote"),
                    countText(explainJson, "Aggregation"),
                    extractMaxNumber(explainNode, "cpuCost"),
                    extractMaxNumber(explainNode, "memoryCost"),
                    extractMaxNumber(explainNode, "networkCost"),
                    extractMaxNumber(explainNode, "outputRowCount"),
                    extractMaxNumber(explainNode, "outputSizeInBytes"));
        }
        catch (Exception e) {
            log.warn(e, "Unable to parse EXPLAIN response as JSON");
            return QueryExplainStats.empty();
        }
    }

    private HttpRequest buildPostRequest(HttpServletRequest request, String explainQuery, URI uri)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(explainConfig.getTimeoutSeconds()))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(explainQuery));
        applyHeaders(builder, request);
        return builder.build();
    }

    private HttpRequest buildGetRequest(HttpServletRequest request, URI uri)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(explainConfig.getTimeoutSeconds()))
                .GET();
        applyHeaders(builder, request);
        return builder.build();
    }

    private void applyHeaders(HttpRequest.Builder builder, HttpServletRequest request)
    {
        String configuredUser = getEnvValue(explainConfig.getTrinoUserEnv());
        String requestUser = request.getHeader(TRINO_USER_HEADER);
        String user = configuredUser != null ? configuredUser : requestUser;
        if (user != null && !user.isBlank()) {
            builder.header(TRINO_USER_HEADER, user);
        }

        String catalog = request.getHeader(TRINO_CATALOG_HEADER);
        String schema = request.getHeader(TRINO_SCHEMA_HEADER);
        if (catalog != null && !catalog.isBlank()) {
            builder.header(TRINO_CATALOG_HEADER, catalog);
        }
        if (schema != null && !schema.isBlank()) {
            builder.header(TRINO_SCHEMA_HEADER, schema);
        }

        String authValue = getEnvValue(explainConfig.getAuthorizationHeaderEnv());
        if (authValue != null && !authValue.isBlank()) {
            builder.header("Authorization", authValue);
        }
    }

    private static String getEnvValue(String envVar)
    {
        if (envVar == null || envVar.isBlank()) {
            return null;
        }
        return System.getenv(envVar);
    }

    private static String normalizeQueryType(String resourceGroupQueryType)
    {
        if (resourceGroupQueryType == null) {
            return "";
        }
        return resourceGroupQueryType
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static int countText(String source, String token)
    {
        int count = 0;
        int fromIndex = 0;
        while (true) {
            int index = source.indexOf(token, fromIndex);
            if (index < 0) {
                return count;
            }
            count++;
            fromIndex = index + token.length();
        }
    }

    private static double extractMaxNumber(JsonNode node, String fieldName)
    {
        if (node == null) {
            return 0;
        }
        double current = node.has(fieldName) && node.get(fieldName).isNumber() ? node.get(fieldName).asDouble() : 0;
        if (node.isObject()) {
            for (JsonNode child : node) {
                current = Math.max(current, extractMaxNumber(child, fieldName));
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                current = Math.max(current, extractMaxNumber(child, fieldName));
            }
        }
        return current;
    }

    private void sleepForPollInterval()
            throws InterruptedException
    {
        long sleepMillis = Math.max(1, (long) (explainConfig.getPollIntervalSeconds() * 1000));
        Thread.sleep(sleepMillis);
    }

    private record ClusterGroupLoad(int runningQueries, int workerNodes, double cpuMetric, double memoryMetric) {}

    private static class MutableClusterGroupLoad
    {
        private int runningQueries;
        private int workerNodes;
        private long cpuMetricSum;
        private int cpuMetricSamples;
        private long memoryMetricSum;
        private int memoryMetricSamples;
    }

    @VisibleForTesting
    record QueryExplainStats(
            int stageCount,
            int nodeCount,
            int joinCount,
            int tableScanCount,
            int remoteExchangeCount,
            int aggregateCount,
            double cpuCost,
            double memoryCost,
            double networkCost,
            double outputRowCount,
            double outputSizeInBytes)
    {
        static QueryExplainStats empty()
        {
            return new QueryExplainStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        static QueryExplainStats of(
                int stageCount,
                int nodeCount,
                int joinCount,
                int tableScanCount,
                int remoteExchangeCount,
                int aggregateCount,
                double cpuCost,
                double memoryCost,
                double networkCost,
                double outputRowCount,
                double outputSizeInBytes)
        {
            return new QueryExplainStats(
                    stageCount,
                    nodeCount,
                    joinCount,
                    tableScanCount,
                    remoteExchangeCount,
                    aggregateCount,
                    cpuCost,
                    memoryCost,
                    networkCost,
                    outputRowCount,
                    outputSizeInBytes);
        }
    }
}
