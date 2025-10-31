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
package io.trino.gateway.proxyserver;

import com.google.inject.Inject;
import io.trino.gateway.ha.router.PathFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;

import java.io.IOException;
import java.net.URI;

/**
 * This pre-matching ContainerRequestFilter catches all requests and forwards
 * those that need to be routed to a Trino backend to {@link RouteToBackendResource}.
 * This enables the setting of additional paths that need to be forwarded in the configuration.
 */
@PreMatching
public class RouterPreMatchContainerRequestFilter
        implements ContainerRequestFilter
{
    public static final String ROUTE_TO_BACKEND = "/trino-gateway/internal/route_to_backend";

    private final PathFilter pathFilter;

    @Inject
    public RouterPreMatchContainerRequestFilter(PathFilter pathFilter)
    {
        this.pathFilter = pathFilter;
    }

    @Override
    public void filter(ContainerRequestContext request)
            throws IOException
    {
        if (pathFilter.isPathWhiteListed(request.getUriInfo().getRequestUri().getPath())) {
            request.setRequestUri(URI.create(ROUTE_TO_BACKEND));
        }
    }
}
