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

import io.airlift.log.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
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

    public static void setPathSpecificResponses(
            MockWebServer backend, Map<String, String> pathResponseMap)
    {
        Dispatcher dispatcher = new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                if (pathResponseMap.containsKey(request.getPath())) {
                    return new MockResponse().setResponseCode(200).setBody(pathResponseMap.get(request.getPath()));
                }
                return new MockResponse().setResponseCode(404);
            }
        };
        backend.setDispatcher(dispatcher);
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
                        MediaType.parse("application/json; charset=utf-8"),
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
                                + "\"}");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/entity?entityType=GATEWAY_BACKEND")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.isSuccessful()).isTrue();
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
