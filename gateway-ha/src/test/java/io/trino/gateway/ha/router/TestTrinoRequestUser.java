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
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.util.QueryRequestMock;
import jakarta.servlet.http.HttpServletRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static com.auth0.jwt.algorithms.Algorithm.HMAC256;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static io.trino.gateway.ha.router.TrinoRequestUser.TRINO_UI_TOKEN_NAME;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

final class TestTrinoRequestUser
{
    private static final String OPAQUE_TOKEN = "opaque-access-token";

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
    void testJwtWithUnusableUserClaim()
    {
        // The default tokenUserField is email
        Map<String, String> tokens = Map.of(
                "number", JWT.create().withClaim("email", 42).sign(HMAC256("random")),
                "array", JWT.create().withArrayClaim("email", new String[] {"user@example.com"}).sign(HMAC256("random")),
                "object", JWT.create().withClaim("email", Map.of("address", "user@example.com")).sign(HMAC256("random")),
                "empty", JWT.create().withClaim("email", "").sign(HMAC256("random")),
                "blank", JWT.create().withClaim("email", " ").sign(HMAC256("random")));
        tokens.forEach((claimType, token) -> {
            HttpServletRequest mockRequest = new QueryRequestMock()
                    .requestAnalyzerConfig(new RequestAnalyzerConfig())
                    .httpHeader(USER_HEADER, null)
                    .httpHeader(AUTHORIZATION, "Bearer " + token)
                    .getHttpServletRequest();

            TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

            assertThat(trinoRequestUser.getUser()).as(claimType).isEmpty();
        });
    }

    @Test
    void testJwtWithNonStringUserClaimFallsBackToUserInfo()
            throws Exception
    {
        String token = JWT.create()
                .withClaim("email", 42)
                .sign(HMAC256("random"));

        try (MockWebServer userInfoServer = new MockWebServer()) {
            userInfoServer.enqueue(userInfoResponse(
                    """
                    {
                      "sub": "123456789"
                    }
                    """));
            userInfoServer.start();

            assertThat(userFromUserInfo(userInfoServer, new RequestAnalyzerConfig(), token)).hasValue("123456789");
        }
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

        assertThat(userFromBearerToken(requestAnalyzerConfig, trinoJwe(key, claims))).hasValue("user@example.com");
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

        assertThat(userFromBearerToken(requestAnalyzerConfig, trinoJwe(key, "{\"email\":\"user@example.com\"}"))).isEmpty();
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
    private static String trinoJwe(byte[] key, String claimsJson)
            throws Exception
    {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZstdOutputStream out = new ZstdOutputStream(compressed)) {
            out.write(claimsJson.getBytes(UTF_8));
        }
        String innerJwt = Base64URL.encode("{\"alg\":\"none\",\"zip\":\"ZSTD\"}") + "." + Base64URL.encode(compressed.toByteArray()) + ".";
        JWEObject jwe = new JWEObject(new JWEHeader(JWEAlgorithm.A256GCMKW, EncryptionMethod.A256GCM), new Payload(innerJwt));
        jwe.encrypt(new AESEncrypter(new SecretKeySpec(key, "AES")));
        return jwe.serialize();
    }

    @Test
    void testMalformedBasicAuthHeaderDoesNotThrow()
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();

