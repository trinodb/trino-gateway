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

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.JwtConfiguration;
import jakarta.ws.rs.core.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestLbJwtManager
{
    private static final String TEST_ISSUER = "trino-gateway-test";
    private static final String TEST_AUDIENCE = "trino-test";
    private static final String TEST_SUBJECT = "test-user";
    private static final String TEST_KEY_ID = "test-key-1";
    private static final String HMAC_SECRET = "test-secret-key-for-hmac-256-bits";

    @TempDir
    Path tempDir;

    @Mock
    private JwkProvider jwkProvider;

    @Mock
    private Jwk jwk;

    private KeyPair keyPair;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private Algorithm rsaAlgorithm;
    private Algorithm hmacAlgorithm;

    @BeforeEach
    void setUp()
            throws Exception
    {
        MockitoAnnotations.openMocks(this);

        // Generate RSA key pair for testing
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        rsaAlgorithm = Algorithm.RSA256(publicKey, privateKey);
        hmacAlgorithm = Algorithm.HMAC256(HMAC_SECRET);
    }

    @Test
    void testConstructorWithValidRSAKeyFile()
            throws IOException
    {
        // Create a PEM public key file
        Path keyFile = createRSAPublicKeyFile();

        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = ImmutableMap.of("ADMIN", "admin_access", "USER", "user_access");

        LbJwtManager manager = new LbJwtManager(config, pagePermissions);

        assertThat(manager.getPrincipalField()).isEqualTo("sub");
        assertThat(manager.getUserMappingPattern()).isEqualTo(config.getUserMappingPattern());
        assertThat(manager.getUserMappingFile()).isEqualTo(config.getUserMappingFile());
    }

    @Test
    void testConstructorWithValidHMACSecretFile()
            throws IOException
    {
        // Create HMAC secret file
        Path secretFile = tempDir.resolve("hmac-secret.txt");
        Files.write(secretFile, HMAC_SECRET.getBytes(UTF_8));

        JwtConfiguration config = createHmacJwtConfiguration(secretFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();

        LbJwtManager manager = new LbJwtManager(config, pagePermissions);

        assertThat(manager.getPrincipalField()).isEqualTo("sub");
        assertThat(manager.getUserMappingPattern()).isEqualTo(config.getUserMappingPattern());
        assertThat(manager.getUserMappingFile()).isEqualTo(config.getUserMappingFile());
    }

    @Test
    void testConstructorRejectsMultipleKeySources()
            throws IOException
    {
        Path secretFile = tempDir.resolve("multiple-sources-secret.txt");
        Files.writeString(secretFile, HMAC_SECRET, UTF_8);

        JwtConfiguration config = new JwtConfiguration();
        config.setPublicKeyFile("public.pem");
        config.setHmacSecretFile(secretFile.toString());

        assertThatThrownBy(() -> new LbJwtManager(config, Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Exactly one JWT key source must be configured: publicKeyFile, hmacSecretFile, or jwksUrl");
    }

    @Test
    void testConstructorRejectsShortHmacSecret()
            throws IOException
    {
        Path secretFile = tempDir.resolve("short-hmac-secret.txt");
        Files.writeString(secretFile, "too-short", UTF_8);

        JwtConfiguration config = new JwtConfiguration();
        config.setHmacSecretFile(secretFile.toString());

        assertThatThrownBy(() -> new LbJwtManager(config, Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("HMAC secret must be at least 256 bits");
    }

    @Test
    void testInvalidRsaPublicKeyDoesNotFallBackToHmac()
            throws Exception
    {
        Path keyFile = Path.of("src/test/resources/invalid_jwt_material.txt").toAbsolutePath();

        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);

        assertThatThrownBy(() -> new LbJwtManager(config, Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize JWT algorithm");
    }

    @Test
    void testConstructorWithJWKSUrl()
            throws IOException
    {
        JwtConfiguration config = createJwksJwtConfiguration("https://example.com/.well-known/jwks.json", TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();

        LbJwtManager manager = new LbJwtManager(config, pagePermissions);

        assertThat(manager.getPrincipalField()).isEqualTo("sub");
        assertThat(manager.getUserMappingPattern()).isEqualTo(config.getUserMappingPattern());
        assertThat(manager.getUserMappingFile()).isEqualTo(config.getUserMappingFile());
    }

    @Test
    void testJwksUsesConfiguredUrlWithoutAppendingWellKnownPath()
            throws Exception
    {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jwksResponse()));
            server.start();

            String jwksPath = "/tenant/discovery/v2.0/keys";
            JwtConfiguration config = createJwksJwtConfiguration(server.url(jwksPath).toString(), TEST_ISSUER, TEST_AUDIENCE);
            LbJwtManager manager = new LbJwtManager(config, Map.of());

            assertThat(manager.getClaimsFromToken(createValidRSATokenWithKeyId())).isPresent();
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo(jwksPath);
        }
    }

    @Test
    void testConstructorWithNullKeyFile()
    {
        JwtConfiguration config = new JwtConfiguration();
        config.setRequiredIssuer(TEST_ISSUER);
        config.setRequiredAudience(TEST_AUDIENCE);
        // keyFile is null

        Map<String, String> pagePermissions = new HashMap<>();

        assertThatThrownBy(() -> new LbJwtManager(config, pagePermissions))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize JWT algorithm");
    }

    @Test
    void testConstructorWithInvalidKeyFile()
    {
        JwtConfiguration config = createRsaJwtConfiguration("/non/existent/file.pem", TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();

        assertThatThrownBy(() -> new LbJwtManager(config, pagePermissions))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize JWT algorithm");
    }

    @Test
    void testConstructorWithInvalidUserMappingFile()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        config.setUserMappingFile(tempDir.resolve("missing-user-mapping.json").toFile());

        assertThatThrownBy(() -> new LbJwtManager(config, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT user mapping file does not exist or is not readable");
    }

    @Test
    void testVerifyValidRSAToken()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String token = createValidRSAToken();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isPresent();
        Map<String, Claim> claims = result.orElseThrow();
        assertThat(claims.get("sub").asString()).isEqualTo(TEST_SUBJECT);
        assertThat(claims.get("iss").asString()).isEqualTo(TEST_ISSUER);

        Claim audienceClaim = claims.get("aud");
        assertThat(audienceClaim).isNotNull();
        // When only one audience is specified, it's stored as a string, not as a list
        assertThat(audienceClaim.asString()).isEqualTo(TEST_AUDIENCE);
    }

    @Test
    void testVerifyValidHMACToken()
            throws IOException
    {
        Path secretFile = tempDir.resolve("hmac-secret.txt");
        Files.write(secretFile, HMAC_SECRET.getBytes(UTF_8));

        JwtConfiguration config = createHmacJwtConfiguration(secretFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String token = createValidHMACToken();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().get("sub").asString()).isEqualTo(TEST_SUBJECT);
    }

    @Test
    void testVerifyValidJWKSToken()
            throws Exception
    {
        // Mock JWKS provider
        when(jwk.getPublicKey()).thenReturn(publicKey);
        when(jwkProvider.get(TEST_KEY_ID)).thenReturn(jwk);

        JwtConfiguration config = createJwksJwtConfiguration("https://example.com/.well-known/jwks.json", TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        // Use reflection to set the jwkProvider (since it's private)
        java.lang.reflect.Field jwkProviderField = LbJwtManager.class.getDeclaredField("jwkProvider");
        jwkProviderField.setAccessible(true);
        jwkProviderField.set(manager, jwkProvider);

        String token = createValidRSATokenWithKeyId();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().get("sub").asString()).isEqualTo(TEST_SUBJECT);
    }

    @Test
    void testVerifyInvalidToken()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String invalidToken = "invalid.jwt.token";
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(invalidToken);

        assertThat(result).isEmpty();
    }

    @Test
    void testVerifyExpiredToken()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String expiredToken = createExpiredRSAToken();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(expiredToken);

        assertThat(result).isEmpty();
    }

    @Test
    void testVerifyTokenWithWrongIssuer()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String token = createRSATokenWithWrongIssuer();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void testVerifyTokenWithWrongAudience()
            throws IOException
    {
        Path keyFile = createRSAPublicKeyFile();
        JwtConfiguration config = createRsaJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        String token = createRSATokenWithWrongAudience();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void testVerifyJWKSTokenWithMissingKeyId()
            throws Exception
    {
        JwtConfiguration config = createJwksJwtConfiguration("https://example.com/.well-known/jwks.json", TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        // Use reflection to set the jwkProvider
        java.lang.reflect.Field jwkProviderField = LbJwtManager.class.getDeclaredField("jwkProvider");
        jwkProviderField.setAccessible(true);
        jwkProviderField.set(manager, jwkProvider);

        String token = createValidRSAToken(); // Token without keyId
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void testVerifyJWKSTokenWithJwkException()
            throws Exception
    {
        // Mock JWKS provider to throw exception
        when(jwkProvider.get(TEST_KEY_ID)).thenThrow(new JwkException("Key not found"));

        JwtConfiguration config = createJwksJwtConfiguration("https://example.com/.well-known/jwks.json", TEST_ISSUER, TEST_AUDIENCE);
        LbJwtManager manager = new LbJwtManager(config, new HashMap<>());

        // Use reflection to set the jwkProvider
        java.lang.reflect.Field jwkProviderField = LbJwtManager.class.getDeclaredField("jwkProvider");
        jwkProviderField.setAccessible(true);
        jwkProviderField.set(manager, jwkProvider);

        String token = createValidRSATokenWithKeyId();
        Optional<Map<String, Claim>> result = manager.getClaimsFromToken(token);

        assertThat(result).isEmpty();
    }

    @Test
    void testProcessPagePermissions()
            throws IOException
    {
        Map<String, String> pagePermissions = ImmutableMap.of(
                "ADMIN", "admin_access",
                "USER", "user_access",
                "API", "api_access");

        Path secretFile = tempDir.resolve("hmac-secret.txt");
        Files.writeString(secretFile, HMAC_SECRET, UTF_8);

        JwtConfiguration config = new JwtConfiguration();
        config.setHmacSecretFile(secretFile.toString());
        LbJwtManager manager = new LbJwtManager(config, pagePermissions);

        List<String> adminRoles = List.of("ADMIN");
        List<String> result = manager.processPagePermissions(adminRoles);
        assertThat(result).containsExactly("admin", "access");

        List<String> multipleRoles = List.of("ADMIN", "USER");
        result = manager.processPagePermissions(multipleRoles);
        assertThat(result).containsExactlyInAnyOrder("admin", "access", "user");

        List<String> invalidRoles = List.of("INVALID");
        result = manager.processPagePermissions(invalidRoles);
        assertThat(result).isEmpty();
    }

    @Test
    void testBuildUnauthorizedResponse()
    {
        Response response = LbJwtManager.buildUnauthorizedResponse();
        assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    void testGetPrincipalFieldDefault()
    {
        Path secretFile;
        try {
            secretFile = tempDir.resolve("hmac-secret.txt");
            Files.write(secretFile, HMAC_SECRET.getBytes(UTF_8));

            JwtConfiguration config = new JwtConfiguration();
            config.setHmacSecretFile(secretFile.toString());
            // principalField defaults to "sub"

            LbJwtManager manager = new LbJwtManager(config, new HashMap<>());
            assertThat(manager.getPrincipalField()).isEqualTo("sub");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testGetPrincipalFieldCustom()
    {
        Path secretFile;
        try {
            secretFile = tempDir.resolve("hmac-secret.txt");
            Files.write(secretFile, HMAC_SECRET.getBytes(UTF_8));

            JwtConfiguration config = new JwtConfiguration();
            config.setHmacSecretFile(secretFile.toString());
            config.setPrincipalField("username");

            LbJwtManager manager = new LbJwtManager(config, new HashMap<>());
            assertThat(manager.getPrincipalField()).isEqualTo("username");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper methods

    private JwtConfiguration createRsaJwtConfiguration(String publicKeyFile, String issuer, String audience)
    {
        JwtConfiguration config = createJwtConfiguration(issuer, audience);
        config.setPublicKeyFile(publicKeyFile);
        return config;
    }

    private JwtConfiguration createHmacJwtConfiguration(String hmacSecretFile, String issuer, String audience)
    {
        JwtConfiguration config = createJwtConfiguration(issuer, audience);
        config.setHmacSecretFile(hmacSecretFile);
        return config;
    }

    private JwtConfiguration createJwksJwtConfiguration(String jwksUrl, String issuer, String audience)
    {
        JwtConfiguration config = createJwtConfiguration(issuer, audience);
        config.setJwksUrl(jwksUrl);
        return config;
    }

    private JwtConfiguration createJwtConfiguration(String issuer, String audience)
    {
        JwtConfiguration config = new JwtConfiguration();
        config.setRequiredIssuer(issuer);
        config.setRequiredAudience(audience);
        config.setPrincipalField("sub");
        return config;
    }

    private Path createRSAPublicKeyFile()
            throws IOException
    {
        Path keyFile = tempDir.resolve("public_key.pem");
        String pemContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n" +
                "-----END PUBLIC KEY-----";
        Files.write(keyFile, pemContent.getBytes(UTF_8));
        return keyFile;
    }

    private String jwksResponse()
    {
        String modulus = Base64.getUrlEncoder().withoutPadding().encodeToString(unsignedBytes(publicKey.getModulus().toByteArray()));
        String exponent = Base64.getUrlEncoder().withoutPadding().encodeToString(unsignedBytes(publicKey.getPublicExponent().toByteArray()));
        return """
               {"keys":[{"kty":"RSA","kid":"%s","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
               """.formatted(TEST_KEY_ID, modulus, exponent);
    }

    private static byte[] unsignedBytes(byte[] value)
    {
        if (value.length > 1 && value[0] == 0) {
            return java.util.Arrays.copyOfRange(value, 1, value.length);
        }
        return value;
    }

    private String createValidRSAToken()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);
    }

    private String createValidRSATokenWithKeyId()
    {
        return JWT.create()
                .withKeyId(TEST_KEY_ID)
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);
    }

    private String createValidHMACToken()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(hmacAlgorithm);
    }

    private String createExpiredRSAToken()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .withExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);
    }

    private String createRSATokenWithWrongIssuer()
    {
        return JWT.create()
                .withIssuer("wrong-issuer")
                .withSubject(TEST_SUBJECT)
                .withAudience(TEST_AUDIENCE)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);
    }

    private String createRSATokenWithWrongAudience()
    {
        return JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject(TEST_SUBJECT)
                .withAudience("wrong-audience")
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);
    }
}
