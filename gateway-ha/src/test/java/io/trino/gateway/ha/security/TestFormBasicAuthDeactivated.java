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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class TestFormBasicAuthDeactivated
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    @Container
    private static final PostgreSQLContainer POSTGRESQL = createPostgreSqlContainer();

    private final int routerPort = 26001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(POSTGRESQL, routerPort, "auth/form-basic-auth-deactivated-test-config.yml");
        HaGatewayLauncher.main(new String[] {testConfigFile.getAbsolutePath()});
    }

    @Test
    void testBasicAuthRejected()
            throws IOException
    {
        String credentials = "Basic " + Base64.getEncoder().encodeToString("admin1:admin1_password".getBytes(ISO_8859_1));
        try (Response response = httpClient.newCall(userinfoRequest(credentials)).execute()) {
            assertThat(response.code()).isBetween(400, 499);
        }
    }

    @Test
    void testFormLoginAccepted()
            throws IOException
    {
        Request loginRequest = new Request.Builder()
                .url("http://localhost:" + routerPort + "/login")
                .post(RequestBody.create("{\"username\":\"admin1\",\"password\":\"admin1_password\"}", JSON))
                .build();
        String token;
        try (Response response = httpClient.newCall(loginRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
            token = objectMapper.readTree(response.body().string()).get("data").get("token").asText();
        }

        try (Response response = httpClient.newCall(userinfoRequest("Bearer " + token)).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode roles = objectMapper.readTree(response.body().string()).get("data").get("roles");
            assertThat(roles).extracting(JsonNode::asText).contains("ADMIN", "USER");
        }
    }

    private Request userinfoRequest(String authorization)
    {
        return new Request.Builder()
                .url("http://localhost:" + routerPort + "/userinfo")
                .post(RequestBody.create("", JSON))
                .addHeader("Authorization", authorization)
                .build();
    }
}
