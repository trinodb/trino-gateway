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

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.JwtConfiguration;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

public class LbJwtManager
{
    private static final Logger log = Logger.get(LbJwtManager.class);
    private static final int MINIMUM_HMAC_SECRET_BYTES = 32;

    private final JwtConfiguration jwtConfig;
    private final Map<String, String> pagePermissions;
    private Algorithm algorithm;
    private JWTVerifier jwtVerifier;
    private JwkProvider jwkProvider; // For JWKS-based authentication

    public LbJwtManager(JwtConfiguration configuration, Map<String, String> pagePermissions)
    {
        this.jwtConfig = configuration;
        configuration.getUserMappingFile().ifPresent(file -> {
            if (!file.isFile() || !file.canRead()) {
                throw new IllegalArgumentException("JWT user mapping file does not exist or is not readable: " + file);
            }
        });
        this.pagePermissions = pagePermissions.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(toImmutableMap(entry -> entry.getKey().toUpperCase(ENGLISH), Map.Entry::getValue));

        initializeAlgorithm();
        initializeVerifier();
    }

    private void initializeAlgorithm()
    {
        try {
            long configuredKeySources = Stream.of(
                            jwtConfig.getPublicKeyFile(),
                            jwtConfig.getHmacSecretFile(),
                            jwtConfig.getJwksUrl())
                    .filter(Objects::nonNull)
                    .count();
            if (configuredKeySources != 1) {
                throw new IllegalArgumentException("Exactly one JWT key source must be configured: publicKeyFile, hmacSecretFile, or jwksUrl");
            }

            if (jwtConfig.getJwksUrl() != null) {
                String jwksUrl = jwtConfig.getJwksUrl();
                log.info("Configuring JWKS provider for URL: %s", jwksUrl);
                this.jwkProvider = new JwkProviderBuilder(URI.create(jwksUrl).toURL()).build();
                this.algorithm = null;
                log.info("Successfully configured JWKS provider for: %s", jwksUrl);
                return;
            }

            if (jwtConfig.getPublicKeyFile() != null) {
                String publicKeyFile = jwtConfig.getPublicKeyFile();
                RSAPublicKey publicKey = loadPublicKey(publicKeyFile);
                algorithm = Algorithm.RSA256(publicKey, null);
                log.info("Loaded RSA public key from: %s", publicKeyFile);
                return;
            }

            String hmacSecretFile = jwtConfig.getHmacSecretFile();
            String hmacSecret = Files.readString(Path.of(hmacSecretFile), UTF_8).trim();
            if (hmacSecret.getBytes(UTF_8).length < MINIMUM_HMAC_SECRET_BYTES) {
                throw new IllegalArgumentException("HMAC secret must be at least 256 bits");
            }
            algorithm = Algorithm.HMAC256(hmacSecret);
            log.info("Loaded HMAC secret from: %s", hmacSecretFile);
        }
        catch (Exception e) {
            log.error(e, "Failed to initialize JWT algorithm");
            throw new RuntimeException("Failed to initialize JWT algorithm", e);
        }
    }

    private void initializeVerifier()
    {
        // For JWKS-based authentication, we create the verifier dynamically in verifyToken()
        // because we need to get the correct key based on the 'kid' from JWT header
        if (jwkProvider != null) {
            log.info("Using JWKS-based verification, verifier will be created per-token");
            this.jwtVerifier = null;
            return;
        }

        // For static key files (PEM/HMAC), create the verifier once
        var verification = JWT.require(algorithm);

        // Add required issuer verification (Trino: http-server.authentication.jwt.required-issuer)
        if (jwtConfig.getRequiredIssuer() != null) {
            verification = verification.withIssuer(jwtConfig.getRequiredIssuer());
        }

        // Add required audience verification (Trino: http-server.authentication.jwt.required-audience)
        if (jwtConfig.getRequiredAudience() != null) {
            verification = verification.withAudience(jwtConfig.getRequiredAudience());
        }

        jwtVerifier = verification.build();
    }

