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
import io.trino.gateway.ha.HaGatewayLauncher;
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
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.security.OidcCookie.OIDC_COOKIE;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestOIDC
{
    private static final int TTL_ACCESS_TOKEN_IN_SECONDS = 5;
    private static final int TTL_REFRESH_TOKEN_IN_SECONDS = 15;

    private static final String HYDRA_IMAGE = "oryd/hydra:v1.10.6";
    private static final String DSN = "postgres://hydra:mysecretpassword@hydra-db:5432/hydra?sslmode=disable";
    private static final int ROUTER_PORT = 21001 + (int) (Math.random() * 1000);

    @BeforeAll
    public void setup()
            throws Exception
    {
        Network network = Network.newNetwork();

        PostgreSQLContainer<?> databaseContainer = new PostgreSQLContainer<>("postgres:16")
                .withNetwork(network)
                .withNetworkAliases("hydra-db")
                .withUsername("hydra")
                .withPassword("mysecretpassword")
                .withDatabaseName("hydra")
                .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 1));
        databaseContainer.start();

        GenericContainer migrationContainer = new GenericContainer(HYDRA_IMAGE)
                .withNetwork(network)
                .withCommand("migrate", "sql", "--yes", DSN)
                .dependsOn(databaseContainer)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy());
        migrationContainer.start();

        FixedHostPortGenericContainer<?> hydraConsent = new FixedHostPortGenericContainer<>("python:3.10.1-alpine")
                .withFixedExposedPort(3000, 3000)
                .withNetwork(network)
                .withNetworkAliases("hydra-consent")
                .withExposedPorts(3000)
                .withCopyFileToContainer(forClasspathResource("auth/login_and_consent_server.py"), "/")
                .withCommand("python", "/login_and_consent_server.py")
                .waitingFor(Wait.forHttp("/healthz").forPort(3000).forStatusCode(200));
        hydraConsent.start();

        FixedHostPortGenericContainer<?> hydra = new FixedHostPortGenericContainer<>(HYDRA_IMAGE)
                .withFixedExposedPort(4444, 4444)
                .withFixedExposedPort(4445, 4445)
                .withNetwork(network)
                .withNetworkAliases("hydra")
                .withEnv("LOG_LEVEL", "debug")
                .withEnv("LOG_LEAK_SENSITIVE_VALUES", "true")
                .withEnv("OAUTH2_EXPOSE_INTERNAL_ERRORS", "1")
                .withEnv("GODEBUG", "http2debug=1")
                .withEnv("DSN", DSN)
                .withEnv("URLS_SELF_ISSUER", "http://localhost:4444/")
                .withEnv("URLS_CONSENT", "http://localhost:3000/consent")
                .withEnv("URLS_LOGIN", "http://localhost:3000/login")
                .withEnv("STRATEGIES_ACCESS_TOKEN", "jwt")
                .withEnv("TTL_ACCESS_TOKEN", TTL_ACCESS_TOKEN_IN_SECONDS + "s")
                .withEnv("TTL_REFRESH_TOKEN", TTL_REFRESH_TOKEN_IN_SECONDS + "s")
                .withEnv("OAUTH2_ALLOWED_TOP_LEVEL_CLAIMS", "groups")
                .withCommand("serve", "all", "--dangerous-force-http")
                .dependsOn(hydraConsent, migrationContainer)
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forLogMessage(".*Setting up http server on :4444.*", 1))
                        .withStrategy(Wait.forLogMessage(".*Setting up http server on :4445.*", 1)))
                .withStartupTimeout(java.time.Duration.ofMinutes(3));

        String clientId = "trino_client_id";
        String clientSecret = "trino_client_secret";
        String tokenEndpointAuthMethod = "client_secret_basic";
        String audience = "trino_client_id";
        String callbackUrl = format("https://localhost:%s/oidc/callback", ROUTER_PORT);
        GenericContainer clientCreatingContainer = new GenericContainer(HYDRA_IMAGE)
                .withNetwork(network)
                .dependsOn(hydra)
                .withCommand("clients", "create",
                        "--endpoint", "http://hydra:4445",
                        "--skip-tls-verify",
                        "--id", clientId,
                        "--secret", clientSecret,
                        "--audience", audience,
                        "-g", "authorization_code,refresh_token,client_credentials",
                        "-r", "token,code,id_token",
                        "--scope", "openid,offline",
                        "--token-endpoint-auth-method", tokenEndpointAuthMethod,
                        "--callbacks", callbackUrl);
        clientCreatingContainer.start();

        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(ROUTER_PORT, "auth/oauth-test-config.yml");
        String[] args = {testConfig.configFilePath()};
        System.out.println(ROUTER_PORT);
        HaGatewayLauncher.main(args);
    }

    @Test
    public void testNormalFlow()
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
    public void testInvalidFlow()
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

    public static void setupInsecureSsl(OkHttpClient.Builder clientBuilder)
            throws Exception
    {
        X509TrustManager trustAllCerts = new X509TrustManager()
        {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
            {
                throw new UnsupportedOperationException("checkClientTrusted should not be called");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
            {
                // skip validation of server certificate
            }

            @Override
            public X509Certificate[] getAcceptedIssuers()
            {
                return new X509Certificate[0];
            }
        };

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[] {trustAllCerts}, new SecureRandom());

        clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), trustAllCerts);
        clientBuilder.hostnameVerifier((hostname, session) -> true);
    }

    public class BadCookieJar
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

    private static OkHttpClient createOkHttpClient(Optional<CookieJar> cookieJar)
            throws Exception
    {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .followRedirects(true)
                .cookieJar(cookieJar.orElseGet(() -> {
                    CookieManager cookieManager = new CookieManager();
                    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                    return new JavaNetCookieJar(cookieManager);
                }));
        setupInsecureSsl(httpClientBuilder);
        return httpClientBuilder.build();
    }
}
