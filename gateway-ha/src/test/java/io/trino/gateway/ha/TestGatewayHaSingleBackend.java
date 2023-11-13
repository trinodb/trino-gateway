package io.trino.gateway.ha;

import static io.trino.gateway.ha.HaGatewayTestUtils.WAIT_FOR_BACKEND_IN_SECONDS;
import static io.trino.gateway.ha.HaGatewayTestUtils.prepareMockGetBackend;
import static io.trino.gateway.ha.HaGatewayTestUtils.prepareMockPostBackend;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_QUEUED_LIST_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_LOGIN_PATH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import lombok.extern.slf4j.Slf4j;
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
import org.junit.jupiter.api.extension.ExtendWith;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport.class)
@Slf4j
public class TestGatewayHaSingleBackend {
  public static final String EXPECTED_RESPONSE = "{\"id\":\"testId\"}";
  int backendPort = 20000 + (int) (Math.random() * 1000);
  int routerPort = 21000 + (int) (Math.random() * 1000);

  private WireMockServer backend =
      new WireMockServer(WireMockConfiguration.options().port(backendPort));
  private final OkHttpClient httpClient = new OkHttpClient();

  @BeforeAll
  public void setup() throws Exception {
    backend.start();
    prepareMockPostBackend(backend, "/v1/statement", EXPECTED_RESPONSE, 200);
    prepareMockPostBackend(backend, UI_LOGIN_PATH, "", 200);
    prepareMockGetBackend(backend, UI_API_STATS_PATH, "{\"activeWorkers\": 1}", 200);
    prepareMockGetBackend(backend, UI_API_QUEUED_LIST_PATH, null, 200);

    // seed database
    HaGatewayTestUtils.TestConfig testConfig =
        HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");
    // Start Gateway
    String[] args = {"server", testConfig.getConfigFilePath()};
    HaGatewayLauncher.main(args);
    // Now populate the backend
    setUpBackend("trino1", "http://localhost:" + backendPort,"externalUrl",true, "adhoc", routerPort);

    log.info("waiting for backend to become healthy");
    SECONDS.sleep(WAIT_FOR_BACKEND_IN_SECONDS);
  }

  @Test
  public void testRequestDelivery() throws Exception {
    RequestBody requestBody =
        RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "SELECT 1");
    Request request =
        new Request.Builder()
            .url("http://localhost:" + routerPort + "/v1/statement")
            .post(requestBody)
            .build();
    Response response = httpClient.newCall(request).execute();
    assertEquals(EXPECTED_RESPONSE, response.body().string());
  }

  @Test
  public void testBackendConfiguration() throws Exception {
    Request request = new Request.Builder()
            .url("http://localhost:" + routerPort + "/entity/GATEWAY_BACKEND")
            .method("GET", null)
            .build();
    Response response = httpClient.newCall(request).execute();

    final ObjectMapper objectMapper = new ObjectMapper();
    ProxyBackendConfiguration[] backendConfiguration =
            objectMapper.readValue(response.body().string(), ProxyBackendConfiguration[].class);

    assertNotNull(backendConfiguration);
    assertEquals(1, backendConfiguration.length);
    assertTrue(backendConfiguration[0].isActive());
    assertEquals("adhoc", backendConfiguration[0].getRoutingGroup());
    assertEquals("externalUrl", backendConfiguration[0].getExternalUrl());
  }

  @AfterAll
  public void cleanup() {
    backend.stop();
  }
}
