package io.trino.gateway.ha.router;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.jeasy.rules.support.reader.YamlRuleDefinitionReader;

@Slf4j
public class RuleReloadingRoutingGroupSelector
    implements RoutingGroupSelector  {

  private RulesEngine rulesEngine = new DefaultRulesEngine();
  private MVELRuleFactory ruleFactory = new MVELRuleFactory(new YamlRuleDefinitionReader());
  private String rulesConfigPath;
  private volatile Rules rules = new Rules();
  private volatile long lastUpdatedTime;
  private ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

  RuleReloadingRoutingGroupSelector(String rulesConfigPath) {
    this.rulesConfigPath = rulesConfigPath;
    try {
      rules = ruleFactory.createRules(
              new FileReader(rulesConfigPath));
      BasicFileAttributes attr = Files.readAttributes(Path.of(rulesConfigPath),
              BasicFileAttributes.class);
      lastUpdatedTime = attr.lastModifiedTime().toMillis();

    } catch (Exception e) {
      log.error("Error opening rules configuration file, using "
              + "routing group header as default.", e);
    }
  }

  @Override
  public String findRoutingGroup(HttpServletRequest request) {
    try {
      BasicFileAttributes attr = Files.readAttributes(Path.of(rulesConfigPath),
              BasicFileAttributes.class);
      if (attr.lastModifiedTime().toMillis() > lastUpdatedTime) {
        Lock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
          if (attr.lastModifiedTime().toMillis() > lastUpdatedTime) {
            // This check is performed again to prevent parsing the rules twice in case another
            // thread finds the condition true and acquires the lock before this one
            log.info(String.format("Updating rules to file modified at %s",
                    attr.lastModifiedTime()));
            rules = ruleFactory.createRules(
                    new FileReader(rulesConfigPath));
            lastUpdatedTime = attr.lastModifiedTime().toMillis();
          }
        } finally {
          writeLock.unlock();
        }
      }
      Facts facts = new Facts();
      HashMap<String, String> result = new HashMap<String, String>();
      facts.put("request", request);
      facts.put("result", result);
      Lock readLock = readWriteLock.readLock();
      readLock.lock();
      try {
        rulesEngine.fire(rules, facts);
      } finally {
        readLock.unlock();
      }
      return result.get("routingGroup");

    } catch (Exception e) {
      log.error("Error opening rules configuration file, using "
              + "routing group header as default.", e);
      // Invalid rules could lead to perf problems as every thread goes into the writeLock
      // block until the issue is resolved
    }
    return request.getHeader(ROUTING_GROUP_HEADER);
  }
}
