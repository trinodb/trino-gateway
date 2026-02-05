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

import io.airlift.json.JsonCodec;
import io.trino.client.QueryResults;
import io.trino.gateway.ha.HaGatewayLauncher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.trino.TrinoContainer;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.trino.gateway.ha.HaGatewayTestUtils.buildGatewayConfig;
import static io.trino.gateway.ha.HaGatewayTestUtils.setUpBackend;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(PER_CLASS)
final class TestProxyRequestHandlerQueryHistoryDisabled
{
    private final OkHttpClient httpClient = new OkHttpClient();
    private TrinoContainer trino;
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();

    private final int routerPort = 22001 + (int) (Math.random() * 1000);

    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private Jdbi jdbi;

    @BeforeAll
    void setup()
            throws Exception
    {
        trino = new TrinoContainer("trinodb/trino:476");
        trino.withCopyFileToContainer(forClasspathResource("trino-config.properties"), "/etc/trino/config.properties");
        trino.start();

        int backendPort = trino.getMappedPort(8080);

        postgresql.start();

        File testConfigFile = buildGatewayConfig(postgresql, routerPort, "test-config-with-query-history-disabled.yml");

        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        setUpBackend("trino", "http://localhost:" + backendPort, "externalUrl", true, "adhoc", routerPort);

        jdbi = Jdbi.create(postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());
    }

    @AfterAll
    void cleanup()
    {
        trino.close();
    }

    @Test
    void testQueryHistoryNotRecordedWhenDisabled()
            throws Exception
    {
        String url = "http://localhost:" + routerPort + "/v1/statement";
        String testQuery = "SELECT 1";
        RequestBody requestBody = RequestBody.create(testQuery, MEDIA_TYPE);

        Request postRequest = new Request.Builder()
                .url(url)
                .addHeader("X-Trino-User", "test-user")
                .post(requestBody)
                .build();

        String queryId;
        try (Response response = httpClient.newCall(postRequest).execute()) {
            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.body()).isNotNull();
            String responseBody = response.body().string();

            JsonCodec<QueryResults> responseCodec = JsonCodec.jsonCodec(QueryResults.class);
            QueryResults queryResults = responseCodec.fromJson(responseBody);
            queryId = queryResults.getId();
            assertThat(queryId).isNotNull();
        }

        // Wait a bit for async query history submission (if it were to happen)
        sleepUninterruptibly(2, SECONDS);

        // Verify that query history was NOT recorded in the database
        List<Map<String, Object>> queryHistory = jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM query_history WHERE query_id = :queryId")
                        .bind("queryId", queryId)
                        .mapToMap()
                        .list());

        assertThat(queryHistory).isEmpty();
    }
}
