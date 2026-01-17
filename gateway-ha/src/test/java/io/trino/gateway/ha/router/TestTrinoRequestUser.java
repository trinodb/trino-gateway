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
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.util.QueryRequestMock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

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
        String userInfoJson = """
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

        Instant expiryTime =  Instant.now().plusSeconds(60);
        String token = JWT.create()
                .withIssuer("gateway")
                .withClaim(claimUserName, claimUserValue)
                .withExpiresAt(expiryTime)
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
}
