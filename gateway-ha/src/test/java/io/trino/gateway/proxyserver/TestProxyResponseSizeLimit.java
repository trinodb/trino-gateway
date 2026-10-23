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
package io.trino.gateway.proxyserver;

import io.trino.gateway.ha.HaGatewayLauncher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;

import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.gzip;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * The proxy response size limit, {@code proxy.http-client.max-response-content-length}, applies
 * to the decompressed response, because Trino Gateway decompresses the response to read it.
 * The test configuration sets the limit to 1MB.
 */
@Testcontainers
@TestInstance(PER_CLASS)
final class TestProxyResponseSizeLimit
{
    private static final String RECEIVED_ACCEPT_ENCODING = "X-Received-Accept-Encoding";
    private static final String HEALTH_CHECK_ENDPOINT = "/v1/info";
    private static final String SMALL_ENDPOINT = "/v1/custom/small";
    private static final String LARGE_ENDPOINT = "/v1/custom/large";
    // Both bodies compress to a few kilobytes, below the limit
    private static final String SMALL_BODY = "{\"data\": \"%s\"}".formatted("a".repeat(512 * 1024));
    private static final String LARGE_BODY = "{\"data\": \"%s\"}".formatted("a".repeat(2 * 1024 * 1024));

    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final OkHttpClient httpClient = new OkHttpClient();
    private final int routerPort = 24001 + (int) (Math.random() * 1000);
    private final int backendPort = 25000 + (int) (Math.random() * 1000);
    private final MockWebServer backend = new MockWebServer();

    @BeforeAll
    void setup()
            throws Exception
    {
        backend.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                if (request.getPath().equals(HEALTH_CHECK_ENDPOINT)) {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"starting\": false}");
                }

                String body = switch (request.getPath()) {
                    case SMALL_ENDPOINT -> SMALL_BODY;
                    case LARGE_ENDPOINT -> LARGE_BODY;
                    default -> null;
                };
                if (body == null) {
                    return new MockResponse().setResponseCode(404);
                }
                String acceptEncoding = request.getHeader(ACCEPT_ENCODING);
                MockResponse response = new MockResponse().setResponseCode(200)
                        .setHeader(CONTENT_TYPE, JSON_UTF_8)
                        .setHeader(RECEIVED_ACCEPT_ENCODING, String.valueOf(acceptEncoding));
                if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    return response.setHeader(CONTENT_ENCODING, "gzip").setBody(gzip(body));
                }
                return response.setBody(body);
            }
        });
        backend.start(backendPort);

        File testConfigFile = buildGatewayConfig(POSTGRESQL, routerPort, "test-config-with-response-limit-template.yml");
        HaGatewayLauncher.main(new String[] {testConfigFile.getAbsolutePath()});

        setUpBackend("compression", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @AfterAll
    void cleanup()
            throws Exception
    {
        backend.shutdown();
    }

    @Test
    void testCompressedResponseWithinLimit()
            throws Exception
    {
        try (Response response = httpClient.newCall(request(SMALL_ENDPOINT)).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.header(RECEIVED_ACCEPT_ENCODING)).contains("gzip");
            assertThat(response.body()).isNotNull();
            assertThat(response.body().string()).isEqualTo(SMALL_BODY);
        }
    }

    @Test
    void testCompressedResponseExceedingLimitWhenDecompressed()
            throws Exception
    {
        try (Response response = httpClient.newCall(request(LARGE_ENDPOINT)).execute()) {
            assertThat(response.code()).isEqualTo(502);
        }
    }

    private Request request(String path)
    {
        return new Request.Builder()
                .url("http://localhost:" + routerPort + path)
                .get()
                .header(ACCEPT_ENCODING, "identity")
                .build();
    }
}
