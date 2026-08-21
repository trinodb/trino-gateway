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
import io.trino.gateway.ha.router.PathFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.trino.gateway.ha.handler.HttpUtils.ORIGINAL_USER_HEADER;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static java.util.Objects.requireNonNull;

public class ClientCertificateJwtRequestAuthenticator
        implements ProxyRequestAuthenticator
{
    private static final Logger log = Logger.get(ClientCertificateJwtRequestAuthenticator.class);
    private static final int JWT_EXPIRATION_SECONDS = 300;
    private static final int JWT_REUSE_SAFETY_WINDOW_SECONDS = 10;
    // Backdate "not before" to tolerate gateway/cluster clock skew.
    private static final int JWT_CLOCK_SKEW_SECONDS = 60;

    private final ClientCertificateUserResolver clientCertificateUserResolver;
    private final String jwtPrincipalClaim;
    private final Optional<String> jwtIssuer;
    private final Optional<List<String>> jwtAudiences;
    private final Optional<String> jwtKeyId;
    private final LbKeyProvider keyProvider;
    private final Cache<String, CachedJwt> jwtCache;
    private final PathFilter pathFilter;
    private final boolean clientCertificateRequired;
    private final boolean overrideTrinoUser;

    public ClientCertificateJwtRequestAuthenticator(HaGatewayConfiguration configuration)
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = requireNonNull(configuration.getClientCertificateJwtAuthentication(), "clientCertificateJwtAuthentication is null");
        RequestAnalyzerConfig requestAnalyzerConfig = configuration.getRequestAnalyzerConfig();
        clientCertificateUserResolver = new ClientCertificateUserResolver(requestAnalyzerConfig);
        clientCertificateRequired = authConfig.isClientCertificateRequired();
        overrideTrinoUser = authConfig.isOverrideTrinoUser();
        jwtPrincipalClaim = requireNonNull(authConfig.getJwtPrincipalClaim(), "jwtPrincipalClaim is null");
        jwtIssuer = Optional.ofNullable(authConfig.getJwtIssuer()).filter(issuer -> !issuer.isBlank());
        jwtAudiences = Optional.ofNullable(authConfig.getJwtAudiences()).filter(audiences -> !audiences.isEmpty());
        jwtKeyId = Optional.ofNullable(authConfig.getJwtKeyId()).filter(keyId -> !keyId.isBlank());
        keyProvider = new LbKeyProvider(requireNonNull(authConfig.getJwtSigningKeyPair(), "jwtSigningKeyPair is null"));
        jwtCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(JWT_EXPIRATION_SECONDS - JWT_REUSE_SAFETY_WINDOW_SECONDS, TimeUnit.SECONDS)
                .build();
        pathFilter = new PathFilter(configuration);
    }

    @Override
    public HttpServletRequest authenticate(HttpServletRequest request)
    {
        if (!isAuthenticatedRequest(request)) {
            return request;
        }

        Optional<String> identity = getOrCreateIdentity(request);
        if (identity.isEmpty()) {
            if (clientCertificateRequired) {
                throw unauthorized("Client certificate authentication is required");
            }
            return request;
        }

        String jwt = getOrCreateJwt(identity.orElseThrow());

        ImmutableMap.Builder<String, String> forwardedHeaders = ImmutableMap.<String, String>builder()
                .put(AUTHORIZATION, "Bearer " + jwt);
        if (overrideTrinoUser) {
            forwardedHeaders.put(USER_HEADER, identity.orElseThrow());
            // Trino runs checkCanSetUser / checkCanImpersonateUser against this one, so it must be pinned too
            forwardedHeaders.put(ORIGINAL_USER_HEADER, identity.orElseThrow());
        }
        return new HeaderOverrideRequestWrapper(request, forwardedHeaders.buildOrThrow());
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

    private String getOrCreateJwt(String identity)
    {
        Instant now = Instant.now();
        CachedJwt cachedJwt = jwtCache.getIfPresent(identity);
        if (cachedJwt != null && cachedJwt.isReusableAt(now)) {
            return cachedJwt.token();
        }

        CachedJwt jwt = createJwt(identity, now);
        jwtCache.put(identity, jwt);
        return jwt.token();
    }

    private CachedJwt createJwt(String identity, Instant now)
    {
        Instant expiresAt = now.plusSeconds(JWT_EXPIRATION_SECONDS);
        Instant notBefore = now.minusSeconds(JWT_CLOCK_SKEW_SECONDS);
        JWTCreator.Builder tokenBuilder = JWT.create()
                .withIssuedAt(Date.from(now))
                .withNotBefore(Date.from(notBefore))
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
        // getRequestURI() is the raw request target, which requiresBackendAuthentication normalizes
        return pathFilter.requiresBackendAuthentication(request.getRequestURI());
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
