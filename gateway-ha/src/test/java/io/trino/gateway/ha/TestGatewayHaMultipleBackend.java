package io.trino.gateway.ha;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class TestGatewayHaMultipleBackend
{
    public static final String EXPECTED_RESPONSE1 = "{\"id\":\"testId1\"}";
    public static final String EXPECTED_RESPONSE2 = "{\"id\":\"testId2\"}";
    public static final String CUSTOM_RESPONSE = "123";
    public static final String CUSTOM_PATH = "/v1/custom/extra";

    final int routerPort = 20000 + (int) (Math.random() * 1000);
    final int backend1Port = 21000 + (int) (Math.random() * 1000);
    final int backend2Port = 21000 + (int) (Math.random() * 1000);
    final int customBackendPort = 21000 + (int) (Math.random() * 1000);

    private final WireMockServer adhocBackend =
            new WireMockServer(WireMockConfiguration.options().port(backend1Port));
    private final WireMockServer scheduledBackend =
            new WireMockServer(WireMockConfiguration.options().port(backend2Port));

    private final WireMockServer customBackend =
            new WireMockServer(WireMockConfiguration.options().port(customBackendPort));

    private final OkHttpClient httpClient = new OkHttpClient();

    @BeforeAll
    public void setup()
            throws Exception
    {
        HaGatewayTestUtils.prepareMockBackend(adhocBackend, "/v1/statement", EXPECTED_RESPONSE1);
        HaGatewayTestUtils.prepareMockBackend(scheduledBackend, "/v1/statement", EXPECTED_RESPONSE2);
        HaGatewayTestUtils.prepareMockBackend(customBackend, CUSTOM_PATH, CUSTOM_RESPONSE);

        // seed database
        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");

        // Start Gateway
        String[] args = {"server", testConfig.getConfigFilePath()};
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
        assertEquals(response1.body().string(), CUSTOM_RESPONSE);

        Request request2 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/invalid")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "custom")
                        .build();
        Response response2 = httpClient.newCall(request2).execute();
        assertEquals(response2.code(), 404);
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
                        .post(requestBody)
                        .build();
        Response response1 = httpClient.newCall(request1).execute();
        assertEquals(EXPECTED_RESPONSE1, response1.body().string());
        // When X-Trino-Routing-Group is set in header, query should be routed to cluster under the
        // routing group
        Request request4 =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/v1/statement")
                        .post(requestBody)
                        .addHeader("X-Trino-Routing-Group", "scheduled")
                        .build();
        Response response4 = httpClient.newCall(request4).execute();
        assertEquals(EXPECTED_RESPONSE2, response4.body().string());
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

        assertNotNull(backendConfiguration);
        assertEquals(3, backendConfiguration.length);
        assertTrue(backendConfiguration[0].isActive());
        assertTrue(backendConfiguration[1].isActive());
        assertTrue(backendConfiguration[2].isActive());
        assertEquals("adhoc", backendConfiguration[0].getRoutingGroup());
        assertEquals("scheduled", backendConfiguration[1].getRoutingGroup());
        assertEquals("custom", backendConfiguration[2].getRoutingGroup());
        assertEquals("externalUrl", backendConfiguration[0].getExternalUrl());
        assertEquals("externalUrl", backendConfiguration[1].getExternalUrl());
        assertEquals("externalUrl", backendConfiguration[2].getExternalUrl());
    }

    @AfterAll
    public void cleanup()
    {
        adhocBackend.stop();
        scheduledBackend.stop();
    }
}
