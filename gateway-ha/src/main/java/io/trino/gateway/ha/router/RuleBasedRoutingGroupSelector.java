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

import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.sort;

public abstract class RuleBasedRoutingGroupSelector<T extends RoutingRule<?>>
        implements RoutingGroupSelector
{
    public static final String RESULTS_ROUTING_GROUP_KEY = "routingGroup";

    private List<T> rules;
    final boolean analyzeRequest;
    final boolean clientsUseV2Format;
    final int maxBodySize;
    final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;
    private volatile long lastUpdatedTimeMillis;

    RuleBasedRoutingGroupSelector(RequestAnalyzerConfig requestAnalyzerConfig)
    {
        this(new ArrayList<>(), requestAnalyzerConfig);
    }

    public RuleBasedRoutingGroupSelector(List<T> rules, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        setRules(rules);
        analyzeRequest = requestAnalyzerConfig.isAnalyzeRequest();
        clientsUseV2Format = requestAnalyzerConfig.isClientsUseV2Format();
        maxBodySize = requestAnalyzerConfig.getMaxBodySize();
        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig);
    }

    abstract void reloadRules(long lastUpdatedTimeMillis);

    void setRules(List<T> rules)
    {
        this.rules = new ArrayList<>(rules);
        lastUpdatedTimeMillis = System.currentTimeMillis();
        sort(this.rules);
    }

    // TODO: add CRUD operations for the rules

    @Override
    public String findRoutingGroup(HttpServletRequest request)
    {
        reloadRules(lastUpdatedTimeMillis);
        Map<String, String> result = new HashMap<>();
        Map<String, Object> variables;
        if (analyzeRequest) {
            TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                    request,
                    clientsUseV2Format,
                    maxBodySize);
            TrinoRequestUser trinoRequestUser = trinoRequestUserProvider.getInstance(request);
            variables = ImmutableMap.of("result", result, "request", request, "trinoQueryProperties", trinoQueryProperties, "trinoRequestUser", trinoRequestUser);
        }
        else {
            variables = ImmutableMap.of("result", result, "request", request);
        }

        rules.forEach(rule -> rule.evaluate(variables));
        return result.get(RESULTS_ROUTING_GROUP_KEY);
    }
}
