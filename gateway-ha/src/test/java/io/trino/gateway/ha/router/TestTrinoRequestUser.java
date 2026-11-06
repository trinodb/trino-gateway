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
package io.trino.gateway.ha.router;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.util.Base64URL;
import io.airlift.compress.v3.zstd.ZstdOutputStream;
import io.airlift.json.JsonCodec;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.util.QueryRequestMock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.zip.DeflaterOutputStream;

import static com.auth0.jwt.algorithms.Algorithm.HMAC256;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

final class TestTrinoRequestUser
{
    @Test
    void testJsonCreator()
    {
        JsonCodec<TrinoRequestUser> codec = JsonCodec.jsonCodec(TrinoRequestUser.class);
        String userInfoJson =
                """
                {
                  "sub": "12345",
                  "name": "Usr McUsr",
                  "email": "user@example.com",
                  "birthdate": "1969-12-31"
                }
                """;
        TrinoRequestUser trinoRequestUser = new TrinoRequestUser(Optional.of("usr"), userInfoJson);

        String trinoRequestUserJson = codec.toJson(trinoRequestUser);
        TrinoRequestUser deserializedTrinoRequestUser = codec.fromJson(trinoRequestUserJson);

        assertThat(deserializedTrinoRequestUser.getUser()).isEqualTo(trinoRequestUser.getUser());
        assertThat(deserializedTrinoRequestUser.getUserInfo()).isEqualTo(trinoRequestUser.getUserInfo());
    }

    @Test
    void testUserFromJwtToken()
    {
        String claimUserName = "username";
        String claimUserValue = "trino";

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setTokenUserField(claimUserName);

        Algorithm algorithm = HMAC256("random");

        Instant expiryTime = Instant.now().plusSeconds(60);
        String token = JWT.create()
                .withIssuer("gateway")
                .withClaim(claimUserName, claimUserValue)
                .withExpiresAt(Date.from(expiryTime))
                .sign(algorithm);

        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Bearer " + token)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).hasValue(claimUserValue);
    }

    @Test
    void testGetBasicAuthUser()
    {
        String username = "trino_user";
        String password = "don't care";
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();

        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Basic " + encodedCredentials)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).hasValue(username);
    }

    @Test
    void testUserFromTrinoJweToken()
            throws Exception
    {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthRefreshTokensSecretKey(Base64.getEncoder().encodeToString(key));

        String claims = "{\"email\":\"user@example.com\",\"aud\":[\"trino_client\"],\"iss\":\"Trino_coordinator\",\"access_token\":\"opaque\"}";

        assertThat(userFromBearerToken(requestAnalyzerConfig, trinoJwe(key, "ZSTD", claims))).hasValue("user@example.com");
        assertThat(userFromBearerToken(requestAnalyzerConfig, trinoJwe(key, "DEF", claims))).hasValue("user@example.com");
    }

    @Test
    void testTrinoJweTokenWithWrongKey()
            throws Exception
    {
        byte[] key = new byte[32];
        byte[] otherKey = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(key);
        random.nextBytes(otherKey);

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthRefreshTokensSecretKey(Base64.getEncoder().encodeToString(otherKey));

        assertThat(userFromBearerToken(requestAnalyzerConfig, trinoJwe(key, "ZSTD", "{\"email\":\"user@example.com\"}"))).isEmpty();
    }

    private static Optional<String> userFromBearerToken(RequestAnalyzerConfig requestAnalyzerConfig, String token)
    {
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Bearer " + token)
                .getHttpServletRequest();
        return ((TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER)).getUser();
    }

    // Make a token in the same format as Trino JweTokenSerializer.
    private static String trinoJwe(byte[] key, String zip, String claimsJson)
            throws Exception
    {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream out = "ZSTD".equals(zip) ? new ZstdOutputStream(compressed) : new DeflaterOutputStream(compressed)) {
            out.write(claimsJson.getBytes(UTF_8));
        }
        String innerJwt = Base64URL.encode("{\"alg\":\"none\",\"zip\":\"" + zip + "\"}") + "." + Base64URL.encode(compressed.toByteArray()) + ".";
        JWEObject jwe = new JWEObject(new JWEHeader(JWEAlgorithm.A256GCMKW, EncryptionMethod.A256GCM), new Payload(innerJwt));
        jwe.encrypt(new AESEncrypter(new SecretKeySpec(key, "AES")));
        return jwe.serialize();
    }
}
