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
package io.trino.gateway.ha.router;

import io.airlift.http.client.HttpClient;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * RoutingSelector provides a way to match an HTTP request to a Gateway routing group.
 */
public interface RoutingSelector
{
    String ROUTING_GROUP_HEADER = "X-Trino-Routing-Group";

    /**
     * Routing selector that relies on the X-Trino-Routing-Group
     * header to determine the right routing group.
     */
    static RoutingSelector byRoutingGroupHeader()
    {
        return request -> new RoutingSelectorResponse(request.getHeader(ROUTING_GROUP_HEADER), null);
    }

    /**
     * Routing selector that uses routing engine rules
     * to determine the right routing group or cluster.
     */
    static RoutingSelector byRoutingRulesEngine(String rulesConfigPath, Duration rulesRefreshPeriod, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        return new FileBasedRoutingSelector(rulesConfigPath, rulesRefreshPeriod, requestAnalyzerConfig);
    }

    /**
     * Routing selector that uses RESTful API
     * to determine the right routing group.
     */
    static RoutingSelector byRoutingExternal(
            HttpClient httpClient,
            RulesExternalConfiguration rulesExternalConfiguration,
            RequestAnalyzerConfig requestAnalyzerConfig)
    {
        return new ExternalRoutingSelector(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
    }

    /**
     * Given an HTTP request find a routing destination to direct the request to. If a routing group or cluster cannot
     * be determined return null.
     */
    RoutingSelectorResponse findRoutingDestination(HttpServletRequest request);
}