    private RSAPublicKey loadPublicKey(String publicKeyPath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        String publicKeyContent = new String(Files.readAllBytes(Path.of(publicKeyPath)), UTF_8);

        publicKeyContent = publicKeyContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyContent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    public String getPrincipalField()
    {
        return jwtConfig.getPrincipalField();
    }

    public Optional<String> getUserMappingPattern()
    {
        return jwtConfig.getUserMappingPattern();
    }

    public Optional<java.io.File> getUserMappingFile()
    {
        return jwtConfig.getUserMappingFile();
    }

    /**
     * Verifies a JWT token and returns the claims if valid.
     *
     * @param token The JWT token to verify
     * @return Optional map of claims if token is valid, empty optional otherwise
     */
    public Optional<Map<String, Claim>> getClaimsFromToken(String token)
    {
        try {
            // Handle JWKS-based verification
            if (jwkProvider != null) {
                return verifyTokenWithJwks(token);
            }

            // Handle static key verification (PEM/HMAC)
            DecodedJWT verifiedJwt = jwtVerifier.verify(token);
            return Optional.of(verifiedJwt.getClaims());
        }
        catch (JWTVerificationException e) {
            log.error(e, "JWT verification failed");
            return Optional.empty();
        }
    }

    /**
     * Verifies a JWT token using JWKS (JSON Web Key Set).
     */
    private Optional<Map<String, Claim>> verifyTokenWithJwks(String token)
    {
        try {
            // First decode the token without verification to get the key ID (kid)
            DecodedJWT unverifiedJwt = JWT.decode(token);
            String keyId = unverifiedJwt.getKeyId();
            String algorithm = unverifiedJwt.getAlgorithm();
            String issuer = unverifiedJwt.getIssuer();

            if (keyId == null) {
                log.error("JWT token is missing 'kid' (key ID) in header, required for JWKS verification. " +
                        "Token algorithm: %s, issuer: %s", algorithm, issuer);
                return Optional.empty();
            }

            // Get the public key from JWKS based on key ID
            RSAPublicKey publicKey = (RSAPublicKey) jwkProvider.get(keyId).getPublicKey();

            Algorithm rsaAlgorithm = Algorithm.RSA256(publicKey, null);

            // Create verifier with the specific key
            var verification = JWT.require(rsaAlgorithm);

            if (jwtConfig.getRequiredIssuer() != null) {
                verification = verification.withIssuer(jwtConfig.getRequiredIssuer());
            }

            if (jwtConfig.getRequiredAudience() != null) {
                verification = verification.withAudience(jwtConfig.getRequiredAudience());
            }

            JWTVerifier jwtVerifierLocal = verification.build();

            // Now verify the token with the correct key
            DecodedJWT verifiedJwt = jwtVerifierLocal.verify(token);

            log.debug(
                    "Successfully verified JWT token with key ID: %s, issuer: %s",
                    keyId,
                    issuer);

            return Optional.of(verifiedJwt.getClaims());
        }
        catch (JwkException e) {
            log.error(e, "Failed to get public key from JWKS provider. Key ID might not exist in JWKS or JWKS endpoint might be unreachable");
            return Optional.empty();
        }
        catch (JWTVerificationException e) {
            log.error(e, "JWT verification failed with JWKS. This could be due to: " +
                    "1) Token signed with different key than retrieved from JWKS, " +
                    "2) Token expired or not yet valid, " +
                    "3) Issuer/audience mismatch, " +
                    "4) Token signature is invalid");
            return Optional.empty();
        }
        catch (Exception e) {
            log.error(e, "Unexpected error during JWKS-based JWT verification");
            return Optional.empty();
        }
    }

    public List<String> processPagePermissions(List<String> roles)
    {
        for (String role : roles) {
            String value = pagePermissions.get(role);
            if (value == null) {
                return List.of();
            }
        }

        return roles.stream()
                .flatMap(role -> Stream.of(pagePermissions.get(role).split("_")))
                .distinct().toList();
    }

    public static Response buildUnauthorizedResponse()
    {
        return Response.status(UNAUTHORIZED)
                .build();
    }
}
