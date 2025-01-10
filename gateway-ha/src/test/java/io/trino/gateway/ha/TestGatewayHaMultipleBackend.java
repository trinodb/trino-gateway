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
package io.trino.gateway.ha;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.trino.client.QueryResults;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayCookie;
import io.trino.gateway.ha.router.OAuth2GatewayCookie;
import okhttp3.Cookie;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(Lifecycle.PER_CLASS)
final class TestGatewayHaMultipleBackend
{
    public static final String CUSTOM_RESPONSE = "123";
    public static final String CUSTOM_PATH = "/v1/custom/extra";

    public static final String CUSTOM_LOGOUT = "/custom/logout"; //defined in src/test/resources/test-config-template.yml

    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private TrinoContainer adhocTrino;
    private TrinoContainer scheduledTrino;
    private final PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16");

    public static String oauthInitiatePath = OAuth2GatewayCookie.OAUTH2_PATH;
    public static String oauthCallbackPath = oauthInitiatePath + "/callback";
    public static String oauthInitialResponse = "abc";
    public static String oauthCallbackResponse = "xyz";

    final int routerPort = 20000 + (int) (Math.random() * 1000);
    final int customBackendPort = 21000 + (int) (Math.random() * 1000);

    private final MockWebServer customBackend = new MockWebServer();

    private final OkHttpClient httpClient = new OkHttpClient();

    @BeforeAll
    void setup()
            throws Exception
    {
        adhocTrino = new TrinoContainer("trinodb/trino");
        adhocTrino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        adhocTrino.start();
        scheduledTrino = new TrinoContainer("trinodb/trino");
        scheduledTrino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        scheduledTrino.start();
        postgresql.start();

        int backend1Port = adhocTrino.getMappedPort(8080);
        int backend2Port = scheduledTrino.getMappedPort(8080);

        HaGatewayTestUtils.prepareMockBackend(customBackend, customBackendPort, "default custom response");
        customBackend.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                Map<String, String> pathResponse = ImmutableMap.of(
                        oauthInitiatePath, oauthInitialResponse,
                        oauthCallbackPath, oauthCallbackResponse,
                        CUSTOM_PATH, CUSTOM_RESPONSE,
                        CUSTOM_LOGOUT, "");
                if (pathResponse.containsKey(request.getPath())) {
                    return new MockResponse().setResponseCode(200).setBody(pathResponse.get(request.getPath()));
                }
                if (request.getPath().equals("/v1/info")) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"starting\": false}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        File testConfigFile =
                HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "test-config-template.yml");

