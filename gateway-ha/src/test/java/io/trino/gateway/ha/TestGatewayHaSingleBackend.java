package io.trino.gateway.ha;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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

@TestInstance(Lifecycle.PER_CLASS)
public class TestGatewayHaSingleBackend {
  public static final String EXPECTED_RESPONSE = "{\"id\":\"testId\"}";
  int backendPort = 20000 + (int) (Math.random() * 1000);
  int routerPort = 21000 + (int) (Math.random() * 1000);

  private WireMockServer backend =
      new WireMockServer(WireMockConfiguration.options().port(backendPort));
  private final OkHttpClient httpClient = new OkHttpClient();

  @BeforeAll
  public void setup() throws Exception {
    HaGatewayTestUtils.prepareMockBackend(backend, "/v1/statement", EXPECTED_RESPONSE);

    // seed database
    HaGatewayTestUtils.TestConfig testConfig =
        HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");
    // Start Gateway
    String[] args = {"server", testConfig.getConfigFilePath()};
    HaGatewayLauncher.main(args);
    // Now populate the backend
    HaGatewayTestUtils.setUpBackend(
        "trino1", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
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

  @AfterAll
  public void cleanup() {
    backend.stop();
  }
}
