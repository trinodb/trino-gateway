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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Request;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.nimbusds.openid.connect.sdk.UserInfoResponse.parse;

public class TrinoRequestUser
{
    public static final String TRINO_USER_HEADER_NAME = "X-Trino-User";
    public static final String TRINO_UI_TOKEN_NAME = "Trino-UI-Token";
    public static final String TRINO_SECURE_UI_TOKEN_NAME = "__Secure-Trino-ID-Token";

    private Optional<String> user = Optional.empty();
    private Optional<UserInfo> userInfo = Optional.empty();

    private final String oauthUserInfoUrl;
    private static final Logger log = Logger.get(TrinoRequestUser.class);

    private final LoadingCache<String, UserInfo> userInfoCache =
            CacheBuilder.newBuilder()
                    .maximumSize(10000)
                    .expireAfterAccess(10, TimeUnit.MINUTES)
                    .build(
                            new CacheLoader<String, UserInfo>()
                            {
                                @Override
                                public UserInfo load(String accessToken)
                                        throws Exception
                                {
                                    return getUserInfo(accessToken);
                                }
                            });

    public TrinoRequestUser(HttpServletRequest request, RequestAnalyzerConfig config)
    {
        oauthUserInfoUrl = config.getOauthTokenInfoUrl();
        user = extractUser(request, config.getTokenUserField());
    }

    public Optional<String> getUser()
    {
        return user;
    }

    public Optional<UserInfo> getUserInfo()
    {
        return userInfo;
    }

    @SuppressWarnings("unused")
    public boolean userEquals(String testUser)
    {
        return user.filter(testUser::equals).isPresent();
    }

    private Optional<String> extractUserFromCookies(HttpServletRequest request, String userField)
    {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        log.debug("Trying to get user from cookie");
        Optional<Cookie> uiToken = Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(TRINO_UI_TOKEN_NAME) || cookie.getName().equals(TRINO_SECURE_UI_TOKEN_NAME))
                .findAny();
        return uiToken.map(cookie -> {
            if (cookie.getValue().split("\\.").length == 3) { //this is a JWS
                log.debug("Found JWS cookie");
                String token = cookie.getValue().split("\\.")[1];
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(new String(Base64.getDecoder().decode(token)));
                    if (node.has(userField)) {
                        log.debug("Got user from cookie");
                        return node.get(userField).asText();
                    }
                }
                catch (JsonProcessingException e) {
                    log.warn("Could not deserialize bearer token as json");
                }
            }
            return null;
        });
    }

    private Optional<String> extractUser(HttpServletRequest request, String userField)
    {
        String header;
        header = request.getHeader(TRINO_USER_HEADER_NAME);
        if (header != null) {
            return Optional.of(header);
        }

        Optional<String> user = extractUserFromAuthorizationHeader(request.getHeader("Authorization"), userField);
        if (user.isPresent()) {
            return user;
        }

        return extractUserFromCookies(request, userField);
    }

    private Optional<String> extractUserFromAuthorizationHeader(String header, String userField)
    {
        if (header == null) {
            return Optional.empty();
        }

        if (header.contains("Basic")) {
            return Optional.of(new String(Base64.getDecoder().decode(header.split(" ")[1]), StandardCharsets.UTF_8).split(":")[0]);
        }

        if (header.toLowerCase().contains("bearer")) {
            return extractUserFromBearerAuth(header, userField);
        }
        return Optional.empty();
    }

    private Optional<String> extractUserFromBearerAuth(String header, String userField)
    {
        log.debug("Trying to extract user from bearer token");
        int space = header.indexOf(' ');
        if ((space < 0) || !header.substring(0, space).equalsIgnoreCase("bearer")) {
            return Optional.empty();
        }

        String token = header.substring(space + 1).trim();
        ObjectMapper mapper = new ObjectMapper();

        if (header.split("\\.").length == 3) { //this is probably a JWS
            log.debug("Trying to extract from JWS");
            token = header.split("\\.")[1];
            try {
                JsonNode node = mapper.readTree(new String(Base64.getDecoder().decode(token)));
                if (node.has(userField)) {
                    log.debug("Trying to extract user from JWS json. User: %s", node.get(userField).asText());
                    return Optional.of(node.get(userField).asText());
                }
            }
            catch (JsonProcessingException e) {
                log.warn("Could not deserialize bearer token as json");
            }
        }

        if (!isNullOrEmpty(oauthUserInfoUrl)) {
            try {
                UserInfo userInfo = userInfoCache.get(token);
                this.userInfo = Optional.of(userInfo);
                return Optional.of(userInfo.getSubject().toString());
            }
            catch (ExecutionException e) {
                log.error(e, "Could not get userInfo");
            }
        }
        return Optional.empty();
    }

    private UserInfo getUserInfo(String token)
    {
        Request nimbusRequest = new UserInfoRequest(URI.create(oauthUserInfoUrl), new BearerAccessToken(token));

        try {
            UserInfoResponse userInfoResponse = parse(nimbusRequest.toHTTPRequest().send());
            if (!userInfoResponse.indicatesSuccess()) {
                log.error("Received bad response from userinfo endpoint: %s", userInfoResponse.toErrorResponse().getErrorObject());
                return null;
            }
            return userInfoResponse.toSuccessResponse().getUserInfo();
        }
        catch (IOException ex) {
            log.debug("Call to access token endpoint failed: %s", ex.getMessage());
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
