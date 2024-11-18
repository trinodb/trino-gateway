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

import io.trino.gateway.ha.security.util.AuthenticationException;
import io.trino.gateway.ha.security.util.Authorizer;
import io.trino.gateway.ha.security.util.IdTokenAuthenticator;
import jakarta.annotation.Nullable;
import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
import java.util.Optional;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;
import static java.util.Objects.requireNonNull;

@Priority(AUTHENTICATION)
public class LbFilter
        implements ContainerRequestFilter
{
    private final IdTokenAuthenticator idTokenAuthenticator;
    private final Authorizer lbAuthorizer;
    private final String prefix;
    private final LbUnauthorizedHandler lbUnauthorizedHandler;

    public LbFilter(IdTokenAuthenticator idTokenAuthenticator, Authorizer lbAuthorizer, String prefix, LbUnauthorizedHandler lbUnauthorizedHandler)
    {
        this.idTokenAuthenticator = requireNonNull(idTokenAuthenticator, "idTokenAuthenticator is null");
        this.lbAuthorizer = requireNonNull(lbAuthorizer, "lbAuthorizer is null");
        this.prefix = requireNonNull(prefix, "prefix is null");
        this.lbUnauthorizedHandler = requireNonNull(lbUnauthorizedHandler, "lbUnauthorizedHandler is null");
    }

    /**
     * Filters requests by checking for the token cookie and authorization header,
     * and authenticates the user using the filter's authenticator.
     */
    @Override
    public void filter(final ContainerRequestContext requestContext)
            throws WebApplicationException
    {
        // Checks for cookie, if not find then search for authorization header
        try {
            String idToken = Optional
                    .ofNullable(requestContext.getCookies().get(SessionCookie.OAUTH_ID_TOKEN))
                    .map(Cookie::getValue)
                    .orElse(getToken(requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));

            LbPrincipal principal = idTokenAuthenticator.authenticate(idToken)
                    .orElseThrow(() -> new AuthenticationException("Authentication error"));
            requestContext.setSecurityContext(new SecurityContext()
            {
                @Override
                public Principal getUserPrincipal()
                {
                    return principal;
                }

                @Override
                public boolean isUserInRole(String role)
                {
                    return lbAuthorizer.authorize(principal, role, requestContext);
                }

                @Override
                public boolean isSecure()
                {
                    return requestContext.getSecurityContext().isSecure();
                }

                @Override
                public String getAuthenticationScheme()
                {
                    return SecurityContext.BASIC_AUTH;
                }
            });
        }
        catch (Exception e) {
            throw new WebApplicationException(lbUnauthorizedHandler.buildResponse());
        }
    }

    /**
     * Parses a value of the `Authorization` header in the form of `Bearer a892bf3e284da9bb40648ab10`.
     *
     * @param header the value of the `Authorization` header
     * @return a token
     */
    @Nullable
    private String getToken(String header)
    {
        if (header == null) {
            return null;
        }

        final int space = header.indexOf(' ');
        if (space <= 0) {
            return null;
        }

        final String method = header.substring(0, space);
        if (!prefix.equalsIgnoreCase(method)) {
            return null;
        }

        return header.substring(space + 1);
    }
}
