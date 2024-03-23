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
import io.trino.gateway.ha.security.util.BasicCredentials;
import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.security.Principal;

import static io.trino.gateway.ha.security.util.BasicCredentials.extractBasicAuthCredentials;
import static jakarta.ws.rs.Priorities.AUTHENTICATION;
import static java.util.Objects.requireNonNull;

@Priority(AUTHENTICATION)
public class BasicAuthFilter
        implements ContainerRequestFilter
{
    private final ApiAuthenticator apiAuthenticator;
    private final Authorizer lbAuthorizer;
    private final LbUnauthorizedHandler lbUnauthorizedHandler;

    public BasicAuthFilter(ApiAuthenticator apiAuthenticator, Authorizer lbAuthorizer, LbUnauthorizedHandler lbUnauthorizedHandler)
    {
        this.apiAuthenticator = requireNonNull(apiAuthenticator);
        this.lbAuthorizer = requireNonNull(lbAuthorizer);
        this.lbUnauthorizedHandler = requireNonNull(lbUnauthorizedHandler, "lbUnauthorizedHandler is null");
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        try {
            BasicCredentials basicCredentials = extractBasicAuthCredentials(requestContext);
            LbPrincipal principal = apiAuthenticator.authenticate(basicCredentials)
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
}
