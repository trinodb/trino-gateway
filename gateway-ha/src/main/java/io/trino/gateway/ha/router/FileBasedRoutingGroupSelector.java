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
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.sort;

public class FileBasedRoutingGroupSelector
        implements RoutingGroupSelector
{
    public static final String RESULTS_ROUTING_GROUP_KEY = "routingGroup";

    private List<RoutingRule> rules;
    final boolean analyzeRequest;
    final boolean clientsUseV2Format;
    final int maxBodySize;
    final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;
    private volatile long lastUpdatedTimeMillis;
    Path rulesPath;

    public FileBasedRoutingGroupSelector(String rulesPath, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        analyzeRequest = requestAnalyzerConfig.isAnalyzeRequest();
        clientsUseV2Format = requestAnalyzerConfig.isClientsUseV2Format();
        maxBodySize = requestAnalyzerConfig.getMaxBodySize();
        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig);
        this.rulesPath = Paths.get(rulesPath);

        setRules(readRulesFromPath(this.rulesPath));
    }

    void setRules(List<RoutingRule> rules)
    {
        this.rules = new ArrayList<>(rules);
        lastUpdatedTimeMillis = System.currentTimeMillis();
        sort(this.rules);
    }

    @Override
    public String findRoutingGroup(HttpServletRequest request)
    {
        reloadRules(lastUpdatedTimeMillis);
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

        rules.forEach(rule -> {
            if (rule.evaluateCondition(data, state)) {
                rule.evaluateAction(result, data, state);
            }
        });
        return result.get(RESULTS_ROUTING_GROUP_KEY);
    }

    void reloadRules(long lastUpdatedTimeMillis)
    {
        try {
            BasicFileAttributes attr = Files.readAttributes(this.rulesPath, BasicFileAttributes.class);
            if (attr.lastModifiedTime().toMillis() <= lastUpdatedTimeMillis) {
                return;
            }
            synchronized (this) {
                // Prevent re-entry in case another thread passes the first check while rules are being updated
                if (attr.lastModifiedTime().toMillis() > lastUpdatedTimeMillis) {
                    List<RoutingRule> ruleList = readRulesFromPath(this.rulesPath);
                    setRules(ruleList);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Could not access rules file", e);
        }
    }

    public List<RoutingRule> readRulesFromPath(Path rulesPath)
    {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        try {
            String content = Files.readString(rulesPath, UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            List<RoutingRule> routingRulesList = new ArrayList<>();
            while (parser.nextToken() != null) {
                MVELRoutingRule routingRules = yamlReader.readValue(parser, MVELRoutingRule.class);
                routingRulesList.add(routingRules);
            }
            return routingRulesList;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read or parse routing rules configuration from path: " + rulesPath, e);
        }
    }
}
