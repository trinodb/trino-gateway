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
import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.MonitorConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;

import java.net.URI;

import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.util.Objects.requireNonNull;

public class ClusterStatsInfoApiMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsInfoApiMonitor.class);
    private static final JsonResponseHandler<ServerInfo> SERVER_INFO_JSON_RESPONSE_HANDLER = createJsonResponseHandler(jsonCodec(ServerInfo.class));
    private final HttpClient client;
    int retries;

    public ClusterStatsInfoApiMonitor(HttpClient client, MonitorConfiguration monitorConfiguration)
    {
        this.client = requireNonNull(client, "client is null");
        retries = monitorConfiguration.getRetries();
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        return ClusterStats.builder(backend.getName()).trinoStatus(checkStatus(backend.getProxyTo()))
                .proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup()).build();
    }

    private TrinoStatus checkStatus(String baseUrl)
    {
        return checkStatus(baseUrl, retries);
    }

    private TrinoStatus checkStatus(String baseUrl, int retriesRemaining)
    {
        Request request = prepareGet()
                .setUri(uriBuilderFrom(URI.create(baseUrl)).appendPath("/v1/info").build())
                .build();
        try {
            ServerInfo serverInfo = client.execute(request, SERVER_INFO_JSON_RESPONSE_HANDLER);
            return serverInfo.isStarting() ? TrinoStatus.PENDING : TrinoStatus.HEALTHY;
        }
        catch (UnexpectedResponseException e) {
            if (shouldRetry(e.getStatusCode())) {
                if (retriesRemaining > 0) {
                    log.warn("Retrying health check on error: %s, ", e.toString());
                    return checkStatus(baseUrl, retriesRemaining - 1);
                }
                else {
                    log.error("Encountered error %s, no retries remaining", e.toString());
                }
            }
            else {
                log.error(e, "Health check failed with non-retryable response. %s", e.toString());
            }
        }
        catch (Exception e) {
            log.error(e, "Exception checking %s for health", request.getUri());
        }
        return TrinoStatus.UNHEALTHY;
    }

    public static boolean shouldRetry(int statusCode)
    {
        switch (statusCode) {
            case HTTP_BAD_GATEWAY:
            case HTTP_UNAVAILABLE:
            case HTTP_GATEWAY_TIMEOUT:
                return true;
            default:
                return false;
        }
    }
}
