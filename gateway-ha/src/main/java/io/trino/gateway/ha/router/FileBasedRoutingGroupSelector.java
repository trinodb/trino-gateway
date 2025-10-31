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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.sort;

public class FileBasedRoutingGroupSelector
        implements RoutingGroupSelector
{
    private static final Logger log = Logger.get(FileBasedRoutingGroupSelector.class);
    public static final String RESULTS_ROUTING_GROUP_KEY = "routingGroup";

    private static final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());

    private final Supplier<List<RoutingRule>> rules;
    private final boolean analyzeRequest;

    public FileBasedRoutingGroupSelector(String rulesPath, Duration rulesRefreshPeriod, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        analyzeRequest = requestAnalyzerConfig.isAnalyzeRequest();

        rules = memoizeWithExpiration(() -> readRulesFromPath(Path.of(rulesPath)), rulesRefreshPeriod.toJavaTime());
    }

    @Override
    public RoutingSelectorResponse findRoutingDestination(HttpServletRequest request)
    {
        Map<String, String> result = new HashMap<>();
        Map<String, Object> state = new HashMap<>();

        Map<String, Object> data;
        if (analyzeRequest) {
            TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
            TrinoRequestUser trinoRequestUser = (TrinoRequestUser) request.getAttribute(TRINO_REQUEST_USER);
            data = ImmutableMap.of("request", request, TRINO_QUERY_PROPERTIES, trinoQueryProperties, TRINO_REQUEST_USER, trinoRequestUser);
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
        return new RoutingSelectorResponse(result.get(RESULTS_ROUTING_GROUP_KEY));
    }

    public List<RoutingRule> readRulesFromPath(Path rulesPath)
    {
        try {
            String content = Files.readString(rulesPath, UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            List<RoutingRule> routingRulesList = new ArrayList<>();
            while (parser.nextToken() != null) {
                MVELRoutingRule routingRules = yamlReader.readValue(parser, MVELRoutingRule.class);
                routingRulesList.add(routingRules);
            }
            sort(routingRulesList);
            return routingRulesList;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read or parse routing rules configuration from path: " + rulesPath, e);
        }
    }
}
