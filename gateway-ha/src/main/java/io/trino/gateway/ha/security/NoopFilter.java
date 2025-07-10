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

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.security.Principal;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;

@Priority(AUTHENTICATION)
public class NoopFilter
        implements ContainerRequestFilter
{
    @Override
    public void filter(final ContainerRequestContext requestContext)
            throws IOException
    {
        requestContext.setSecurityContext(new SecurityContext()
        {
            @Override
            public Principal getUserPrincipal()
            {
                return new LbPrincipal("user", "ADMIN_USER_API");
            }

            @Override
            public boolean isUserInRole(String role)
            {
                return true;
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
}
