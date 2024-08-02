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
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
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

import static com.nimbusds.openid.connect.sdk.UserInfoResponse.parse;
import static java.util.Objects.requireNonNull;

public class TrinoRequestUser
{
    public static final String TRINO_USER_HEADER_NAME = "X-Trino-User";
    public static final String TRINO_UI_TOKEN_NAME = "Trino-UI-Token";
    public static final String TRINO_SECURE_UI_TOKEN_NAME = "__Secure-Trino-ID-Token";

    private Optional<String> user = Optional.empty();
    private Optional<UserInfo> userInfo = Optional.empty();

    private static final Logger log = Logger.get(TrinoRequestUser.class);

    private final Optional<LoadingCache<String, UserInfo>> userInfoCache;

    private TrinoRequestUser(HttpServletRequest request, String userField, Optional<LoadingCache<String, UserInfo>> userInfoCache)
    {
        this.userInfoCache = requireNonNull(userInfoCache);
        user = extractUser(request, userField);
    }

    @JsonCreator
    public TrinoRequestUser(
            @JsonProperty("user") Optional<String> user,
            @JsonProperty("userInfo") String userInfo)
    {
        this.user = user;
        this.userInfo = Optional.ofNullable(userInfo).map(u -> {
            try {
                return UserInfo.parse(u);
            }
            catch (ParseException e) {
                log.error(e, "Could not parse UserInfo %s", u);
                return null;
            }
        });
        userInfoCache = Optional.empty();
    }

    @SuppressWarnings("unused")
    @JsonProperty
    public Optional<String> getUser()
    {
        return user;
    }

    @SuppressWarnings("unused")
    @JsonSerialize(using = UserInfoJsonSerializer.class)
    public Optional<UserInfo> getUserInfo()
    {
        return userInfo;
    }

    @SuppressWarnings("unused")
    public boolean userExistsAndEquals(String testUser)
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

        return uiToken.map(t -> {
            try {
                DecodedJWT jwt = JWT.decode(t.getValue());
                jwt.getClaim(userField);
                return jwt.getClaim(userField).asString();
            }
            catch (JWTDecodeException e) {
                log.warn("Could not deserialize token as jwt");
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
            try {
                return Optional.of(new String(Base64.getDecoder().decode(header.split(" ")[1]), StandardCharsets.UTF_8).split(":")[0]);
            }
            catch (IllegalArgumentException e) {
                log.error(e, "Authorization: Basic header contains invalid base64");
                log.debug("Invalid header value: " + header.split(" ")[1]);
                return Optional.empty();
            }
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

        if (header.split("\\.").length == 3) { //this is probably a JWS
            log.debug("Trying to extract from JWS");
            try {
                DecodedJWT jwt = JWT.decode(header);
                if (jwt.getClaims().containsKey(userField)) {
                    return Optional.of(jwt.getClaim(userField).asString());
                }
            }
            catch (JWTDecodeException e) {
                log.warn("Could not deserialize bearer token as json");
            }
        }

        if (userInfoCache.isPresent()) {
            try {
                userInfo = Optional.of(userInfoCache.orElseThrow().get(token));
                return Optional.of(userInfo.orElseThrow().getSubject().toString());
            }
            catch (ExecutionException e) {
                log.error(e, "Could not get userInfo");
            }
        }
        return Optional.empty();
    }

    public static class TrinoRequestUserProvider
    {
        private final String userField;
        private final Optional<URI> oauthUserInfoUrl;
        private final Optional<LoadingCache<String, UserInfo>> userInfoCache;

        public TrinoRequestUserProvider(RequestAnalyzerConfig config)
        {
            userField = config.getTokenUserField();
            if (config.getOauthTokenInfoUrl() != null) {
                oauthUserInfoUrl = Optional.of(URI.create(config.getOauthTokenInfoUrl()));
                userInfoCache = Optional.of(CacheBuilder.newBuilder()
                        .maximumSize(10000)
                        .expireAfterAccess(10, TimeUnit.MINUTES)
                        .build(CacheLoader.from(this::getUserInfo)));
            }
            else {
                oauthUserInfoUrl = Optional.empty();
                userInfoCache = Optional.empty();
            }
        }

        public TrinoRequestUser getInstance(HttpServletRequest request)
        {
            return new TrinoRequestUser(request, userField, userInfoCache);
        }

        private UserInfo getUserInfo(String token)
        {
            Request nimbusRequest = new UserInfoRequest(oauthUserInfoUrl.orElseThrow(), new BearerAccessToken(token));
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

    public static class UserInfoJsonSerializer
            extends StdSerializer<Optional<UserInfo>>
    {
        public UserInfoJsonSerializer()
        {
            this(null);
        }

        public UserInfoJsonSerializer(Class<Optional<UserInfo>> t)
        {
            super(t);
        }

        @Override
        public void serialize(Optional<UserInfo> userInfo, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException
        {
            userInfo.ifPresent(u -> {
                try {
                    jsonGenerator.writeString(u.toJSONString());
                }
                catch (IOException e) {
                    log.error(e, "Error serializing userInfo");
                }
            });
        }
    }
}
