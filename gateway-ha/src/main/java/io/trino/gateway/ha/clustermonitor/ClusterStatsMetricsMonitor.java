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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.security.util.BasicCredentials;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.trino.gateway.ha.clustermonitor.MonitorUtils.shouldRetry;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ClusterStatsMetricsMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsMetricsMonitor.class);

    private final HttpClient httpClient;
    private final int retries;
    private final MetricsResponseHandler metricsResponseHandler;
    private final Header identityHeader;
    private final boolean monitorMtlsEnabled;
    private final String metricsEndpoint;
    private final String runningQueriesMetricName;
    private final String queuedQueriesMetricName;
    private final ImmutableSet<String> metricNames;
    private final Map<String, Float> metricMinimumValues;
    private final Map<String, Float> metricMaximumValues;
    private final boolean xForwardedProtoHeader;

    public ClusterStatsMetricsMonitor(HttpClient httpClient, BackendStateConfiguration backendStateConfiguration, MonitorConfiguration monitorConfiguration)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        retries = monitorConfiguration.getRetries();
        monitorMtlsEnabled = backendStateConfiguration.isMonitorMtlsEnabled();
        if (!monitorMtlsEnabled) {
            if (!isNullOrEmpty(backendStateConfiguration.getPassword())) {
                identityHeader = new Header("Authorization",
                        new BasicCredentials(backendStateConfiguration.getUsername(), backendStateConfiguration.getPassword()).getBasicAuthHeader());
            }
            else {
                identityHeader = new Header("X-Trino-User", backendStateConfiguration.getUsername());
            }
        }
        else {
            identityHeader = null;
        }
        metricsEndpoint = monitorConfiguration.getMetricsEndpoint();
        runningQueriesMetricName = monitorConfiguration.getRunningQueriesMetricName();
        queuedQueriesMetricName = monitorConfiguration.getQueuedQueriesMetricName();
        metricMinimumValues = ImmutableMap.copyOf(monitorConfiguration.getMetricMinimumValues());
        metricMaximumValues = ImmutableMap.copyOf(monitorConfiguration.getMetricMaximumValues());
        metricNames = ImmutableSet.<String>builder()
                .add(runningQueriesMetricName, queuedQueriesMetricName)
                .addAll(metricMinimumValues.keySet())
                .addAll(metricMaximumValues.keySet())
                .build();
        metricsResponseHandler = new MetricsResponseHandler(metricNames);
        xForwardedProtoHeader = backendStateConfiguration.getXForwardedProtoHeader();
    }

    private static ClusterStats getUnhealthyStats(ProxyBackendConfiguration backend)
    {
        return ClusterStats.builder(backend.getName())
                .trinoStatus(TrinoStatus.UNHEALTHY)
                .proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup())
                .build();
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        Map<String, String> metrics = getMetrics(backend.getProxyTo(), retries);
        if (metrics.isEmpty()) {
            log.error("No metrics available for %s!", backend.getName());
            return getUnhealthyStats(backend);
        }

        for (Map.Entry<String, Float> entry : metricMinimumValues.entrySet()) {
            if (!metrics.containsKey(entry.getKey())
                    || Float.parseFloat(metrics.get(entry.getKey())) < entry.getValue()) {
                log.warn("Health metric value below min for cluster %s: %s=%s", backend.getName(), entry.getKey(), metrics.get(entry.getKey()));
                return getUnhealthyStats(backend);
            }
        }

        for (Map.Entry<String, Float> entry : metricMaximumValues.entrySet()) {
            if (!metrics.containsKey(entry.getKey())
                    || Float.parseFloat(metrics.get(entry.getKey())) > entry.getValue()) {
                log.warn("Health metric value over max for cluster %s: %s=%s", backend.getName(), entry.getKey(), metrics.get(entry.getKey()));
                return getUnhealthyStats(backend);
            }
        }
        return ClusterStats.builder(backend.getName())
                .trinoStatus(TrinoStatus.HEALTHY)
                .runningQueryCount((int) Float.parseFloat(metrics.get(runningQueriesMetricName)))
                .queuedQueryCount((int) Float.parseFloat(metrics.get(queuedQueriesMetricName)))
                .proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup())
                .build();
    }

    private Map<String, String> getMetrics(String baseUrl, int retriesRemaining)
    {
        HttpUriBuilder uri = uriBuilderFrom(URI.create(baseUrl)).appendPath(metricsEndpoint);
        for (String metric : metricNames) {
            uri.addParameter("name[]", metric);
        }

        Request.Builder requestBuilder = prepareGet()
                .setUri(uri.build())
                .addHeader("Content-Type", "application/openmetrics-text; version=1.0.0; charset=utf-8");
        if (identityHeader != null) {
            requestBuilder.addHeader(identityHeader.name, identityHeader.value);
        }
        if (xForwardedProtoHeader) {
            requestBuilder.addHeader(X_FORWARDED_PROTO, "https");
        }
        Request request = requestBuilder.build();

        try {
            return httpClient.execute(request, metricsResponseHandler);
        }
        catch (UnexpectedResponseException e) {
            if (shouldRetry(e.getStatusCode())) {
                if (retriesRemaining > 0) {
                    log.warn("Retrying health check on error: %s, ", e.toString());
                    return getMetrics(baseUrl, retriesRemaining - 1);
                }
                log.error("Encountered error %s, no retries remaining", e.toString());
            }
            log.error(e, "Health check failed with non-retryable response.\n%s", e.toString());
        }
        catch (Exception e) {
            log.error(e, "Exception checking %s for health", request.getUri());
        }
        return ImmutableMap.of();
    }

    private static class MetricsResponseHandler
            implements ResponseHandler<Map<String, String>, RuntimeException>
    {
        private final ImmutableSet<String> requiredKeys;

        public MetricsResponseHandler(Set<String> requiredKeys)
        {
            this.requiredKeys = ImmutableSet.copyOf(requiredKeys);
        }

        @Override
        public Map<String, String> handleException(Request request, Exception exception)
                throws RuntimeException
        {
            throw propagate(request, exception);
        }

        @Override
        public Map<String, String> handle(Request request, Response response)
                throws RuntimeException
        {
            try {
                String responseBody = new String(response.getInputStream().readAllBytes(), UTF_8);
                Map<String, String> metrics = Arrays.stream(responseBody.split("\n"))
                        .filter(line -> !line.startsWith("#"))
                        .collect(toImmutableMap(s -> s.split(" ")[0], s -> s.split(" ")[1]));
                if (!metrics.keySet().containsAll(requiredKeys)) {
                    throw new UnexpectedResponseException(
                            format("Request is missing required keys: \n%s\nin response: '%s'", String.join("\n", requiredKeys), responseBody),
                            request,
                            response);
                }
                return metrics;
            }
            catch (IOException e) {
                throw new UnexpectedResponseException(request, response);
            }
        }
    }

    private record Header(String name, String value) {}
}
