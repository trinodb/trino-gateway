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
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.TrinoContainer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestGatewayHaMultipleBackend
{
    public static final String CUSTOM_RESPONSE = "123";
    public static final String CUSTOM_PATH = "/v1/custom/extra";

    private TrinoContainer adhocTrino;
    private TrinoContainer scheduledTrino;

    public static String oauthInitiatePath = "/oauth2";
    public static String oauthCallbackPath = oauthInitiatePath + "/callback";
    public static String oauthInitialResponse = "abc";
    public static String oauthCallbackResponse = "xyz";

    final int routerPort = 20000 + (int) (Math.random() * 1000);
    final int customBackendPort = 21000 + (int) (Math.random() * 1000);

    private final MockWebServer customBackend = new MockWebServer();

    private final OkHttpClient httpClient = new OkHttpClient();

    @BeforeAll
    public void setup()
            throws Exception
    {
        adhocTrino = new TrinoContainer("trinodb/trino");
        adhocTrino.start();
        scheduledTrino = new TrinoContainer("trinodb/trino");
        scheduledTrino.start();

        int backend1Port = adhocTrino.getMappedPort(8080);
        int backend2Port = scheduledTrino.getMappedPort(8080);

        HaGatewayTestUtils.prepareMockBackend(customBackend, customBackendPort, CUSTOM_RESPONSE);

        // seed database
        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");

        // Start Gateway
        String[] args = {"server", testConfig.configFilePath()};
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
    public void testCustomPath()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "abc");
        Request request1 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + CUSTOM_PATH)
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response response1 = httpClient.newCall(request1).execute();
        assertThat(response1.body().string()).isEqualTo(CUSTOM_RESPONSE);
        RecordedRequest recordedRequest = customBackend.takeRequest();
        assertThat(recordedRequest.getRequestLine()).contains("POST");
        assertThat(recordedRequest.getPath()).isEqualTo(CUSTOM_PATH);

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
    public void testQueryDeliveryToMultipleRoutingGroups()
            throws Exception
    {
        // Default request should be routed to adhoc backend
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "SELECT 1");
        Request request1 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .addHeader("X-Trino-User", "test")
                        .post(requestBody)
                        .build();
        Response response1 = httpClient.newCall(request1).execute();
        assertThat(response1.body().string()).contains("http://localhost:" + adhocTrino.getMappedPort(8080));
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
        assertThat(response4.body().string()).contains("http://localhost:" + scheduledTrino.getMappedPort(8080));
    }

    @Test
    public void testBackendConfiguration()
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
    public void testCookieBasedRouting()
            throws IOException
    {
        // This simulates the Trino oauth handshake
        HaGatewayTestUtils.addEndpoint(customBackend, oauthInitiatePath, oauthInitialResponse);
        HaGatewayTestUtils.addEndpoint(customBackend, oauthCallbackPath, oauthCallbackResponse);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        String oauthInitiateBody = "anything";
        RequestBody requestBody =
                RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"), oauthInitiateBody);

        Request initiateRequest =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + oauthInitiatePath)
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response initiateResponse = httpClient.newCall(initiateRequest).execute();
        assertThat(isNullOrEmpty(initiateResponse.header("set-cookie"))).isFalse();

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
                        .url("http://localhost:" + routerPort + "/logout")
                        .post(requestBody)
                        .addHeader("Cookie", initiateResponse.header("set-cookie"))
                        .build();
        Response logoutResponse = httpClient.newCall(logoutRequest).execute();

        assertThat(isNullOrEmpty(logoutResponse.header("set-cookie"))).isFalse();

        Response callbackAfterLogoutResponse = httpClient.newCall(callbackRequest).execute();
        // The request to logout should clear the cookie, so it should now be ignored and the request
        // routed to adhoc, which does not have the oauthCallbackPath
        assertThat(callbackAfterLogoutResponse.code()).isEqualTo(404);
    }

    @AfterAll
    public void cleanup()
    {
        adhocTrino.stop();
        scheduledTrino.stop();
    }
}
