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
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static io.airlift.http.client.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.Objects.requireNonNull;

public class ClusterStatsInfoApiMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = LoggerFactory.getLogger(ClusterStatsInfoApiMonitor.class);
    private static final JsonResponseHandler<ServerInfo> SERVER_INFO_JSON_RESPONSE_HANDLER = createJsonResponseHandler(jsonCodec(ServerInfo.class));
    private final HttpClient client;

    public ClusterStatsInfoApiMonitor(HttpClient client)
    {
        this.client = requireNonNull(client, "client is null");
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        return ClusterStats.builder(backend.getName()).healthy(isReadyStatus(backend.getProxyTo()))
                .proxyTo(backend.getProxyTo())
                .externalUrl(backend.getExternalUrl())
                .routingGroup(backend.getRoutingGroup()).build();
    }

    private boolean isReadyStatus(String baseUrl)
    {
        Request request = prepareGet()
                .setUri(uriBuilderFrom(URI.create(baseUrl)).appendPath("/v1/info").build())
                .build();

        try {
            ServerInfo serverInfo = client.execute(request, SERVER_INFO_JSON_RESPONSE_HANDLER);
            return !serverInfo.isStarting();
        }
        catch (Exception e) {
            log.error("Exception checking {} for health: {} ", request.getUri(), e.getMessage());
        }
        return false;
    }
}
