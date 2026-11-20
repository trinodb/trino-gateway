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
package io.trino.gateway.ha.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.HaGatewayLauncher;
import io.trino.gateway.ha.HaGatewayTestUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestAuthorization
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    int routerPort = 22000 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(POSTGRESQL, routerPort, "auth/auth-test-config.yml");
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);
    }

    @Test
    void testUnauthorized()
            throws IOException
    {
        try (Response response = makeRequest(Optional.empty())) {
            assertThat(response.code()).isBetween(400, 499);
        }
    }

    @Test
    void testBadCredentials()
            throws IOException
    {
        try (Response response = makeRequest(Optional.of("unknown:unknown"))) {
            assertThat(response.code()).isBetween(400, 499);
        }
    }

    @Test
    void testGoodCredentials()
            throws IOException
    {
        List<String> adminRoleList = getRoles("admin1:admin1_password");
        assertThat(adminRoleList).containsAll(ImmutableList.of("ADMIN", "USER"));

        List<String> userRoleList = getRoles("user1:password_user1");
        assertThat(userRoleList).containsAll(ImmutableList.of("USER", "API"));
    }

    @Test
    void testManagementResourcesRequireAdminOrApiRole()
            throws IOException
    {
        for (String path : ImmutableList.of("/metrics", "/v1/jmx/mbean")) {
            assertThat(getStatusCode(path, Optional.empty())).isEqualTo(403);
            assertThat(getStatusCode(path, Optional.of("unknown:unknown"))).isEqualTo(403);
            assertThat(getStatusCode(path, Optional.of("user2:password_user2"))).isEqualTo(403);
            assertThat(getStatusCode(path, Optional.of("admin1:admin1_password"))).isEqualTo(200);
            assertThat(getStatusCode(path, Optional.of("user1:password_user1"))).isEqualTo(200);
        }
    }

    @Test
    void testPublicResourcesNeedNoAuthentication()
            throws IOException
    {
        assertThat(getStatusCode("/trino-gateway/livez", Optional.empty())).isEqualTo(200);
    }

    private int getStatusCode(String path, Optional<String> credentials)
            throws IOException
    {
        Request.Builder builder = new Request.Builder()
                .url("http://localhost:" + routerPort + path)
                .get();
        credentials.ifPresent(value -> builder.addHeader("Authorization", basicAuthorization(value)));
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            return response.code();
        }
    }

    @Test
    void testFormLogin()
            throws IOException
    {
        assertThat(login("admin1", "admin1_password")).isEqualTo(200);
        assertThat(login("admin1", "wrong_password")).isEqualTo(403);
        assertThat(login("unknown", "admin1_password")).isEqualTo(403);
        assertThat(login("{\"username\":null,\"password\":\"admin1_password\"}")).isEqualTo(403);
        assertThat(login("{\"username\":\"admin1\"}")).isEqualTo(403);
        assertThat(login("null")).isEqualTo(403);
    }

    private int login(String username, String password)
            throws IOException
    {
        return login(objectMapper.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private int login(String body)
            throws IOException
    {
        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/login")
                .post(RequestBody.create(body, MediaType.parse("application/json; charset=utf-8")))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.code();
        }
    }

    private static String basicAuthorization(String credentials)
    {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1));
    }

    private List<String> getRoles(String credentials)
            throws IOException
    {
        try (Response response = makeRequest(Optional.of(credentials))) {
            assertThat(response.code()).isEqualTo(200);

            JsonNode roles = objectMapper.readTree(response.body().string()).get("data").get("roles");
            assertThat(roles.isArray()).isTrue();

            ObjectReader reader = objectMapper.readerFor(new TypeReference<List<String>>() {});
            return reader.readValue(roles.toString());
        }
    }

    private Response makeRequest(Optional<String> credentials)
            throws IOException
    {
        RequestBody emptyRequestBody = RequestBody.create("", MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder()
                .url("http://localhost:" + routerPort + "/userinfo")
                .post(emptyRequestBody);
        if (credentials.isPresent()) {
            String encodedCredentials = "Basic " + Base64.getEncoder().encodeToString(credentials.orElseThrow().getBytes(StandardCharsets.ISO_8859_1));
            credentials.ifPresent(_ -> builder.addHeader("Authorization", encodedCredentials));
        }
        return httpClient.newCall(builder.build()).execute();
    }
}
