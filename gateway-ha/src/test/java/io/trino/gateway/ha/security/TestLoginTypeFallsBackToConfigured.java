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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the edge case where the only method listed in {@code authentication.defaultType}
 * has no configuration block, while a different method is configured but unlisted (here the
 * scalar {@code defaultType: "oauth"} with only a {@code form} block). The login page must
 * fall back to the configured method rather than throw — the whole gateway would otherwise
 * fail to boot because {@code LoginResource} resolves {@code /loginType} in its constructor.
 * The fact that {@link BeforeAll} boots successfully is itself part of the assertion:
 * {@code /loginType} advertises {@code form}, and form/basic auth still authenticates.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class TestLoginTypeFallsBackToConfigured
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();

    private final int routerPort = 27001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        postgresql.start();
        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "auth/oauth-scalar-form-only-test-config.yml");
        String[] args = {testConfigFile.getAbsolutePath()};
        // Before the fallback fix this call threw from LoginResource's constructor and the
        // gateway never started; a successful boot here is part of what this test guards.
        HaGatewayLauncher.main(args);
    }

    @Test
    void testLoginTypeFallsBackToConfiguredForm()
            throws IOException
    {
        // oauth is the only listed method but has no oauth block; the login page falls back to
        // the configured form method instead of leaving the page empty (or crashing at boot).
        try (Response response = httpClient.newCall(loginTypeRequest()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode data = objectMapper.readTree(response.body().string()).get("data");
            assertThat(data.isArray()).isTrue();
            assertThat(data.size()).isEqualTo(1);
            assertThat(data.get(0).asText()).isEqualTo("form");
        }
    }

    @Test
    void testFormFallbackStillAuthenticates()
            throws IOException
    {
        try (Response response = httpClient.newCall(userinfoRequest("admin1:admin1_password")).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode roles = objectMapper.readTree(response.body().string()).get("data").get("roles");
            assertThat(roles.isArray()).isTrue();
            assertThat(roles).extracting(JsonNode::asText).contains("ADMIN", "USER");
        }
    }

    private Request loginTypeRequest()
    {
        return new Request.Builder()
                .url("http://localhost:" + routerPort + "/loginType")
                .post(RequestBody.create("{}", MediaType.parse("application/json; charset=utf-8")))
                .build();
    }

    private Request userinfoRequest(String credentials)
    {
        String encodedCredentials = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1));
        return new Request.Builder()
                .url("http://localhost:" + routerPort + "/userinfo")
                .post(RequestBody.create("", MediaType.parse("application/json; charset=utf-8")))
                .addHeader("Authorization", encodedCredentials)
                .build();
    }
}
