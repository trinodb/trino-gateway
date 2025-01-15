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

import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.jeasy.rules.support.reader.YamlRuleDefinitionReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RuleReloadingRoutingGroupSelector
        implements RoutingGroupSelector
{
    private static final Logger log = Logger.get(RuleReloadingRoutingGroupSelector.class);
    private final RulesEngine rulesEngine = new DefaultRulesEngine();
    private final MVELRuleFactory ruleFactory = new MVELRuleFactory(new YamlRuleDefinitionReader());
    private final Path rulesConfigPath;
    private volatile Rules rules = new Rules();
    private volatile long lastUpdatedTime;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    private final RequestAnalyzerConfig requestAnalyzerConfig;
    private final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;

    RuleReloadingRoutingGroupSelector(String rulesConfigPath, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        this.rulesConfigPath = Path.of(rulesConfigPath);
        this.requestAnalyzerConfig = requestAnalyzerConfig;
        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig);
        try {
            rules = ruleFactory.createRules(
                    Files.newBufferedReader(this.rulesConfigPath, UTF_8));
            BasicFileAttributes attr = Files.readAttributes(this.rulesConfigPath,
                    BasicFileAttributes.class);
            lastUpdatedTime = attr.lastModifiedTime().toMillis();
        }
        catch (Exception e) {
            throw new RuntimeException("Error opening rules configuration file at "
                    + rulesConfigPath + "\n"
                    + "Using routing group header as default.", e);
        }
    }

    @Override
    public String findRoutingGroup(HttpServletRequest request)
    {
        try {
            BasicFileAttributes attr = Files.readAttributes(rulesConfigPath,
                    BasicFileAttributes.class);
            log.debug("File modified time: %s. lastUpdatedTime: %s", attr.lastModifiedTime(), lastUpdatedTime);
            if (attr.lastModifiedTime().toMillis() > lastUpdatedTime) {
                Lock writeLock = readWriteLock.writeLock();
                writeLock.lock();
                try {
                    if (attr.lastModifiedTime().toMillis() > lastUpdatedTime) {
                        // This check is performed again to prevent parsing the rules twice in case another
                        // thread finds the condition true and acquires the lock before this one
                        log.info("Updating rules to file modified at %s", attr.lastModifiedTime());
                        rules = ruleFactory.createRules(
                                Files.newBufferedReader(rulesConfigPath, UTF_8));
                        lastUpdatedTime = attr.lastModifiedTime().toMillis();
                    }
                }
                finally {
                    writeLock.unlock();
                }
            }

            Facts facts = new Facts();
            HashMap<String, String> result = new HashMap<String, String>();

            facts.put("request", request);
            if (requestAnalyzerConfig.isAnalyzeRequest()) {
                TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                        request,
                        requestAnalyzerConfig.isClientsUseV2Format(),
                        requestAnalyzerConfig.getMaxBodySize());
                TrinoRequestUser trinoRequestUser = trinoRequestUserProvider.getInstance(request);
                facts.put("trinoQueryProperties", trinoQueryProperties);
                facts.put("trinoRequestUser", trinoRequestUser);
            }
            facts.put("result", result);
            Lock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                rulesEngine.fire(rules, facts);
            }
            finally {
                readLock.unlock();
            }
            return result.get("routingGroup");
        }
        catch (Exception e) {
            log.error(e, "Error opening rules configuration file, using "
                    + "routing group header as default.");
            // Invalid rules could lead to perf problems as every thread goes into the writeLock
            // block until the issue is resolved
        }
        return request.getHeader(ROUTING_GROUP_HEADER);
    }
}
