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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Suppliers.memoizeWithExpiration;

public class RulesRoutingGroupSelector
        implements RoutingGroupSelector
{
    private static final Logger log = Logger.get(RulesRoutingGroupSelector.class);
    public static final String RESULTS_ROUTING_GROUP_KEY = "routingGroup";

    private final Supplier<List<RoutingRule>> rules;
    private final IRoutingRulesManager routingRulesManager;

    private final boolean analyzeRequest;
    private final boolean clientsUseV2Format;
    private final int maxBodySize;
    private final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;

    public RulesRoutingGroupSelector(IRoutingRulesManager routingRulesManager, Duration rulesRefreshPeriod, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        analyzeRequest = requestAnalyzerConfig.isAnalyzeRequest();
        clientsUseV2Format = requestAnalyzerConfig.isClientsUseV2Format();
        maxBodySize = requestAnalyzerConfig.getMaxBodySize();
        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig);
        this.routingRulesManager = routingRulesManager;
        rules = memoizeWithExpiration(() -> this.routingRulesManager.getRoutingRules().stream().map(RoutingRule::fromPersistedRoutingRule).sorted().toList(),
                rulesRefreshPeriod.toJavaTime());
    }

    @Override
    public String findRoutingGroup(HttpServletRequest request)
    {
        Map<String, String> result = new HashMap<>();
        Map<String, Object> state = new HashMap<>();

        Map<String, Object> data;
        if (analyzeRequest) {
            TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                    request,
                    clientsUseV2Format,
                    maxBodySize);
            TrinoRequestUser trinoRequestUser = trinoRequestUserProvider.getInstance(request);
            data = ImmutableMap.of("request", request, "trinoQueryProperties", trinoQueryProperties, "trinoRequestUser", trinoRequestUser);
        }
        else {
            data = ImmutableMap.of("request", request);
        }

        rules.get().forEach(rule -> {
            if (rule.evaluateCondition(data, state)) {
                log.debug("%s evaluated to true on request: %s", rule, request);
                rule.evaluateAction(result, data, state);
            }
        });
        return result.get(RESULTS_ROUTING_GROUP_KEY);
    }
}
