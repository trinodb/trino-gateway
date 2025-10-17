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
package io.trino.gateway.ha;

import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import okhttp3.CookieJar;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.trino.gateway.ha.util.ConfigurationUtils.replaceEnvironmentVariables;
import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.testcontainers.utility.MountableFile.forClasspathResource;

public class HaGatewayTestUtils
{
    private static final Logger log = Logger.get(HaGatewayTestUtils.class);
    private static final OkHttpClient httpClient = new OkHttpClient();

    // TODO: TRINO-7103 Set up TMC job to update Trino image version in trino-gateway
    private static final String TRINO_IMAGE_VERSION = "406.186.12";

    private static final String HYDRA_IMAGE = "oryd/hydra:v1.11.10";
    private static final String DSN = "mysql://hydra:mysecretpassword@tcp(hydra-db:3306)/hydra?parseTime=true";

    private static final int TTL_ACCESS_TOKEN_IN_SECONDS = 5;
    private static final int TTL_REFRESH_TOKEN_IN_SECONDS = 15;

    private HaGatewayTestUtils() {}

    public static void prepareMockBackend(
            MockWebServer backend, int customBackendPort, String expectedResponse)
            throws IOException
    {
        backend.start(customBackendPort);
        backend.enqueue(new MockResponse()
                .setBody(expectedResponse)
                .addHeader(CONTENT_ENCODING, PLAIN_TEXT_UTF_8)
                .setResponseCode(200));
    }

    public static void seedRequiredData(String h2DbFilePath)
    {
        String jdbcUrl = "jdbc:h2:" + h2DbFilePath;
        Jdbi jdbi = Jdbi.create(jdbcUrl, "sa", "sa");
        try (Handle handle = jdbi.open()) {
            handle.createUpdate(HaGatewayTestUtils.getResourceFileContent("gateway-ha-persistence-mysql.sql"))
                    .execute();
        }
    }

    public static File buildGatewayConfig(PostgreSQLContainer postgreSqlContainer, int routerPort, String configFile)
            throws Exception
    {
        Map<String, String> additionalVars = ImmutableMap.<String, String>builder()
                .put("REQUEST_ROUTER_PORT", String.valueOf(routerPort))
                .putAll(buildPostgresVars(postgreSqlContainer))
                .buildOrThrow();
        return buildGatewayConfig(configFile, additionalVars);
    }

