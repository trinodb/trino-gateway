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
import com.auth0.jwt.JWTCreator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.ClientCertificateJwtAuthenticationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.handler.HeaderOverrideRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.trino.gateway.ha.handler.HttpUtils.V1_QUERY_PATH;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static java.util.Objects.requireNonNull;

public class ClientCertificateJwtRequestAuthenticator
        implements ProxyRequestAuthenticator
{
    private static final Logger log = Logger.get(ClientCertificateJwtRequestAuthenticator.class);
    private static final int JWT_EXPIRATION_SECONDS = 300;
    private static final int JWT_REUSE_SAFETY_WINDOW_SECONDS = 10;

    private final ClientCertificateUserResolver clientCertificateUserResolver;
    private final String jwtPrincipalClaim;
    private final Optional<String> jwtIssuer;
    private final Optional<List<String>> jwtAudiences;
    private final Optional<String> jwtKeyId;
    private final LbKeyProvider keyProvider;
    private final Cache<String, CachedJwt> jwtCache;
    private final List<String> authenticatedPaths;

    public ClientCertificateJwtRequestAuthenticator(HaGatewayConfiguration configuration)
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = requireNonNull(configuration.getClientCertificateJwtAuthentication(), "clientCertificateJwtAuthentication is null");
        RequestAnalyzerConfig requestAnalyzerConfig = configuration.getRequestAnalyzerConfig();
        clientCertificateUserResolver = new ClientCertificateUserResolver(requestAnalyzerConfig);
        jwtPrincipalClaim = requireNonNull(authConfig.getJwtPrincipalClaim(), "jwtPrincipalClaim is null");
        jwtIssuer = Optional.ofNullable(authConfig.getJwtIssuer()).filter(issuer -> !issuer.isBlank());
        jwtAudiences = Optional.ofNullable(authConfig.getJwtAudiences()).filter(audiences -> !audiences.isEmpty());
        jwtKeyId = Optional.ofNullable(authConfig.getJwtKeyId()).filter(keyId -> !keyId.isBlank());
        keyProvider = new LbKeyProvider(requireNonNull(authConfig.getJwtSigningKeyPair(), "jwtSigningKeyPair is null"));
        jwtCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(JWT_EXPIRATION_SECONDS - JWT_REUSE_SAFETY_WINDOW_SECONDS, TimeUnit.SECONDS)
                .build();
        authenticatedPaths = Stream.concat(configuration.getStatementPaths().stream(), Stream.of(V1_QUERY_PATH))
                .distinct()
                .collect(toImmutableList());
    }

    @Override
    public HttpServletRequest authenticate(HttpServletRequest request)
    {
        if (!isAuthenticatedRequest(request)) {
            return request;
        }

        Optional<String> identity = getOrCreateIdentity(request);
        if (identity.isEmpty()) {
            return request;
        }

        String jwt = getOrCreateJwt(request, identity.orElseThrow());

        Map<String, String> forwardedHeaders = ImmutableMap.of(AUTHORIZATION, "Bearer " + jwt);
        return new HeaderOverrideRequestWrapper(request, forwardedHeaders);
    }

    private Optional<String> getOrCreateIdentity(HttpServletRequest request)
    {
        try {
            Optional<String> identity = clientCertificateUserResolver.resolveMappedUser(request);
            identity.ifPresent(value -> log.debug("Authenticated client certificate identity [%s]", value));
            return identity;
        }
        catch (UserMappingException e) {
            throw unauthorized(e.getMessage());
        }
    }

    private String getOrCreateJwt(HttpServletRequest request, String identity)
    {
        Instant now = Instant.now();
        CachedJwt cachedJwt = jwtCache.getIfPresent(identity);
        if (cachedJwt != null && cachedJwt.isReusableAt(now)) {
            return cachedJwt.token();
        }

        CachedJwt jwt = createJwt(request, identity, now);
        jwtCache.put(identity, jwt);
        return jwt.token();
    }

    @SuppressWarnings("unused") // request reserved for future claims expansion
    private CachedJwt createJwt(HttpServletRequest request, String identity, Instant now)
    {
        Instant expiresAt = now.plusSeconds(JWT_EXPIRATION_SECONDS);
        JWTCreator.Builder tokenBuilder = JWT.create()
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withSubject(identity);

        jwtIssuer.ifPresent(tokenBuilder::withIssuer);
        if (!jwtPrincipalClaim.equals("sub")) {
            tokenBuilder.withClaim(jwtPrincipalClaim, identity);
        }
        jwtAudiences.ifPresent(audiences -> tokenBuilder.withAudience(audiences.toArray(new String[0])));
        jwtKeyId.ifPresent(tokenBuilder::withKeyId);
        return new CachedJwt(tokenBuilder.sign(keyProvider.signingAlgorithm()), expiresAt);
    }

    private boolean isAuthenticatedRequest(HttpServletRequest request)
    {
        String requestUri = request.getRequestURI();
        for (String authenticatedPath : authenticatedPaths) {
            if (requestUri.startsWith(authenticatedPath)) {
                return true;
            }
        }
        return false;
    }

    private record CachedJwt(String token, Instant expiresAt)
    {
        private boolean isReusableAt(Instant now)
        {
            return now.isBefore(expiresAt.minusSeconds(JWT_REUSE_SAFETY_WINDOW_SECONDS));
        }
    }

    private static WebApplicationException unauthorized(String message)
    {
        return new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(TEXT_PLAIN_TYPE)
                        .entity(message)
                        .build());
    }
}
