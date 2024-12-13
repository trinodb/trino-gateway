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
import com.google.inject.Inject;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RoutingRulesManager
{
    private final RoutingRulesConfiguration routingRulesConfiguration;

    @Inject
    public RoutingRulesManager(HaGatewayConfiguration configuration)
    {
        this.routingRulesConfiguration = configuration.getRoutingRules();
    }

    public List<RoutingRule> getRoutingRules()
            throws IOException
    {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        String rulesConfigPath = routingRulesConfiguration.getRulesConfigPath();
        ImmutableList.Builder<RoutingRule> routingRulesBuilder = ImmutableList.builder();
        try {
            String content = Files.readString(Paths.get(rulesConfigPath), UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            while (parser.nextToken() != null) {
                RoutingRule routingRule = yamlReader.readValue(parser, RoutingRule.class);
                routingRulesBuilder.add(routingRule);
            }
            return routingRulesBuilder.build();
        }
        catch (IOException e) {
            throw new IOException("Failed to read or parse routing rules configuration from path : " + rulesConfigPath, e);
        }
    }

    public synchronized List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
            throws IOException
    {
        ImmutableList.Builder<RoutingRule> updatedRoutingRulesBuilder = ImmutableList.builder();
        String rulesConfigPath = routingRulesConfiguration.getRulesConfigPath();
        List<RoutingRule> currentRoutingRulesList = new ArrayList<>();
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        try {
            String content = Files.readString(Paths.get(rulesConfigPath), UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            while (parser.nextToken() != null) {
                RoutingRule currentRoutingRule = yamlReader.readValue(parser, RoutingRule.class);
                currentRoutingRulesList.add(currentRoutingRule);
            }
            for (int i = 0; i < currentRoutingRulesList.size(); i++) {
                if (currentRoutingRulesList.get(i).name().equals(routingRule.name())) {
                    currentRoutingRulesList.set(i, routingRule);
                    break;
                }
            }
            ObjectMapper yamlWriter = new ObjectMapper(new YAMLFactory());
            StringBuilder yamlContent = new StringBuilder();
            for (RoutingRule rule : currentRoutingRulesList) {
                yamlContent.append(yamlWriter.writeValueAsString(rule));
                updatedRoutingRulesBuilder.add(rule);
            }
            try (FileChannel fileChannel = FileChannel.open(Paths.get(rulesConfigPath), StandardOpenOption.WRITE, StandardOpenOption.READ);
                    FileLock lock = fileChannel.lock()) {
                Files.writeString(Paths.get(rulesConfigPath), yamlContent.toString(), UTF_8);
                lock.release();
            }
        }
        catch (IOException e) {
            throw new IOException("Failed to parse or update routing rules configuration form path : " + rulesConfigPath, e);
        }
        return updatedRoutingRulesBuilder.build();
    }
}