    public static File buildGatewayConfig(String configFile, Map<String, String> additionalVars)
            throws Exception
    {
        Map<String, String> vars = ImmutableMap.<String, String>builder()
                .put("APPLICATION_CONNECTOR_PORT", String.valueOf(30000 + (int) (Math.random() * 1000)))
                .put("ADMIN_CONNECTOR_PORT", String.valueOf(31000 + (int) (Math.random() * 1000)))
                .put("RESOURCES_DIR", Path.of("src", "test", "resources").toAbsolutePath().toString())
                .putAll(additionalVars)
                .buildOrThrow();
        String configStr = replaceEnvironmentVariables(getResourceFileContent(configFile), vars);

        File target = File.createTempFile("config-" + System.currentTimeMillis(), "config.yaml");

        try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(), UTF_8)) {
            writer.append(configStr);
        }

        log.info("Test Gateway Config \n[%s]", configStr);
        return target;
    }

    public static Map<String, String> buildPostgresVars(PostgreSQLContainer<?> postgresql)
    {
        return ImmutableMap.<String, String>builder()
                .put("POSTGRESQL_JDBC_URL", postgresql.getJdbcUrl())
                .put("POSTGRESQL_USER", postgresql.getUsername())
                .put("POSTGRESQL_PASSWORD", postgresql.getPassword())
                .buildOrThrow();
    }

    public static String getResourceFileContent(String fileName)
    {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream =
                HaGatewayTestUtils.class.getClassLoader().getResourceAsStream(fileName);
        Scanner scn = new Scanner(inputStream, UTF_8);
        while (scn.hasNextLine()) {
            sb.append(scn.nextLine()).append("\n");
        }
        return sb.toString();
    }

    public static void setUpBackend(
            String name,
            String proxyTo,
            String externalUrl,
            boolean active,
            String routingGroup,
            int routerPort)
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(
                        "{ \"name\": \""
                                + name
                                + "\",\"proxyTo\": \""
                                + proxyTo
                                + "\",\"externalUrl\": \""
                                + externalUrl
                                + "\",\"active\": "
                                + active
                                + ",\"routingGroup\": \""
                                + routingGroup
                                + "\"}",
                        MediaType.parse("application/json; charset=utf-8"));
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/entity?entityType=GATEWAY_BACKEND")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.isSuccessful()).isTrue();
        verifyTrinoStatus(routerPort, name);
    }

    public static OracleContainer getOracleContainer()
    {
        // gvenzl/oracle-xe:18.4.0-slim is x86 only, and tests using this image will most likely timeout if
        // run on other CPU architectures. This test should run in three circumstances:
        // * in CI, defined by the presence of GitHub environment variables
        // * if the CPU architecture is x86_64
        // * if they are explicitly enabled by setting TG_RUN_ORACLE_TESTS=true in the test environment
        // reference: https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/store-information-in-variables#default-environment-variables
        assumeTrue("true".equals(System.getenv("GITHUB_ACTIONS"))
                || "x86_64".equalsIgnoreCase(System.getProperty("os.arch"))
                || "true".equals(System.getenv("TG_RUN_ORACLE_TESTS")));
        return new OracleContainer("gvenzl/oracle-xe:18.4.0-slim");
    }

    private static void verifyTrinoStatus(int port, String name)
            throws IOException
    {
        Request getBackendStateRequest = new Request.Builder()
                .url(format("http://localhost:%s/api/public/backends/%s/state", port, name))
                .get()
                .build();

        for (int i = 0; i < 10; i++) {
            try (Response getBackendStateResponse = httpClient.newCall(getBackendStateRequest).execute()) {
                checkState(getBackendStateResponse.isSuccessful());
                JsonCodec<ClusterStats> responseCodec = JsonCodec.jsonCodec(ClusterStats.class);
                ResponseBody getBackendStateResponseBody = requireNonNull(getBackendStateResponse.body(), "getBackendStateResponse.body() is null");
                ClusterStats clusterStats = responseCodec.fromJson(getBackendStateResponseBody.string());
                if (clusterStats.trinoStatus() == TrinoStatus.HEALTHY) {
                    return;
                }
            }
            sleepUninterruptibly(1, TimeUnit.SECONDS);
        }
        throw new IllegalStateException("Trino cluster is not healthy");
    }

    public static TrinoContainer getTrinoContainer()
    {
        TrinoContainer trino = new TrinoContainer(DockerImageName.parse("trinodb/trino:" + TRINO_IMAGE_VERSION));
        trino.withCopyFileToContainer(forClasspathResource("trino_container_init.sh"), "/");
        trino.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("bash", "-c", "./trino_container_init.sh"));
        return trino;
    }

    public static void setupOidc(int routerPort, String configFile, String scopes)
                throws Exception
    {
        PostgreSQLContainer<?> postgresql = createPostgreSqlContainer();
        postgresql.start();
        setupOidcContainers(routerPort, scopes);

        Path localhostJks = Path.of("src", "test", "resources", "auth", "localhost.jks").toAbsolutePath();
        Map<String, String> additionalVars = ImmutableMap.<String, String>builder()
                .put("REQUEST_ROUTER_PORT", String.valueOf(routerPort))
                .put("LOCALHOST_JKS", localhostJks.toString())
                .putAll(buildPostgresVars(postgresql))
                .buildOrThrow();
        File testConfigFile = HaGatewayTestUtils.buildGatewayConfig(configFile, additionalVars);
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);
    }

    public static void setupOidcContainers(int routerPort, String scopes)
    {
        Network network = Network.newNetwork();
        MySQLContainer<?> databaseContainer = new MySQLContainer<>("mysql:8.0.36")
                .withNetwork(network)
                .withNetworkAliases("hydra-db")
                .withUsername("hydra")
                .withPassword("mysecretpassword")
                .withDatabaseName("hydra")
                .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 1));
        databaseContainer.start();

        GenericContainer<?> migrationContainer = new GenericContainer<>(HYDRA_IMAGE)
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
        hydra.start();

        String clientId = "trino_client_id";
        String clientSecret = "trino_client_secret";
        String tokenEndpointAuthMethod = "client_secret_basic";
        String audience = "trino_client_id";
        String callbackUrl = format("https://localhost:%s/oidc/callback", routerPort);
        GenericContainer<?> clientCreatingContainer = new GenericContainer<>(HYDRA_IMAGE)
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
                "--scope", scopes,
                "--token-endpoint-auth-method", tokenEndpointAuthMethod,
                "--callbacks", callbackUrl);
        clientCreatingContainer.start();
    }

    public static OkHttpClient createOkHttpClient(Optional<CookieJar> cookieJar)
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

    private static void setupInsecureSsl(OkHttpClient.Builder clientBuilder)
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
}