        // "Basic" with no space and no credentials
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Basic")
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).isEmpty();
    }

    @Test
    void testBasicAuthHeaderWithEmptyCredentials()
    {
        for (String header : new String[] {"Basic ", "Basic Og=="}) {
            HttpServletRequest mockRequest = new QueryRequestMock()
                    .requestAnalyzerConfig(new RequestAnalyzerConfig())
                    .httpHeader(USER_HEADER, null)
                    .httpHeader(AUTHORIZATION, header)
                    .getHttpServletRequest();

            TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

            assertThat(trinoRequestUser.getUser()).isEmpty();
        }
    }

    @Test
    void testBasicAuthHeaderWithEmptyCredentialsFallsBackToCookie()
    {
        String uiToken = JWT.create()
                .withClaim("email", "alice@example.com")
                .sign(HMAC256("random"));
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(new RequestAnalyzerConfig())
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Basic Og==")
                .cookie(TRINO_UI_TOKEN_NAME, uiToken)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).hasValue("alice@example.com");
    }

    @Test
    void testBasicAuthHeaderWithInvalidBase64()
    {
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(new RequestAnalyzerConfig())
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Basic not*base64")
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).isEmpty();
    }

    @Test
    void testBearerTokenContainingBasicIsNotDecodedAsBasic()
    {
        String encoded = Base64.getEncoder().encodeToString("alice:secret".getBytes(UTF_8));
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(new RequestAnalyzerConfig())
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Bearer Basic " + encoded)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).isEmpty();
    }

    @Test
    void testBasicAuthSchemeIsCaseInsensitive()
    {
        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(new RequestAnalyzerConfig())
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "basic " + Base64.getEncoder().encodeToString("alice:secret".getBytes(UTF_8)))
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);

        assertThat(trinoRequestUser.getUser()).hasValue("alice");
    }

    @Test
    void testUserFromUserInfoSubject()
            throws Exception
    {
        assertThat(userFromUserInfo(new RequestAnalyzerConfig(), userInfoResponse(
                """
                {
                  "sub": "123456789",
                  "email": "user@example.com"
                }
                """))).hasValue("123456789");
    }

    @Test
    void testUserFromUserInfoClaim()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthTokenInfoUserField("preferred_username");

        assertThat(userFromUserInfo(requestAnalyzerConfig, userInfoResponse(
                """
                {
                  "sub": "123456789",
                  "email": "user@example.com",
                  "preferred_username": " user "
                }
                """))).hasValue("user");
    }

    @Test
    void testUserFromUserInfoFallsBackToSubject()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthTokenInfoUserField("email");

        // Missing, blank and non-string claim
        assertThat(userFromUserInfo(requestAnalyzerConfig, userInfoResponse("{\"sub\": \"123456789\"}"))).hasValue("123456789");
        assertThat(userFromUserInfo(requestAnalyzerConfig, userInfoResponse("{\"sub\": \"123456789\", \"email\": \" \"}"))).hasValue("123456789");
        assertThat(userFromUserInfo(requestAnalyzerConfig, userInfoResponse("{\"sub\": \"123456789\", \"email\": 42}"))).hasValue("123456789");
    }

    @Test
    void testTokenUserFieldDoesNotApplyToUserInfo()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setTokenUserField("preferred_username");

        assertThat(userFromUserInfo(requestAnalyzerConfig, userInfoResponse(
                """
                {
                  "sub": "123456789",
                  "preferred_username": "user"
                }
                """))).hasValue("123456789");
    }

    @Test
    void testUserFromUserInfoForJwtWithoutTokenUserField()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthTokenInfoUserField("preferred_username");

        // No email claim, the default tokenUserField, so the token is exchanged with the userinfo endpoint
        String token = JWT.create()
                .withSubject("123456789")
                .sign(HMAC256("random"));

        try (MockWebServer userInfoServer = new MockWebServer()) {
            userInfoServer.enqueue(userInfoResponse(
                    """
                    {
                      "sub": "123456789",
                      "preferred_username": "user"
                    }
                    """));
            userInfoServer.start();

            assertThat(userFromUserInfo(userInfoServer, requestAnalyzerConfig, token)).hasValue("user");

            RecordedRequest recordedRequest = userInfoServer.takeRequest(5, SECONDS);
            assertThat(recordedRequest).isNotNull();
            assertThat(recordedRequest.getHeader(AUTHORIZATION)).isEqualTo("Bearer " + token);
        }
    }

    @Test
    void testUserFromUserInfoErrorResponse()
            throws Exception
    {
        assertThat(userFromUserInfo(new RequestAnalyzerConfig(), new MockResponse().setResponseCode(500))).isEmpty();
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testUserInfoRequestTimesOut()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setOauthRequestTimeout(new Duration(200, MILLISECONDS));

        assertThat(userFromUserInfo(requestAnalyzerConfig, new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))).isEmpty();
    }

    @Test
    void testUserInfoRequestCarriesBearerToken()
            throws Exception
    {
        try (MockWebServer userInfoServer = new MockWebServer()) {
            userInfoServer.enqueue(userInfoResponse(
                    """
                    {
                      "sub": "123456789"
                    }
                    """));
            userInfoServer.start();

            assertThat(userFromUserInfo(userInfoServer, new RequestAnalyzerConfig(), OPAQUE_TOKEN)).hasValue("123456789");

            RecordedRequest recordedRequest = userInfoServer.takeRequest(5, SECONDS);
            assertThat(recordedRequest).isNotNull();
            assertThat(recordedRequest.getPath()).isEqualTo("/userinfo");
            assertThat(recordedRequest.getHeader(AUTHORIZATION)).isEqualTo("Bearer " + OPAQUE_TOKEN);
        }
    }

    private static MockResponse userInfoResponse(String json)
    {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json);
    }

    private static Optional<String> userFromUserInfo(RequestAnalyzerConfig requestAnalyzerConfig, MockResponse response)
            throws IOException
    {
        try (MockWebServer userInfoServer = new MockWebServer()) {
            userInfoServer.enqueue(response);
            userInfoServer.start();
            return userFromUserInfo(userInfoServer, requestAnalyzerConfig, OPAQUE_TOKEN);
        }
    }

    private static Optional<String> userFromUserInfo(MockWebServer userInfoServer, RequestAnalyzerConfig requestAnalyzerConfig, String token)
    {
        requestAnalyzerConfig.setOauthTokenInfoUrl(userInfoServer.url("/userinfo").toString());

        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Bearer " + token)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);
        return trinoRequestUser.getUser();
    }
}
