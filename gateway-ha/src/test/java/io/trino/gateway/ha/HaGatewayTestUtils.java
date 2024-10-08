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

import com.google.common.base.Stopwatch;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;
import java.util.Scanner;

import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class HaGatewayTestUtils
{
    private static final Logger log = Logger.get(HaGatewayTestUtils.class);
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Random RANDOM = new Random();

    private HaGatewayTestUtils() {}

    public static void seedRequiredData(TestConfig testConfig)
    {
        String jdbcUrl = "jdbc:h2:" + testConfig.h2DbFilePath();
        Jdbi jdbi = Jdbi.create(jdbcUrl, "sa", "sa");
        try (Handle handle = jdbi.open()) {
            handle.createUpdate(HaGatewayTestUtils.getResourceFileContent("gateway-ha-persistence-mysql.sql"))
                    .execute();
        }
    }

    public static void prepareMockBackend(
            MockWebServer backend, int customBackendPort, String expectedResonse)
            throws IOException
    {
        backend.start(customBackendPort);
        backend.enqueue(new MockResponse()
                .setBody(expectedResonse)
                .addHeader(CONTENT_ENCODING, PLAIN_TEXT_UTF_8)
                .setResponseCode(200));
    }

    public static TestConfig buildGatewayConfigAndSeedDb(int routerPort, String configFile)
            throws Exception
    {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        File tempH2DbDir = new File(baseDir, "h2db-" + RANDOM.nextInt() + System.currentTimeMillis());
        tempH2DbDir.deleteOnExit();

        URL resource = HaGatewayTestUtils.class.getClassLoader().getResource("auth/localhost.jks");
        String configStr =
                getResourceFileContent(configFile)
                        .replace("REQUEST_ROUTER_PORT", String.valueOf(routerPort))
                        .replace("DB_FILE_PATH", tempH2DbDir.getAbsolutePath())
                        .replace(
                                "APPLICATION_CONNECTOR_PORT", String.valueOf(30000 + (int) (Math.random() * 1000)))
                        .replace("ADMIN_CONNECTOR_PORT", String.valueOf(31000 + (int) (Math.random() * 1000)))
                        .replace("LOCALHOST_JKS", Paths.get(resource.toURI()).toFile().getAbsolutePath())
                        .replace("RESOURCES_DIR", Paths.get("src", "test", "resources").toFile().getAbsolutePath());

        File target = File.createTempFile("config-" + System.currentTimeMillis(), "config.yaml");

        FileWriter fw = new FileWriter(target, UTF_8);
        fw.append(configStr);
        fw.flush();
        log.info("Test Gateway Config \n[%s]", configStr);
        TestConfig testConfig = new TestConfig(target.getAbsolutePath(), tempH2DbDir.getAbsolutePath());
        seedRequiredData(testConfig);
        return testConfig;
    }

    public static String getResourceFileContent(String fileName)
    {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream =
                HaGatewayTestUtils.class.getClassLoader().getResourceAsStream(fileName);
        Scanner scn = new Scanner(inputStream, UTF_8);
        while (scn.hasNextLine()) {
            sb.append(scn.nextLine()).append("\n");
        }
        return sb.toString();
    }

    public static void setUpBackend(
            String name,
            String proxyTo,
            String externalUrl,
            boolean active,
            String routingGroup,
            int routerPort)
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(
                        "{ \"name\": \""
                                + name
                                + "\",\"proxyTo\": \""
                                + proxyTo
                                + "\",\"externalUrl\": \""
                                + externalUrl
                                + "\",\"active\": "
                                + active
                                + ",\"routingGroup\": \""
                                + routingGroup
                                + "\"}",
                        MediaType.parse("application/json; charset=utf-8"));
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/entity?entityType=GATEWAY_BACKEND")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.isSuccessful()).isTrue();
        TrinoStatus newTrinoStatus = TrinoStatus.PENDING;
        Stopwatch stopwatch = Stopwatch.createStarted();
        // pull cluster health states for 10 seconds
        // It should be enough as the healthcheck is run every second
        Duration timeout = Duration.ofSeconds(10);
        while (newTrinoStatus != TrinoStatus.HEALTHY && stopwatch.elapsed().compareTo(timeout) < 0) {
            // check the state of newly added cluster every second
            Request getBackendStateRequest = new Request.Builder()
                    .url(format("http://localhost:%s/api/public/backends/%s/state", routerPort, name))
                    .get()
                    .build();
            try (Response getBackendStateResponse = httpClient.newCall(getBackendStateRequest).execute()) {
                if (getBackendStateResponse.isSuccessful()) {
                    JsonCodec<ClusterStats> responseCodec = JsonCodec.jsonCodec(ClusterStats.class);
                    ResponseBody getBackendStateResponseBody = getBackendStateResponse.body();
                    if (getBackendStateResponseBody != null) {
                        ClusterStats clusterStats = responseCodec.fromJson(getBackendStateResponseBody.string());
                        newTrinoStatus = clusterStats.trinoStatus();
                        log.debug("status for new trino cluster %s is %s", name, newTrinoStatus);
                    }
                }
            }
            Thread.sleep(1000);
        }
        assertThat(newTrinoStatus).isEqualTo(TrinoStatus.HEALTHY);
    }

    public record TestConfig(String configFilePath, String h2DbFilePath)
    {
        public TestConfig
        {
            requireNonNull(configFilePath, "configFilePath is null");
            requireNonNull(h2DbFilePath, "h2DbFilePath is null");
        }
    }
}
