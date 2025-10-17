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
import io.trino.gateway.ha.HaGatewayTestUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Optional;

import static io.trino.gateway.ha.HaGatewayTestUtils.createOkHttpClient;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAuthenticationFallbacks
{
    private final int routerPort = 21001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        HaGatewayTestUtils.setupOidc(routerPort, "auth/oauth-and-form-test-config.yml", "openid");
    }

    @Test
    void testPrimaryAuth()
            throws Exception
    {
        OkHttpClient httpClient = createOkHttpClient(Optional.empty());
        try (Response response = httpClient.newCall(uiLoginTypeCall().build()).execute()) {
            String body = response.body().string();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode dataNode = jsonNode.get("data");
            assertThat(dataNode.isArray()).isTrue();
            assertThat(dataNode.size()).isEqualTo(2);
            assertThat(dataNode.get(0).asText()).isEqualTo("oauth");
            assertThat(dataNode.get(1).asText()).isEqualTo("form");
        }
    }

    @Test
    void testApiFallbackAuth()
            throws Exception
    {
        OkHttpClient httpClient = createOkHttpClient(Optional.empty());
        Request request = new Request.Builder()
                .url(format("https://localhost:%s/webapp/getAllBackends", routerPort))
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .addHeader("Authorization", "Basic YWRtaW4xOmFkbWluMV9wYXNzd29yZA==") // admin1:admin1_password
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.isSuccessful()).isTrue();
            String body = response.body().string();
            assertThat(body).contains("Successful.");
        }
    }

    private Request.Builder uiLoginTypeCall()
    {
        return new Request.Builder()
                .url(format("https://localhost:%s/loginType", routerPort))
                .post(RequestBody.create("{}", MediaType.parse("application/json")));
    }
}
