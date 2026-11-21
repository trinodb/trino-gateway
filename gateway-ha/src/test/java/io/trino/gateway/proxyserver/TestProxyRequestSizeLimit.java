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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.gzip;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/// The request size limit, `proxyRequestConfiguration.requestSize`, applies to the decompressed
/// request body. The test configuration sets the limit to 1MB.
@Testcontainers
@TestInstance(PER_CLASS)
final class TestProxyRequestSizeLimit
{
    private static final String HEALTH_CHECK_ENDPOINT = "/v1/info";
    private static final String STATEMENT_ENDPOINT = "/v1/statement";
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");
    private static final String SMALL_QUERY = "SELECT '%s'".formatted("a".repeat(512 * 1024));
    private static final String LARGE_QUERY = "SELECT '%s'".formatted("a".repeat(2 * 1024 * 1024));

    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final OkHttpClient httpClient = new OkHttpClient();
    private final int routerPort = 26001 + (int) (Math.random() * 1000);
    private final int backendPort = 27000 + (int) (Math.random() * 1000);
    private final MockWebServer backend = new MockWebServer();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

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
                if (request.getPath().equals(STATEMENT_ENDPOINT)) {
                    receivedBody.set(request.getBody().readUtf8());
                    return new MockResponse().setResponseCode(200)
                            .setHeader(CONTENT_TYPE, JSON_UTF_8)
                            .setBody("{\"id\": \"20261118_000000_00000_abcde\"}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        backend.start(backendPort);

        File testConfigFile = buildGatewayConfig(POSTGRESQL, routerPort, "test-config-with-request-limit-template.yml");
        HaGatewayLauncher.main(new String[] {testConfigFile.getAbsolutePath()});

        setUpBackend("request-limit", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);
    }

    @AfterAll
    void cleanup()
            throws Exception
    {
        backend.shutdown();
    }

    @Test
    void testRequestWithinLimit()
            throws Exception
    {
        try (Response response = httpClient.newCall(statement(RequestBody.create(SMALL_QUERY, TEXT), false)).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
        assertThat(receivedBody.get()).isEqualTo(SMALL_QUERY);
    }

    @Test
    void testRequestExceedingLimit()
            throws Exception
    {
        try (Response response = httpClient.newCall(statement(RequestBody.create(LARGE_QUERY, TEXT), false)).execute()) {
            assertThat(response.code()).isEqualTo(413);
        }
    }

    @Test
    void testCompressedRequestExceedingLimitWhenDecompressed()
            throws Exception
    {
        // The compressed body is a few kilobytes, below the limit
        RequestBody body = RequestBody.create(gzip(LARGE_QUERY).readByteString(), TEXT);
        try (Response response = httpClient.newCall(statement(body, true)).execute()) {
            assertThat(response.code()).isEqualTo(413);
        }
    }

    private Request statement(RequestBody body, boolean compressed)
    {
        Request.Builder request = new Request.Builder()
                .url("http://localhost:" + routerPort + STATEMENT_ENDPOINT)
                .post(body);
        if (compressed) {
            request.header(CONTENT_ENCODING, "gzip");
        }
        return request.build();
    }
}
