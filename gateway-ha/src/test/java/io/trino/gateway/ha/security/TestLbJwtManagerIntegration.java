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

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.airlift.log.Logger;
import io.trino.gateway.ha.HaGatewayLauncher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for LbJwtManager that tests JWT authentication
 * in a running gateway instance.
 * <p>
 * This test creates a real JWT token and tests it against a running gateway
 * to ensure end-to-end JWT authentication works properly.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class TestLbJwtManagerIntegration
{
    private static final Logger log = Logger.get(TestLbJwtManagerIntegration.class);

    private static final String TEST_ISSUER = "trino-gateway-integration-test";
    private static final String TEST_AUDIENCE = "trino-integration";
    private static final String TEST_SUBJECT = "integration-test-user";

    @TempDir
    static Path tempDir;

    private final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17")
            .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 1));
    private int routerPort = 21002 + (int) (Math.random() * 1000);
    private OkHttpClient httpClient;
    private KeyPair keyPair;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private Algorithm rsaAlgorithm;

    @BeforeAll
    void setUp()
            throws Exception
    {
        // Start PostgreSQL container and wait for it to be ready
        postgresql.start();

        // Generate RSA key pair for testing
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        rsaAlgorithm = Algorithm.RSA256(publicKey, privateKey);
        httpClient = new OkHttpClient();

        // Create gateway configuration with JWT authentication
        File testConfigFile = createGatewayConfigFile();

        // Start Gateway
        String[] args = {testConfigFile.getAbsolutePath()};
        HaGatewayLauncher.main(args);

        // Wait for the gateway to start
        Thread.sleep(5000);
        log.info("Gateway started on port: " + routerPort);
    }

    @Test
    void testAuthenticatedRequestWithValidJWT()
            throws IOException
    {
        String validToken = createValidRSAToken();
        log.info("Testing with valid JWT token");

        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/gateway")
                .header("Authorization", "Bearer " + validToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("Response code for valid JWT: " + response.code());
            // Should be able to access the endpoint with valid JWT
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    void testUnauthenticatedRequestWithoutJWT()
            throws IOException
    {
        log.info("Testing request without JWT token");

        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/gateway")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("Response code without JWT: " + response.code());
            // Should be forbidden without JWT
            assertThat(response.code()).isEqualTo(403);
        }
    }

    @Test
    void testAuthenticatedRequestWithInvalidJWT()
            throws IOException
    {
        String invalidToken = "invalid.jwt.token";
        log.info("Testing with invalid JWT token");

        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/gateway")
                .header("Authorization", "Bearer " + invalidToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("Response code for invalid JWT: " + response.code());
            // Should be forbidden with invalid JWT
            assertThat(response.code()).isEqualTo(403);
        }
    }

    @Test
    void testAuthenticatedRequestWithExpiredJWT()
            throws IOException
    {
        String expiredToken = createExpiredRSAToken();
        log.info("Testing with expired JWT token");

        Request request = new Request.Builder()
                .url("http://localhost:" + routerPort + "/gateway")
                .header("Authorization", "Bearer " + expiredToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("Response code for expired JWT: " + response.code());
            // Should be forbidden with expired JWT
            assertThat(response.code()).isEqualTo(403);
        }
    }

    // Helper methods

    private File createGatewayConfigFile()
            throws IOException
    {
        Path rsaKeyFile = createRSAPublicKeyFile();

        // Create a minimal gateway configuration for JWT testing
        String yamlConfig = String.format("""
                        serverConfig:
                          http-server.http.port: %d
                          node.environment: test

                        authentication:
                          defaultType: jwt
                          jwt:
                            keyFile: %s
                            requiredIssuer: %s
                            requiredAudience: %s
                            principalField: sub

                        pagePermissions:
                          ADMIN: admin_access
                          USER: user_access
                          API: api_access

                        dataStore:
                          jdbcUrl: %s
                          user: %s
                          password: %s
                          driver: org.postgresql.Driver

                        clusterStatsConfiguration:
                          monitorType: NOOP
                        """, routerPort, rsaKeyFile.toString(), TEST_ISSUER, TEST_AUDIENCE,
                postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());

        Path configFile = tempDir.resolve("test-jwt-integration-config.yml");
        Files.write(configFile, yamlConfig.getBytes(UTF_8));
        return configFile.toFile();
    }

    private Path createRSAPublicKeyFile()
            throws IOException
    {
        Path keyFile = tempDir.resolve("integration_public_key.pem");
        String pemContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n" +
                "-----END PUBLIC KEY-----";
        Files.write(keyFile, pemContent.getBytes(UTF_8));
        return keyFile;
    }

    private String createValidRSAToken()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .withClaim("role", "API")
                .sign(rsaAlgorithm);
    }

    private String createExpiredRSAToken()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .withExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .withClaim("role", "API")
                .sign(rsaAlgorithm);
    }
}
