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
import io.trino.gateway.ha.domain.RoutingRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * File-based implementation of {@link RoutingRulesStore}.
 * Reads and writes routing rules from a YAML configuration file.
 */
public class FileRoutingRulesStore
        implements RoutingRulesStore
{
    private final String rulesConfigPath;

    public FileRoutingRulesStore(String rulesConfigPath)
    {
        this.rulesConfigPath = rulesConfigPath;
    }

    @Override
    public List<RoutingRule> getRoutingRules()
    {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper yamlReader = new ObjectMapper(yamlFactory);
        ImmutableList.Builder<RoutingRule> routingRulesBuilder = ImmutableList.builder();
        try {
            String content = Files.readString(Path.of(rulesConfigPath), UTF_8);
            YAMLParser parser = yamlFactory.createParser(content);
            while (parser.nextToken() != null) {
                RoutingRule routingRule = yamlReader.readValue(parser, RoutingRule.class);
                routingRulesBuilder.add(routingRule);
            }
            return routingRulesBuilder.build();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read or parse routing rules configuration from path: " + rulesConfigPath, e);
        }
    }

    @Override
    public synchronized List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
    {
        List<RoutingRule> currentRoutingRulesList = getRoutingRules();
        requireRuleExists(currentRoutingRulesList, routingRule.name());
        return writeRoutingRules(currentRoutingRulesList.stream()
                .map(rule -> rule.name().equals(routingRule.name()) ? routingRule : rule)
                .collect(toImmutableList()));
    }

    @Override
    public synchronized List<RoutingRule> createRoutingRule(RoutingRule routingRule)
    {
        List<RoutingRule> currentRoutingRulesList = getRoutingRules();
        if (containsRule(currentRoutingRulesList, routingRule.name())) {
            throw new IllegalArgumentException("Routing rule already exists: " + routingRule.name());
        }
        return writeRoutingRules(ImmutableList.<RoutingRule>builder()
                .addAll(currentRoutingRulesList)
                .add(routingRule)
                .build());
    }

    @Override
    public synchronized List<RoutingRule> deleteRoutingRule(String name)
    {
        List<RoutingRule> currentRoutingRulesList = getRoutingRules();
        requireRuleExists(currentRoutingRulesList, name);
        return writeRoutingRules(currentRoutingRulesList.stream()
                .filter(rule -> !rule.name().equals(name))
                .collect(toImmutableList()));
    }

    private List<RoutingRule> writeRoutingRules(List<RoutingRule> routingRules)
    {
        Path path = Path.of(rulesConfigPath);
        try (FileChannel fileChannel = FileChannel.open(path, WRITE, READ);
                FileLock lock = fileChannel.lock()) {
            ObjectMapper yamlWriter = new ObjectMapper(new YAMLFactory());
            StringBuilder yamlContent = new StringBuilder();
            for (RoutingRule rule : routingRules) {
                yamlContent.append(yamlWriter.writeValueAsString(rule));
            }
            Files.writeString(path, yamlContent.toString(), UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to parse or update routing rules configuration from path: " + rulesConfigPath, e);
        }
        return routingRules;
    }

    private static void requireRuleExists(List<RoutingRule> routingRules, String name)
    {
        if (!containsRule(routingRules, name)) {
            throw new IllegalArgumentException("Routing rule not found: " + name);
        }
    }

    private static boolean containsRule(List<RoutingRule> routingRules, String name)
    {
        return routingRules.stream().anyMatch(rule -> rule.name().equals(name));
    }
}
