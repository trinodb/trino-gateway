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
package io.trino.gateway.ha.handler.schema;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Contains the complete result of the routing process including:
 * - routingDestination: Contains the routing group, cluster host, and target URI to route the request to
 * - modifiedRequest: The request with any headers set by the external routing service
 * or other routing selectors. The modifiedRequest might be a wrapper around the original
 * request with custom header handling.
 */
public record RoutingTargetResponse(
        RoutingDestination routingDestination,
        HttpServletRequest modifiedRequest) {}
