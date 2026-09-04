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
 * Verifies that a configured authentication method is always accepted even when it is not
 * listed in {@code authentication.defaultType}. The config here uses the single-value (scalar)
 * form {@code defaultType: "oauth"} (bound as a one-element list) alongside a configured
 * {@code form} block, mirroring an existing deployment that upgrades to multi-auth:
 *
 * <ul>
 *   <li>{@code /loginType} advertises only {@code oauth} — the login page shows SSO as the
 *       primary method and does not surface the unlisted form.</li>
 *   <li>the {@code ChainedAuthFilter} still accepts form/basic, so automation using Basic
 *       auth keeps working. Before the membership fix this request would 403, since the chain
 *       was built solely from the {@code defaultType} list.</li>
 * </ul>
 */
@TestInstance(Lifecycle.PER_CLASS)
final class TestConfiguredMethodAlwaysAccepted
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();

    private final int routerPort = 26001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        postgresql.start();
        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(postgresql, routerPort, "auth/oauth-primary-form-configured-test-config.yml");
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);
    }

    @Test
    void testLoginTypeAdvertisesOnlyListedPrimary()
            throws IOException
    {
        // form is configured but not listed in defaultType, so the login page shows only oauth.
        try (Response response = httpClient.newCall(loginTypeRequest()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode data = objectMapper.readTree(response.body().string()).get("data");
            assertThat(data.isArray()).isTrue();
            assertThat(data.size()).isEqualTo(1);
            assertThat(data.get(0).asText()).isEqualTo("oauth");
        }
    }

    @Test
    void testBasicAuthAuthenticatesForUnlistedFormMethod()
            throws IOException
    {
        // oauth is the only listed (and shown) method, but the configured form block keeps
        // Basic auth accepted by the chain. This is the backward-compatibility guarantee.
        try (Response response = httpClient.newCall(userinfoRequest("admin1:admin1_password")).execute()) {
            assertThat(response.code()).isEqualTo(200);
            JsonNode roles = objectMapper.readTree(response.body().string()).get("data").get("roles");
            assertThat(roles.isArray()).isTrue();
            assertThat(roles).extracting(JsonNode::asText).contains("ADMIN", "USER");
        }
    }

    @Test
    void testBasicAuthRejectsBadCredentials()
            throws IOException
    {
        try (Response response = httpClient.newCall(userinfoRequest("unknown:unknown")).execute()) {
            assertThat(response.code()).isBetween(400, 499);
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
