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
import io.trino.gateway.ha.config.RequestAnalyzerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MVELFileRoutingGroupSelector
        extends RuleBasedRoutingGroupSelector<MVELRoutingRule>
{
    Path rulesPath;

    MVELFileRoutingGroupSelector(String rulesPath, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        super(requestAnalyzerConfig);
        this.rulesPath = Paths.get(rulesPath);

        setRules(readRulesFromPath(this.rulesPath));
    }

    @Override
    void reloadRules(long lastUpdatedTimeMillis)
    {
        try {
            BasicFileAttributes attr = Files.readAttributes(this.rulesPath, BasicFileAttributes.class);
            if (attr.lastModifiedTime().toMillis() > lastUpdatedTimeMillis) {
                synchronized (this) {
                    if (attr.lastModifiedTime().toMillis() > lastUpdatedTimeMillis) {
                        List<MVELRoutingRule> ruleList = readRulesFromPath(this.rulesPath);
                        setRules(ruleList);
                    }
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Could not access rules file", e);
        }
    }

    public List<MVELRoutingRule> readRulesFromPath(Path rulesPath)
    {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        try {
            String content = Files.readString(rulesPath, UTF_8);
            YAMLParser parser = new YAMLFactory().createParser(content);
            List<MVELRoutingRule> routingRulesList = new ArrayList<>();
            while (parser.nextToken() != null) {
                MVELRoutingRule routingRules = yamlReader.readValue(parser, MVELRoutingRule.class);
                routingRulesList.add(routingRules);
            }
            return routingRulesList;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read or parse routing rules configuration from path : " + rulesPath, e);
        }
    }
}
