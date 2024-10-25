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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.http.client.HttpStatus;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.http.client.HttpStatus.fromStatusCode;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_QUEUED_LIST_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_LOGIN_PATH;
import static java.util.Objects.requireNonNull;

public class ClusterStatsHttpMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsHttpMonitor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SESSION_USER = "sessionUser";

    private final String username;
    private final String password;

    public ClusterStatsHttpMonitor(BackendStateConfiguration backendStateConfiguration)
    {
        username = backendStateConfiguration.getUsername();
        password = backendStateConfiguration.getPassword();
    }

    @Override
    public ClusterStats monitor(ProxyBackendConfiguration backend)
    {
        ClusterStats.Builder clusterStats = ClusterStatsMonitor.getClusterStatsBuilder(backend);
        // Fetch Cluster level Stats.
        String response = queryCluster(backend, UI_API_STATS_PATH);
        if (isNullOrEmpty(response)) {
            log.error("Received null/empty response for %s", UI_API_STATS_PATH);
            return clusterStats.build();
        }

        try {
            HashMap<String, Object> result = OBJECT_MAPPER.readValue(response, new TypeReference<>() {});
            int activeWorkers = (int) result.get("activeWorkers");
            clusterStats
                    .numWorkerNodes(activeWorkers)
                    .queuedQueryCount((int) result.get("queuedQueries"))
                    .runningQueryCount((int) result.get("runningQueries"))
                    .trinoStatus(activeWorkers > 0 ? TrinoStatus.HEALTHY : TrinoStatus.UNHEALTHY)
                    .proxyTo(backend.getProxyTo())
                    .externalUrl(backend.getExternalUrl())
                    .routingGroup(backend.getRoutingGroup());
        }
        catch (Exception e) {
            log.error(e, "Error parsing cluster stats from [%s]", response);
        }

        // Fetch User Level Stats.
        Map<String, Integer> clusterUserStats = new HashMap<>();
        response = queryCluster(backend, UI_API_QUEUED_LIST_PATH);
        if (isNullOrEmpty(response)) {
            log.error("Received null/empty response for %s", UI_API_QUEUED_LIST_PATH);
            return clusterStats.build();
        }
        try {
            List<Map<String, Object>> queries = OBJECT_MAPPER.readValue(response, new TypeReference<>() {});
            for (Map<String, Object> query : queries) {
                String user = (String) query.get(SESSION_USER);
                clusterUserStats.put(user, clusterUserStats.getOrDefault(user, 0) + 1);
            }
        }
        catch (Exception e) {
            log.error(e, "Error parsing cluster user stats");
        }
        return clusterStats.userQueuedCount(clusterUserStats).build();
    }

    private OkHttpClient acquireClientWithCookie(String loginUrl)
    {
        UiApiCookieJar cookieJar = new UiApiCookieJar();
        OkHttpClient client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();
        Request loginRequest = new Request.Builder()
                .url(HttpUrl.parse(loginUrl))
                .post(formBody)
                .build();

        Call call = client.newCall(loginRequest);

        try (Response res = call.execute()) {
            log.info("login request received response code %d", res.code());
            return client;
        }
        catch (IOException e) {
            log.warn(e, "login request failed");
        }
        return null;
    }

    private String queryCluster(ProxyBackendConfiguration backend, String path)
    {
        String loginUrl = backend.getProxyTo() + UI_LOGIN_PATH;
        OkHttpClient client = acquireClientWithCookie(loginUrl);
        if (client == null) {
            log.error("Client received is null");
            return null;
        }

        String targetUrl = backend.getProxyTo() + path;
        Request request = new Request.Builder()
                .url(HttpUrl.parse(targetUrl))
                .get()
                .build();

        Call call = client.newCall(request);

        try (Response res = call.execute()) {
            return switch (fromStatusCode(res.code())) {
                case HttpStatus.OK -> requireNonNull(res.body(), "body is null").string();
                case HttpStatus.UNAUTHORIZED -> {
                    log.info("Unauthorized to fetch cluster stats");
                    log.debug("username: %s, targetUrl: %s, cookieStore: %s", username, targetUrl, client.cookieJar().loadForRequest(HttpUrl.parse(targetUrl)));
                    yield null;
                }
                default -> null;
            };
        }
        catch (IOException e) {
            log.warn(e, "Failed to fetch cluster stats");
        }
        return null;
    }
}
