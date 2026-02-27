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
package io.trino.gateway.ha.router.schema;

import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * Response from the routing service that includes:
 * - routingDecision: The target routing group for the request (Optional)
 * - routingCluster: The target routing cluster for the request (Optional)
 * - externalHeaders: Headers that can be set in the request (Currently can only be set in ExternalRoutingGroupSelector)
 */
public record RoutingSelectorResponse(@Nullable String routingGroup, @Nullable String routingCluster, Map<String, String> externalHeaders)
        implements RoutingResponse
{
    public RoutingSelectorResponse {
        externalHeaders = ImmutableMap.copyOf(externalHeaders);
    }

    public RoutingSelectorResponse(String routingGroup, String routingCluster)
    {
        this(routingGroup, routingCluster, ImmutableMap.of());
    }
}
