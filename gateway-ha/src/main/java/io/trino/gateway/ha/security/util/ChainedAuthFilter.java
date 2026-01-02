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
package io.trino.gateway.ha.security.util;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.security.ApiAuthenticator;
import io.trino.gateway.ha.security.AuthorizationManager;
import io.trino.gateway.ha.security.BasicAuthFilter;
import io.trino.gateway.ha.security.FormAuthenticator;
import io.trino.gateway.ha.security.LbAuthenticator;
import io.trino.gateway.ha.security.LbFilter;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbUnauthorizedHandler;
import jakarta.annotation.Nullable;
import jakarta.annotation.Priority;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.io.IOException;
import java.util.List;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;
import static java.util.Objects.requireNonNull;

@Priority(AUTHENTICATION)
public class ChainedAuthFilter
        implements ContainerRequestFilter
{
    private final List<ContainerRequestFilter> filters;

    @Inject
    public ChainedAuthFilter(
            @Nullable LbOAuthManager oauthManager,
            @Nullable LbFormAuthManager formAuthManager,
            AuthorizationManager authorizationManager,
            HaGatewayConfiguration config,
            Authorizer authorizer)
    {
        requireNonNull(authorizationManager, "authorizationManager is null");
        requireNonNull(authorizer, "authorizer is null");

        ImmutableList.Builder<ContainerRequestFilter> authFilters = ImmutableList.builder();
        String defaultType = config.getAuthentication().getDefaultType();
        if (oauthManager != null) {
            authFilters.add(new LbFilter(
                    new LbAuthenticator(oauthManager, authorizationManager),
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(defaultType)));
        }

        if (formAuthManager != null) {
            authFilters.add(new LbFilter(
                    new FormAuthenticator(formAuthManager, authorizationManager),
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(defaultType)));

            authFilters.add(new BasicAuthFilter(
                    new ApiAuthenticator(formAuthManager, authorizationManager),
                    authorizer,
                    new LbUnauthorizedHandler(defaultType)));
        }
        this.filters = requireNonNull(authFilters.build());
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext)
            throws IOException
    {
        for (ContainerRequestFilter filter : filters) {
            try {
                filter.filter(containerRequestContext);
                return;
            }
            catch (Exception _) {
                // Suppress exception and try next filter
            }
        }
        throw new ForbiddenException("Authentication error");
    }
}
