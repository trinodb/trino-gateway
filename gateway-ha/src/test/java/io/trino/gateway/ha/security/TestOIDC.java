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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.HaGatewayTestUtils;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.HaGatewayTestUtils.createOkHttpClient;
import static io.trino.gateway.ha.security.OidcCookie.OIDC_COOKIE;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestOIDC
{
    private static final int ROUTER_PORT = 21001 + (int) (Math.random() * 1000);

    @BeforeAll
    void setup()
            throws Exception
    {
        HaGatewayTestUtils.setupOidc(ROUTER_PORT, "auth/oauth-test-config.yml", "openid,offline");
    }

    @Test
    void testNormalFlow()
            throws Exception
    {
        OkHttpClient httpClient = createOkHttpClient(Optional.empty());
        String redirectURL;
        try (Response response = httpClient.newCall(uiCall().build()).execute()) {
            assertThat(response.header("Set-Cookie")).isNotNull();
            assertThat(response.header("Set-Cookie")).contains(OIDC_COOKIE);
            redirectURL = extractRedirectURL(response.body().string());
            assertThat(redirectURL).contains("http://localhost:4444/");
        }
        Request oidcRequest = new Request.Builder()
                .url(redirectURL)
                .get()
                .build();
        try (Response response = httpClient.newCall(oidcRequest).execute()) {
            assertThat(response.request().url().host()).isEqualTo("localhost");
            assertThat(response.request().url().port()).isEqualTo(ROUTER_PORT);
            assertThat(response.request().url().encodedPath()).isEqualTo("/trino-gateway");
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    void testInvalidFlow()
            throws Exception
    {
        OkHttpClient httpClient = createOkHttpClient(Optional.empty());

        String redirectURL;
        try (Response response = httpClient.newCall(uiCall().build()).execute()) {
            redirectURL = extractRedirectURL(response.body().string());
            assertThat(redirectURL).contains("http://localhost:4444/");
        }

        Request oidcRequest = new Request.Builder()
                .url(redirectURL)
                .get()
                .build();
        OkHttpClient httpClientBadCookie = createOkHttpClient(Optional.of(new BadCookieJar()));
        try (Response response = httpClientBadCookie.newCall(oidcRequest).execute()) {
            assertThat(response.request().url().host()).isEqualTo("localhost");
            assertThat(response.request().url().port()).isEqualTo(ROUTER_PORT);
            assertThat(response.code()).isEqualTo(401);
        }
    }

    private Request.Builder uiCall()
    {
        return new Request.Builder()
                .url(format("https://localhost:%s/sso", ROUTER_PORT))
                .post(RequestBody.create("", null));
    }

    public static class BadCookieJar
            implements CookieJar
    {
        private JavaNetCookieJar cookieJar;

        public BadCookieJar()
        {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            cookieJar = new JavaNetCookieJar(cookieManager);
        }

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies)
        {
            cookieJar.saveFromResponse(url, cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url)
        {
            if (url.host().equals("localhost") && url.port() == ROUTER_PORT) {
                Cookie cookie = new Cookie.Builder()
                        .name(OIDC_COOKIE)
                        .value("BAD_STATE|BAD_NONCE")
                        .domain("localhost")
                        .build();
                return List.of(cookie);
            }
            else {
                return cookieJar.loadForRequest(url);
            }
        }
    }

    private static String extractRedirectURL(String body)
            throws JsonProcessingException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(body);
        return jsonNode.get("data").asText();
    }
}
