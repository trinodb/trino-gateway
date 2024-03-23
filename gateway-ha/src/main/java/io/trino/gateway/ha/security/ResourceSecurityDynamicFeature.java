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
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;
import static java.util.Objects.requireNonNull;

@Priority(AUTHENTICATION)
public class ResourceSecurityDynamicFeature
        implements DynamicFeature
{
    private final ContainerRequestFilter filter;

    public ResourceSecurityDynamicFeature(ContainerRequestFilter filter)
    {
        this.filter = requireNonNull(filter);
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context)
    {
        if (resourceInfo.getResourceClass().getAnnotation(RolesAllowed.class) != null
                || resourceInfo.getResourceMethod().getAnnotation(RolesAllowed.class) != null) {
            context.register(filter);
        }
    }
}
