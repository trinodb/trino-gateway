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
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import jakarta.servlet.http.HttpServletRequest;

/**
 * RoutingGroupSelector provides a way to match an HTTP request to a Gateway routing group.
 */
public interface RoutingGroupSelector
{
    String ROUTING_GROUP_HEADER = "X-Trino-Routing-Group";

    /**
     * Routing group selector that relies on the X-Trino-Routing-Group
     * header to determine the right routing group.
     */
    static RoutingGroupSelector byRoutingGroupHeader()
    {
        return request -> request.getHeader(ROUTING_GROUP_HEADER);
    }

    /**
     * Routing group selector that uses routing engine rules
     * to determine the right routing group.
     */
    static RoutingGroupSelector byRoutingRulesEngine(HaGatewayConfiguration configuration)
    {
        IRoutingRulesManager routingRulesManager = new ForwardingRoutingRulesManager(configuration);
        RoutingRulesConfiguration routingRulesConfig = configuration.getRoutingRules();
        return new RulesRoutingGroupSelector(routingRulesManager, routingRulesConfig.getRulesRefreshPeriod(), configuration.getRequestAnalyzerConfig());
    }

    /**
     * Routing group selector that uses RESTful API
     * to determine the right routing group.
     */
    static RoutingGroupSelector byRoutingExternal(
            HttpClient httpClient,
            RulesExternalConfiguration rulesExternalConfiguration,
            RequestAnalyzerConfig requestAnalyzerConfig)
    {
        return new ExternalRoutingGroupSelector(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
    }

    /**
     * Given an HTTP request find a routing group to direct the request to. If a routing group cannot
     * be determined return null.
     */
    String findRoutingGroup(HttpServletRequest request);
}
