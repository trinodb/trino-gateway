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

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static com.auth0.jwt.algorithms.Algorithm.HMAC256;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
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
        requestAnalyzerConfig.setOauthTokenInfoTimeout(new Duration(200, MILLISECONDS));

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

            assertThat(userFromUserInfo(userInfoServer, new RequestAnalyzerConfig())).hasValue("123456789");

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
            return userFromUserInfo(userInfoServer, requestAnalyzerConfig);
        }
    }

    private static Optional<String> userFromUserInfo(MockWebServer userInfoServer, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        requestAnalyzerConfig.setOauthTokenInfoUrl(userInfoServer.url("/userinfo").toString());

        HttpServletRequest mockRequest = new QueryRequestMock()
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .httpHeader(USER_HEADER, null)
                .httpHeader(AUTHORIZATION, "Bearer " + OPAQUE_TOKEN)
                .getHttpServletRequest();

        TrinoRequestUser trinoRequestUser = (TrinoRequestUser) mockRequest.getAttribute(TRINO_REQUEST_USER);
        return trinoRequestUser.getUser();
    }
}