        // Start Gateway
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);
        // Now populate the backend
        HaGatewayTestUtils.setUpBackend(
                "trino1", "http://localhost:" + backend1Port, "externalUrl", true, "adhoc", routerPort);
        HaGatewayTestUtils.setUpBackend(
                "trino2", "http://localhost:" + backend2Port, "externalUrl", true, "scheduled",
                routerPort);
        HaGatewayTestUtils.setUpBackend(
                "custom", "http://localhost:" + customBackendPort, "externalUrl", true, "custom",
                routerPort);
    }

    @Test
    void testCustomPath()
            throws Exception
    {
        RequestBody requestBody = RequestBody.create("abc", MEDIA_TYPE);
        Request request1 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + CUSTOM_PATH)
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response response1 = httpClient.newCall(request1).execute();
        assertThat(response1.body().string()).isEqualTo(CUSTOM_RESPONSE);

        Request request2 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/invalid")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response response2 = httpClient.newCall(request2).execute();
        assertThat(response2.code()).isEqualTo(404);
    }

    @Test
    void testQueryDeliveryToMultipleRoutingGroups()
            throws Exception
    {
        // Default request should be routed to adhoc backend
        RequestBody requestBody = RequestBody.create("SELECT 1", MEDIA_TYPE);
        Request request1 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response1 = httpClient.newCall(request1).execute();
        assertThat(response1.body().string()).contains("http://localhost:" + routerPort);
        // When X-Trino-Routing-Group is set in header, query should be routed to cluster under the
        // routing group
        Request request4 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "scheduled")
                        .build();
        Response response4 = httpClient.newCall(request4).execute();
        assertThat(response4.body().string()).contains("http://localhost:" + routerPort);
    }

    @Test
    void testTrinoClusterHostCookie()
            throws Exception
    {
        RequestBody requestBody = RequestBody.create("SELECT 1", MEDIA_TYPE);

        // When X-Trino-Routing-Group is set in header, query should be routed to cluster under the routing group
        Request requestWithoutCookie =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "scheduled")
                        .build();
        Response responseWithoutCookie = httpClient.newCall(requestWithoutCookie).execute();
        assertThat(responseWithoutCookie.body().string()).contains("http://localhost:" + routerPort);
        List<Cookie> cookies = Cookie.parseAll(responseWithoutCookie.request().url(), responseWithoutCookie.headers());
        Cookie clusterHostCookie = cookies.stream().filter(c -> c.name().equals("trinoClusterHost")).collect(onlyElement());
        assertThat(clusterHostCookie.value()).isEqualTo("localhost");

        // test with sending the request which includes trinoClusterHost in the cookie
        // when X-Trino-Routing-Group is set in header, query should be routed to cluster under the routing group
        Request requestWithCookie =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "scheduled")
                        .addHeader("Cookie", "trinoClientHost=foo.example.com")
                        .build();
        Response responseWithCookie = httpClient.newCall(requestWithCookie).execute();
        assertThat(responseWithCookie.body().string()).contains("http://localhost:" + routerPort);
        List<Cookie> overridenCookies = Cookie.parseAll(responseWithCookie.request().url(), responseWithCookie.headers());
        Cookie overridenClusterHostCookie = overridenCookies.stream().filter(c -> c.name().equals("trinoClusterHost")).collect(onlyElement());
        assertThat(overridenClusterHostCookie.value()).isEqualTo("localhost");
    }

    @Test
    void testDeleteQueryId()
            throws IOException
    {
        RequestBody requestBody = RequestBody.create("SELECT 1", MEDIA_TYPE);
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "scheduled")
                        .build();
        Response response = httpClient.newCall(request).execute();
        JsonCodec<QueryResults> responseCodec = JsonCodec.jsonCodec(QueryResults.class);
        QueryResults queryResults = responseCodec.fromJson(response.body().string());

        Request deleteRequest = new Request.Builder()
                .url(queryResults.getNextUri().toURL())
                .delete()
                .build();
        Response deleteResponse = httpClient.newCall(deleteRequest).execute();
        assertThat(deleteResponse.code()).isBetween(200, 204);
    }

    @Test
    void testBackendConfiguration()
            throws Exception
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/entity/GATEWAY_BACKEND")
                .method("GET", null)
                .build();
        Response response = httpClient.newCall(request).execute();

        final ObjectMapper objectMapper = new ObjectMapper();
        ProxyBackendConfiguration[] backendConfiguration =
                objectMapper.readValue(response.body().string(), ProxyBackendConfiguration[].class);

        assertThat(backendConfiguration).isNotNull();
        assertThat(backendConfiguration).hasSize(3);
        assertThat(backendConfiguration[0].isActive()).isTrue();
        assertThat(backendConfiguration[1].isActive()).isTrue();
        assertThat(backendConfiguration[2].isActive()).isTrue();
        assertThat(backendConfiguration[0].getRoutingGroup()).isEqualTo("adhoc");
        assertThat(backendConfiguration[1].getRoutingGroup()).isEqualTo("scheduled");
        assertThat(backendConfiguration[2].getRoutingGroup()).isEqualTo("custom");
        assertThat(backendConfiguration[0].getExternalUrl()).isEqualTo("externalUrl");
        assertThat(backendConfiguration[1].getExternalUrl()).isEqualTo("externalUrl");
        assertThat(backendConfiguration[2].getExternalUrl()).isEqualTo("externalUrl");
    }

    @Test
    void testCookieBasedRouting()
            throws IOException
    {
        // This simulates the Trino oauth handshake
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        String oauthInitiateBody = "anything";
        RequestBody requestBody = RequestBody.create(oauthInitiateBody, MEDIA_TYPE);

        Request initiateRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + oauthInitiatePath)
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response initiateResponse = httpClient.newCall(initiateRequest).execute();
        assertThat(initiateResponse.header("set-cookie")).isNotEmpty();

        Request callbackRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + oauthCallbackPath)
                        .post(requestBody)
                        .addHeader("Cookie", initiateResponse.header("set-cookie"))
                        .build();
        Response callbackResponse = httpClient.newCall(callbackRequest).execute();
        assertThat(callbackResponse.body().string()).isEqualTo(oauthCallbackResponse);

        Request logoutRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + CUSTOM_LOGOUT)
                        .post(requestBody)
                        .addHeader("Cookie", initiateResponse.header("set-cookie"))
                        .build();
        Response logoutResponse = httpClient.newCall(logoutRequest).execute();

        List<Cookie> cookies = Cookie.parseAll(logoutResponse.request().url(), logoutResponse.headers());
        Optional<Cookie> cookie = cookies.stream().filter(c -> c.name().equals(OAuth2GatewayCookie.NAME)).findAny();
        assertThat(cookie).isNotEmpty();
        assertThat(cookie.orElseThrow().value()).isEqualTo("delete");
        // expires-at has been deprecated in favor of max-age. However, okhttp3 does not expose a max-age property,
        // but instead sets expires-at to Long.MIN_VALUE when max-age is set to 0
        // https://github.com/square/okhttp/blob/577d621585f7525d3e98a9161bc26d2965686538/okhttp/src/main/kotlin/okhttp3/Cookie.kt#L673
        assertThat(cookie.orElseThrow().expiresAt()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void testCookieSigning()
            throws IOException
    {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        String oauthInitiateBody = "anything";
        RequestBody requestBody = RequestBody.create(oauthInitiateBody, MEDIA_TYPE);

        Request initiateRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + oauthInitiatePath)
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response initiateResponse = httpClient.newCall(initiateRequest).execute();
        String cookieHeader = initiateResponse.header("set-cookie");
        assertThat(cookieHeader).isNotEmpty();
        List<Cookie> cookies = Cookie.parseAll(initiateResponse.request().url(), initiateResponse.headers());
        Optional<Cookie> cookie = cookies.stream().filter(c -> c.name().equals(OAuth2GatewayCookie.NAME)).findAny();
        assertThat(cookie).isNotEmpty();

        GatewayCookie gatewayCookie = GatewayCookie.CODEC.fromJson(Base64.getUrlDecoder().decode(cookie.orElseThrow().value()));
        assertThat(gatewayCookie.getSignature()).isNotEmpty();

        // Tamper with values. This will cause the cookie to be ignored because its values will not match the signature,
        // causing the request will be routed to the adhoc backend
        gatewayCookie.setTs(gatewayCookie.getTs() + 1000);
        jakarta.servlet.http.Cookie tamperedCookie = gatewayCookie.toCookie();
        Request callbackRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + oauthCallbackPath)
                        .post(requestBody)
                        .addHeader("Cookie", String.format("%s=%s", tamperedCookie.getName(), tamperedCookie.getValue()))
                        .build();
        Response callbackResponse = httpClient.newCall(callbackRequest).execute();
        assertThat(callbackResponse.code()).isEqualTo(500);
    }

    @AfterAll
    void cleanup()
    {
        adhocTrino.stop();
        scheduledTrino.stop();
    }
}
