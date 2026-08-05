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
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.SET_COOKIE;
import static com.google.common.net.HttpHeaders.X_FORWARDED_PROTO;
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
        try (MockWebServer server = startTrinoWebUi(() -> Optional.of(LoginType.AUTH_API))) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            RecordedRequest login = server.takeRequest(5, SECONDS);
            assertThat(login.getPath()).isEqualTo("/ui/auth/login");
            assertThat(login.getHeader(CONTENT_TYPE)).startsWith("application/json");
            assertThat(login.getBody().readUtf8()).isEqualTo("{\"username\":\"probe\",\"password\":\"\"}");
        }
    }

    @Test
    void testFallbackToFormLogin()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(() -> Optional.of(LoginType.FORM))) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            assertThat(server.takeRequest(5, SECONDS).getPath()).isEqualTo("/ui/auth/login");
            RecordedRequest login = server.takeRequest(5, SECONDS);
            assertThat(login.getPath()).isEqualTo("/ui/login");
            assertThat(login.getHeader(CONTENT_TYPE)).startsWith("application/x-www-form-urlencoded");
            assertThat(login.getBody().readUtf8()).isEqualTo("username=probe&password=");
        }
    }

    @Test
    void testDetectedLoginTypeIsReused()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(() -> Optional.of(LoginType.FORM))) {
            ClusterStatsHttpMonitor monitor = createMonitor();
            ProxyBackendConfiguration backend = backend(server);
            monitor.monitor(backend);
            monitor.monitor(backend);

            // Each monitor call logs in once. Only the first login tries the JSON login resource of the
            // current Web UI.
            List<String> paths = recordedPaths(server);
            assertThat(paths).filteredOn("/ui/auth/login"::equals).hasSize(1);
            assertThat(paths).filteredOn("/ui/login"::equals).hasSize(2);
        }
    }

    @Test
    void testLoginFailure()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(Optional::empty)) {
            assertThat(createMonitor().monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.UNKNOWN);

            assertThat(recordedPaths(server)).containsExactly("/ui/auth/login", "/ui/login");
        }
    }

    @Test
    void testLoginTypeIsDetectedAgainAfterUpgrade()
            throws Exception
    {
        AtomicReference<Optional<LoginType>> loginType = new AtomicReference<>(Optional.of(LoginType.FORM));
        try (MockWebServer server = startTrinoWebUi(loginType::get)) {
            ClusterStatsHttpMonitor monitor = createMonitor();
            ProxyBackendConfiguration backend = backend(server);
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
            int requestsBeforeUpgrade = server.getRequestCount();
            takePaths(server, requestsBeforeUpgrade);

            // The cluster is upgraded to a Trino release with the current Web UI
            loginType.set(Optional.of(LoginType.AUTH_API));
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            // The cached form login fails once, the JSON login is detected and then reused
            assertThat(takePaths(server, server.getRequestCount() - requestsBeforeUpgrade)).containsExactly(
                    "/ui/login",
                    "/ui/auth/login",
                    UI_API_STATS_PATH,
                    UI_API_QUEUED_LIST_PATH);
        }
    }

    @Test
    void testRequestFailureKeepsDetectedLoginType()
            throws Exception
    {
        AtomicBoolean disconnect = new AtomicBoolean();
        try (MockWebServer server = startTrinoWebUi(() -> Optional.of(LoginType.FORM), disconnect)) {
            ClusterStatsHttpMonitor monitor = createMonitor();
            ProxyBackendConfiguration backend = backend(server);
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            // The cluster drops every connection for one monitor call
            disconnect.set(true);
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.UNKNOWN);
            disconnect.set(false);
            int requestsBefore = server.getRequestCount();
            takePaths(server, requestsBefore);

            // The failed request says nothing about the login type, so the detected form login is
            // used right away, without trying the JSON login resource first
            assertThat(monitor.monitor(backend).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);
            assertThat(takePaths(server, server.getRequestCount() - requestsBefore)).containsExactly(
                    "/ui/login",
                    UI_API_STATS_PATH,
                    UI_API_QUEUED_LIST_PATH);
        }
    }

    @Test
    void testLoginSendsForwardedProtoHeader()
            throws Exception
    {
        try (MockWebServer server = startTrinoWebUi(() -> Optional.of(LoginType.AUTH_API))) {
            BackendStateConfiguration configuration = createConfiguration();
            configuration.setXForwardedProtoHeader(true);
            assertThat(new ClusterStatsHttpMonitor(configuration).monitor(backend(server)).trinoStatus()).isEqualTo(TrinoStatus.HEALTHY);

            RecordedRequest login = server.takeRequest(5, SECONDS);
            assertThat(login.getPath()).isEqualTo("/ui/auth/login");
            assertThat(login.getHeader(X_FORWARDED_PROTO)).isEqualTo("https");
        }
    }

    private static ClusterStatsHttpMonitor createMonitor()
    {
        return new ClusterStatsHttpMonitor(createConfiguration());
    }

    private static BackendStateConfiguration createConfiguration()
    {
        BackendStateConfiguration configuration = new BackendStateConfiguration();
        configuration.setUsername("probe");
        return configuration;
    }

    private static ProxyBackendConfiguration backend(MockWebServer server)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("mock");
        backend.setProxyTo(server.url("").toString().replaceAll("/$", ""));
        return backend;
    }

    // Serves the Web UI login resource of the current login type, which sets a session cookie, and
    // answers every other path, including the login resource of the other type, with 404. Every 404
    // carries a cookie like a load balancer in front of the cluster would set, which must not count
    // as a login.
    private static MockWebServer startTrinoWebUi(Supplier<Optional<LoginType>> loginTypeSupplier)
            throws IOException
    {
        return startTrinoWebUi(loginTypeSupplier, new AtomicBoolean());
    }

    // While disconnect is set, every connection is closed before the request is read
    private static MockWebServer startTrinoWebUi(Supplier<Optional<LoginType>> loginTypeSupplier, AtomicBoolean disconnect)
            throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse peek()
            {
                if (disconnect.get()) {
                    return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
                }
                return super.peek();
            }

            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                String path = request.getPath();
                Optional<LoginType> loginType = loginTypeSupplier.get();
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
                return new MockResponse()
                        .setResponseCode(404)
                        .addHeader(SET_COOKIE, "AWSALB=sticky; Path=/");
            }
        });
        server.start();
        return server;
    }

    private static List<String> recordedPaths(MockWebServer server)
            throws InterruptedException
    {
        return takePaths(server, server.getRequestCount());
    }

    private static List<String> takePaths(MockWebServer server, int count)
            throws InterruptedException
    {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            paths.add(server.takeRequest(5, SECONDS).getPath());
        }
        return paths;
    }
}
