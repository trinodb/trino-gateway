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

import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.net.URI;

import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.trino.gateway.ha.clustermonitor.MonitorUtils.shouldRetry;
import static java.util.Objects.requireNonNull;

/**
 * Lightweight liveness monitor that performs an HTTP ping/pong against a
 * backend. It issues a plain {@code GET} to a configurable ping endpoint
 * (default {@code /v1/ping}) and marks the backend {@link TrinoStatus#HEALTHY}
 * on a {@code 200} response, {@link TrinoStatus#UNHEALTHY} otherwise. Unlike
 * the info/UI/JDBC monitors it carries no session or query dependency, so it
 * will not trigger idle-session churn on the backend.
 */
public class ClusterStatsPingMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsPingMonitor.class);
    private final HttpClient client;
    private final String pingPath;
    private final int retries;

    public ClusterStatsPingMonitor(HttpClient client, MonitorConfiguration monitorConfiguration)
    {
        this.client = requireNonNull(client, "client is null");
        this.pingPath = monitorConfiguration.getPingPath();
        this.retries = monitorConfiguration.getRetries();
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        return ClusterStats.builder(backend.getName())
                .trinoStatus(checkStatus(backend.getProxyTo()))
                .proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup())
                .build();
    }

    private TrinoStatus checkStatus(String baseUrl)
    {
        return checkStatus(baseUrl, retries);
    }

    private TrinoStatus checkStatus(String baseUrl, int retriesRemaining)
    {
        try {
            Request request = prepareGet()
                    .setUri(uriBuilderFrom(URI.create(baseUrl)).appendPath(pingPath).build())
                    .build();
            StatusResponse response = client.execute(request, createStatusResponseHandler());
            if (response.getStatusCode() == OK.code()) {
                return TrinoStatus.HEALTHY;
            }
            if (shouldRetry(response.getStatusCode()) && retriesRemaining > 0) {
                log.warn("Retrying ping health check on status %d for %s", response.getStatusCode(), request.getUri());
                return checkStatus(baseUrl, retriesRemaining - 1);
            }
            log.error("Ping health check for %s returned status %d", request.getUri(), response.getStatusCode());
        }
        catch (Exception e) {
            log.error(e, "Exception pinging %s%s for health", baseUrl, pingPath);
        }
        return TrinoStatus.UNHEALTHY;
    }
}
