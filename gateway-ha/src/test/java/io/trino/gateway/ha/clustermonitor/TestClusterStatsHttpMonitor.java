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

import io.trino.gateway.ha.clustermonitor.ClusterStatsHttpMonitor.LoginType;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.SET_COOKIE;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_QUEUED_LIST_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_STATS_PATH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

final class TestClusterStatsHttpMonitor
{
    private static final String STATS = "{\"activeWorkers\":1,\"queuedQueries\":0,\"runningQueries\":0}";

    @Test
    void testAuthApiLogin()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(Optional.of(LoginType.AUTH_API))) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            RecordedRequest login = server.takeRequest(5, SECONDS);
            assertThat(login.getPath()).isEqualTo("/ui/auth/login");
            assertThat(login.getHeader(CONTENT_TYPE)).startsWith("application/json");
            // Reading the body requires okio, which is not a declared dependency, so compare its size
            assertThat(login.getBodySize()).isEqualTo("{\"username\":\"probe\",\"password\":\"\"}".length());
        }
    }

    @Test
    void testFallbackToFormLogin()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(Optional.of(LoginType.FORM))) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            assertThat(server.takeRequest(5, SECONDS).getPath()).isEqualTo("/ui/auth/login");
            RecordedRequest login = server.takeRequest(5, SECONDS);
            assertThat(login.getPath()).isEqualTo("/ui/login");
            assertThat(login.getHeader(CONTENT_TYPE)).startsWith("application/x-www-form-urlencoded");
            assertThat(login.getBodySize()).isEqualTo("username=probe&password=".length());
        }
    }

    @Test
    void testDetectedLoginTypeIsReused()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(Optional.of(LoginType.FORM))) {
            ClusterStatsHttpMonitor monitor = createMonitor();
            ProxyBackendConfiguration backend = backend(server);
            monitor.monitor(backend);
            monitor.monitor(backend);

            // Each monitor call logs in once for the cluster stats and once for the queued queries.
            // Only the first login tries the JSON login resource of the current Web UI.
            List<String> paths = recordedPaths(server);
            assertThat(paths).filteredOn("/ui/auth/login"::equals).hasSize(1);
            assertThat(paths).filteredOn("/ui/login"::equals).hasSize(4);
        }
    }

    @Test
    void testLoginFailure()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(Optional.empty())) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.UNKNOWN);

            assertThat(recordedPaths(server)).containsExactly("/ui/auth/login", "/ui/login");
        }
    }

    private static ClusterStatsHttpMonitor createMonitor()
    {
        BackendStateConfiguration configuration = new BackendStateConfiguration();
        configuration.setUsername("probe");
        return new ClusterStatsHttpMonitor(configuration);
    }

    private static ProxyBackendConfiguration backend(MockWebServer server)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("mock");
        backend.setProxyTo(server.url("").toString().replaceAll("/$", ""));
        return backend;
    }

    // Serves the Web UI login resource of the given login type, which sets a session cookie, and
    // answers every other path, including the login resource of the other type, with 404
    private static MockWebServer startTrinoWebUi(Optional<LoginType> loginType)
            throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                String path = request.getPath();
                if (loginType.isPresent() && loginType.get().path().equals(path)) {
                    return new MockResponse()
                            .setResponseCode(204)
                            .addHeader(SET_COOKIE, "Trino-UI-Token=token; Path=/ui");
                }
                if (UI_API_STATS_PATH.equals(path)) {
                    return new MockResponse().setBody(STATS);
                }
                if (UI_API_QUEUED_LIST_PATH.equals(path)) {
                    return new MockResponse().setBody("[]");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        return server;
    }

    private static List<String> recordedPaths(MockWebServer server)
            throws InterruptedException
    {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < server.getRequestCount(); i++) {
            paths.add(server.takeRequest(5, SECONDS).getPath());
        }
        return paths;
    }
}
