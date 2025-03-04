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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.persistence.dao.RoutingRule;
import io.trino.gateway.ha.persistence.dao.RoutingRulesDao;

import java.util.List;

public class DbRoutingRulesManager
        implements IRoutingRulesManager
{
    private final RoutingRulesDao routingRulesDao;
    private final boolean dbSupportsArray;

    ObjectMapper objectMapper = new ObjectMapper();

    public DbRoutingRulesManager(RoutingRulesDao routingRulesDao, boolean dbSupportsArray)
    {
        this.routingRulesDao = routingRulesDao;
        this.dbSupportsArray = dbSupportsArray;
    }

    @Override
    public List<RoutingRule> getRoutingRules()
    {
        if (dbSupportsArray) {
            return routingRulesDao.getAll();
        }
        return routingRulesDao.getAllNoListSupport();
    }

    @Override
    public List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
    {
        if (dbSupportsArray) {
            routingRulesDao.update(
                    routingRule.name(),
                    routingRule.description(),
                    routingRule.priority(),
                    routingRule.condition(),
                    routingRule.actions(),
                    routingRule.routingRuleEngine());
            return routingRulesDao.getAll();
        }
        else {
            routingRulesDao.updateNoListSupport(routingRule.name(),
                    routingRule.description(),
                    routingRule.priority(),
                    routingRule.condition(),
                    toJson(routingRule.actions()),
                    routingRule.routingRuleEngine());
            return routingRulesDao.getAllNoListSupport();
        }
    }

    @Override
    public void deleteRoutingRule(String name)
    {
        routingRulesDao.delete(name);
    }

    @Override
    public void createRoutingRule(RoutingRule routingRule)
    {
        if (dbSupportsArray) {
            routingRulesDao.create(
                    routingRule.name(),
                    routingRule.description(),
                    routingRule.priority(),
                    routingRule.condition(),
                    routingRule.actions(),
                    routingRule.routingRuleEngine());
        }
        else {
            routingRulesDao.createNoListSupport(routingRule.name(),
                    routingRule.description(),
                    routingRule.priority(),
                    routingRule.condition(),
                    toJson(routingRule.actions()),
                    routingRule.routingRuleEngine());
        }
    }

    private String toJson(Object object)
    {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed serializing routing rules to JSON", e);
        }
    }
}
