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

    public ChainedAuthFilter(List<ContainerRequestFilter> filters)
    {
        this.filters = requireNonNull(filters);
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
            catch (Exception ignored) {
            }
        }
        throw new ForbiddenException("Authentication error");
    }
}
