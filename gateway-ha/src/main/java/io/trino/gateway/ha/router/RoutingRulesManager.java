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
import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RoutingRulesManager
{
    public List<RoutingRule> getRoutingRules(RoutingRulesConfiguration configuration)
            throws IOException
    {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        String rulesConfigPath = configuration.getRulesConfigPath();
        try {
            String content = Files.readString(Paths.get(rulesConfigPath), UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            List<RoutingRule> routingRulesList = new ArrayList<>();
            while (parser.nextToken() != null) {
                RoutingRule routingRules = yamlReader.readValue(parser, RoutingRule.class);
                routingRulesList.add(routingRules);
            }
            return routingRulesList;
        }
        catch (IOException e) {
            throw new IOException("Failed to read or parse routing rules configuration from path : " + rulesConfigPath, e);
        }
    }

    public List<RoutingRule> updateRoutingRules(RoutingRule routingRules, RoutingRulesConfiguration configuration)
            throws IOException
    {
        ImmutableList.Builder<RoutingRule> routingRulesBuilder = ImmutableList.builder();
        String rulesConfigPath = configuration.getRulesConfigPath();
        try {
            List<RoutingRule> routingRulesList = getRoutingRules(configuration);
            for (int i = 0; i < routingRulesList.size(); i++) {
                if (routingRulesList.get(i).name().equals(routingRules.name())) {
                    routingRulesList.set(i, routingRules);
                    break;
                }
            }
            ObjectMapper yamlWriter = new ObjectMapper(new YAMLFactory());
            StringBuilder yamlContent = new StringBuilder();
            for (RoutingRule rule : routingRulesList) {
                yamlContent.append(yamlWriter.writeValueAsString(rule));
                routingRulesBuilder.add(rule);
            }
            Files.writeString(Paths.get(rulesConfigPath), yamlContent.toString(), UTF_8);
        }
        catch (IOException e) {
            throw new IOException("Failed to parse or update routing rules configuration form path : " + rulesConfigPath, e);
        }
        return routingRulesBuilder.build();
    }
}
