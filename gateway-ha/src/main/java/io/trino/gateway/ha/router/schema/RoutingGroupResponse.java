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

import jakarta.annotation.Nullable;

import java.util.Map;

/**
 Interface representing the response from a routing group selector.
 This interface defines the contract for responses that determine how requests should be routed within
    the Trino Gateway system.

 Implementations of this interface are used to:
    * Specify the target routing group for a request
    * Provide additional headers that should be added to the request
 */
public interface RoutingGroupResponse
{
    @Nullable String routingGroup();

    Map<String, String> externalHeaders();

    Boolean strictRouting();
}
