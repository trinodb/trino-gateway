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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
import static io.airlift.http.client.HttpStatus.fromStatusCode;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_QUEUED_LIST_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_STATS_PATH;
import static java.util.Objects.requireNonNull;

public class ClusterStatsHttpMonitor
        implements ClusterStatsMonitor
{
    private static final Logger log = Logger.get(ClusterStatsHttpMonitor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SESSION_USER = "sessionUser";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private static final String TRINO_UI_COOKIE = "Trino-UI-Token";

    private final String username;
    private final String password;
    private final boolean xForwardedProtoHeader;
    // The login type that last succeeded for each backend, keyed by its proxyTo URL. Detecting it
    // per backend lets one configuration monitor clusters on both sides of Trino 483.
    private final Map<String, LoginType> loginTypes = new ConcurrentHashMap<>();

    public ClusterStatsHttpMonitor(BackendStateConfiguration backendStateConfiguration)
    {
        requireNonNull(backendStateConfiguration, "backendState is null");
        username = requireNonNull(backendStateConfiguration.getUsername(), "backendState username is null");
        password = backendStateConfiguration.getPassword();
        xForwardedProtoHeader = backendStateConfiguration.getXForwardedProtoHeader();
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

    private OkHttpClient acquireClientWithCookie(String proxyTo)
    {
        List<String> attempts = new ArrayList<>();
        LoginType knownLoginType = loginTypes.get(proxyTo);
        try {
            if (knownLoginType != null) {
                Optional<OkHttpClient> client = login(proxyTo, knownLoginType, attempts);
                if (client.isPresent()) {
                    return client.get();
                }
                // The cluster may have moved to a different Trino version, so detect the login type again
                loginTypes.remove(proxyTo);
            }
            for (LoginType loginType : LoginType.values()) {
                if (loginType == knownLoginType) {
                    continue;
                }
                Optional<OkHttpClient> client = login(proxyTo, loginType, attempts);
                if (client.isPresent()) {
                    loginTypes.put(proxyTo, loginType);
                    return client.get();
                }
            }
        }
        catch (IOException e) {
            // A failed request says nothing about the login type, so a known login type stays cached
            log.warn(e, "Login request to %s failed", proxyTo);
            return null;
        }
        log.error(
                "Login to %s failed: %s. Verify the backendState username and password, and that the cluster uses web-ui.authentication.type=FORM",
                proxyTo,
                String.join(", ", attempts));
        return null;
    }

    private Optional<OkHttpClient> login(String proxyTo, LoginType loginType, List<String> attempts)
            throws IOException
    {
        String loginUrl = proxyTo + loginType.path();
        UiApiCookieJar cookieJar = new UiApiCookieJar();
        OkHttpClient client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
        Request loginRequest = new Request.Builder()
                .url(HttpUrl.parse(loginUrl))
                .post(loginBody(loginType))
                .build();

        try (Response response = client.newCall(loginRequest).execute()) {
            // Only the Trino session cookie proves the login, a load balancer in front of the cluster
            // may set its own cookie on any response
            boolean loggedIn = cookieJar.loadForRequest(HttpUrl.parse(loginUrl)).stream()
                    .anyMatch(cookie -> cookie.name().contains(TRINO_UI_COOKIE));
            if (!loggedIn) {
                attempts.add("%s returned %d without a session cookie".formatted(loginType.path(), response.code()));
                return Optional.empty();
            }
            log.debug("Login to %s returned %d", loginUrl, response.code());
            return Optional.of(client);
        }
    }

    private RequestBody loginBody(LoginType loginType)
    {
        return switch (loginType) {
            case AUTH_API -> RequestBody.create(
                    OBJECT_MAPPER.createObjectNode()
                            .put("username", username)
                            .put("password", password)
                            .toString(),
                    JSON_MEDIA_TYPE);
            case FORM -> new FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build();
        };
    }

    private String queryCluster(ProxyBackendConfiguration backend, String path)
    {
        OkHttpClient client = acquireClientWithCookie(backend.getProxyTo());
        if (client == null) {
            log.error("Client received is null");
            return null;
        }

        String targetUrl = backend.getProxyTo() + path;
        Request.Builder requestBuilder = new Request.Builder()
                .url(HttpUrl.parse(targetUrl))
                .get();
        if (xForwardedProtoHeader) {
            requestBuilder.addHeader(X_FORWARDED_PROTO, "https");
        }
        Request request = requestBuilder.build();

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

    // Login resources of the Web UI, in the order they are tried
    enum LoginType
    {
        // Trino 483 and later, which expects a JSON request body
        AUTH_API("/ui/auth/login"),
        // Trino 482 and earlier, which expects a form encoded request body
        FORM("/ui/login");

        private final String path;

        LoginType(String path)
        {
            this.path = path;
        }

        String path()
        {
            return path;
        }
    }
}
