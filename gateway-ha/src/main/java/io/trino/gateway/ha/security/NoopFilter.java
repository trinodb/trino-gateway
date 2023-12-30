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

import io.dropwizard.auth.AuthFilter;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.security.Principal;

@Priority(Priorities.AUTHENTICATION)
public class NoopFilter<P extends Principal>
        extends AuthFilter<String, P>
{
    public NoopFilter()
    {
    }

    @Override
    public void filter(final ContainerRequestContext requestContext)
            throws IOException
    {
        try {
            if (!authenticate(requestContext, "", SecurityContext.BASIC_AUTH)) {
                throw new Exception();
            }
        }
        catch (Exception e) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    public static class Builder<P extends Principal>
            extends AuthFilterBuilder<String, P, NoopFilter<P>>
    {
        @Override
        protected NoopFilter<P> newInstance()
        {
            return new NoopFilter<>();
        }
    }
}
