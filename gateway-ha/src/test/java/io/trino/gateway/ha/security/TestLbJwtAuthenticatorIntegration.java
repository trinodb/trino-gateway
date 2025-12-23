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
import io.trino.gateway.ha.config.JwtConfiguration;
import io.trino.gateway.ha.security.util.AuthenticationException;
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
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestLbJwtAuthenticatorIntegration
{
    private static final String TEST_ISSUER = "trino-gateway-test";
    private static final String TEST_AUDIENCE = "trino-test";
    private static final String TEST_SUBJECT = "test-user@company.com";
    private static final String HMAC_SECRET = "test-secret-key-for-hmac-256";

    @TempDir
    Path tempDir;

    @Mock
    private AuthorizationManager authorizationManager;

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
    void testEndToEndAuthenticationWithRSAAndAuthorizationManager()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration with user mapping pattern
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setUserMappingPattern("(.*)@.*"); // Remove domain from email
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Create valid JWT token without roles (roles are handled by AuthorizationManager)
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        when(authorizationManager.getPrivileges("test-user")).thenReturn(Optional.of("admin_user"));

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("test-user"); // Domain removed by user mapping
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("admin_user"));
    }

    @Test
    void testEndToEndAuthenticationWithHMACAndAuthorizationFallback()
            throws Exception
    {
        // Create HMAC secret file
        Path secretFile = createHMACSecretFile();

        // Setup JWT configuration
        JwtConfiguration jwtConfig = createJwtConfiguration(secretFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Setup authorization manager to return privileges
        when(authorizationManager.getPrivileges(TEST_SUBJECT)).thenReturn(Optional.of("database_access"));

        // Create valid JWT token without roles
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(hmacAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_SUBJECT);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("database_access"));
    }

    @Test
    void testEndToEndAuthenticationWithExpiredToken()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Create expired JWT token
        String expiredToken = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .withExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate with expired token should fail
        assertThatThrownBy(() -> authenticator.authenticate(expiredToken))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void testEndToEndAuthenticationWithWrongIssuer()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Create JWT token with wrong issuer
        String tokenWithWrongIssuer = JWT.create()
                .withIssuer("wrong-issuer")
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate with wrong issuer should fail
        assertThatThrownBy(() -> authenticator.authenticate(tokenWithWrongIssuer))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void testEndToEndAuthenticationWithUserMappingAndAuthorizationManager()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration with user mapping file
        Path userMappingFile = createUserMappingFile();
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setUserMappingFile(userMappingFile.toFile());
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return the expected privileges
        when(authorizationManager.getPrivileges("service_account")).thenReturn(Optional.of("admin_developer_tester"));

        // Create valid JWT token without roles (privileges come from AuthorizationManager)
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("service_account_123")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("service_account"); // Mapped by user mapping rules
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("admin_developer_tester"));
    }

    @Test
    void testEndToEndAuthenticationWithAuthorizationManagerPrivileges()
            throws Exception
    {
        // Create HMAC secret file
        Path secretFile = createHMACSecretFile();

        // Setup JWT configuration
        JwtConfiguration jwtConfig = createJwtConfiguration(secretFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return the expected privileges
        when(authorizationManager.getPrivileges(TEST_SUBJECT)).thenReturn(Optional.of("engineering_data_science"));

        // Create valid JWT token without groups (privileges come from AuthorizationManager)
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(hmacAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_SUBJECT);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("engineering_data_science"));
    }

    @Test
    void testEndToEndAuthenticationWithNoRolesAndNoPrivileges()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Setup authorization manager to return no privileges
        when(authorizationManager.getPrivileges(TEST_SUBJECT)).thenReturn(Optional.empty());

        // Create valid JWT token without roles
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject(TEST_SUBJECT)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_SUBJECT);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.empty());
    }

    @Test
    void testEndToEndAuthenticationWithUserMappingPattern()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration with user mapping pattern to extract username before @ symbol
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setUserMappingPattern("(.*)@.*"); // Extract username before @ (uses $1 by default)
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return privileges for mapped user
        when(authorizationManager.getPrivileges("john.doe")).thenReturn(Optional.of("user_privileges"));

        // Create valid JWT token with email subject
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("john.doe@example.com")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("john.doe"); // Mapped by user mapping pattern (everything before @)
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("user_privileges")); // From AuthorizationManager
    }

    @Test
    void testEndToEndAuthenticationWithUserMappingPatternWithFileBasedMapping()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Setup JWT configuration with user mapping file that can handle multiple patterns
        Path userMappingFile = createUserMappingFile();
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setUserMappingFile(userMappingFile.toFile());
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return the expected privileges
        when(authorizationManager.getPrivileges("service_account")).thenReturn(Optional.of("admin_developer_tester"));

        // Create valid JWT token with service account username that matches the pattern
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("service_account_123")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("service_account"); // Mapped by user mapping rules
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("admin_developer_tester"));
    }

    @Test
    void testEndToEndAuthenticationWithUserMappingPatternDefaultFallback()
            throws Exception
    {
        // Create HMAC secret file
        Path secretFile = createHMACSecretFile();

        // Setup JWT configuration with default user mapping (no pattern specified)
        JwtConfiguration jwtConfig = createJwtConfiguration(secretFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setPrincipalField("sub");
        // No user mapping pattern set, so it should use default (.*)  which matches anything

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return the expected privileges
        when(authorizationManager.getPrivileges("any.username@domain.com")).thenReturn(Optional.of("microservice_production_critical"));

        // Create valid JWT token with any username format
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("any.username@domain.com")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(hmacAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("any.username@domain.com"); // Default mapping preserves full username
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("microservice_production_critical")); // Privileges from AuthorizationManager
    }

    @Test
    void testEndToEndAuthenticationWithUserMappingFileMultipleGroups()
            throws Exception
    {
        // Create RSA public key file
        Path keyFile = createRSAPublicKeyFile();

        // Create a user mapping file with multiple capture groups
        Path complexMappingFile = createComplexUserMappingFile();

        // Setup JWT configuration with user mapping file that supports complex patterns
        JwtConfiguration jwtConfig = createJwtConfiguration(keyFile.toString(), TEST_ISSUER, TEST_AUDIENCE);
        jwtConfig.setUserMappingFile(complexMappingFile.toFile());
        jwtConfig.setPrincipalField("sub");

        // Create JWT manager and authenticator
        Map<String, String> pagePermissions = new HashMap<>();
        LbJwtManager jwtManager = new LbJwtManager(jwtConfig, pagePermissions);
        LbJwtAuthenticator authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Mock authorization manager to return the expected privileges
        when(authorizationManager.getPrivileges("api-123")).thenReturn(Optional.of("microservice_production"));

        // Create valid JWT token with service account that matches the complex pattern
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withAudience(TEST_AUDIENCE)
                .withSubject("svc-api-123")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .sign(rsaAlgorithm);

        // Authenticate
        Optional<LbPrincipal> result = authenticator.authenticate(token);

        // Verify result
        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo("api-123"); // Mapped using multiple capture groups
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("microservice_production")); // Privileges from AuthorizationManager
    }

    private Path createRSAPublicKeyFile()
            throws IOException
    {
        Path keyFile = tempDir.resolve("rsa-public.pem");
        String pemContent = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n" +
                "-----END PUBLIC KEY-----\n";
        Files.write(keyFile, pemContent.getBytes(UTF_8));
        return keyFile;
    }

    private Path createHMACSecretFile()
            throws IOException
    {
        Path secretFile = tempDir.resolve("hmac-secret.txt");
        Files.write(secretFile, HMAC_SECRET.getBytes(UTF_8));
        return secretFile;
    }

    private Path createUserMappingFile()
            throws IOException
    {
        Path mappingFile = tempDir.resolve("user-mapping.json");
        String mappingContent = "{\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"pattern\": \"service_account_(\\\\d+)\",\n" +
                "      \"user\": \"service_account\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"pattern\": \"(.*)@.*\",\n" +
                "      \"user\": \"$1\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        Files.write(mappingFile, mappingContent.getBytes(UTF_8));
        return mappingFile;
    }

    private Path createComplexUserMappingFile()
            throws IOException
    {
        Path mappingFile = tempDir.resolve("complex-user-mapping.json");
        String mappingContent = "{\n" +
                "  \"rules\": [\n" +
                "    {\n" +
                "      \"pattern\": \"svc-([a-z]+)-([0-9]+)\",\n" +
                "      \"user\": \"$1-$2\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"pattern\": \"([a-zA-Z]+)\\\\.([a-zA-Z]+)@.*\",\n" +
                "      \"user\": \"$1.$2\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"pattern\": \"(.*)@.*\",\n" +
                "      \"user\": \"$1\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        Files.write(mappingFile, mappingContent.getBytes(UTF_8));
        return mappingFile;
    }

    private JwtConfiguration createJwtConfiguration(String keyFile, String issuer, String audience)
    {
        JwtConfiguration config = new JwtConfiguration();
        config.setKeyFile(keyFile);
        config.setRequiredIssuer(issuer);
        config.setRequiredAudience(audience);
        config.setPrincipalField("sub");
        return config;
    }
}
