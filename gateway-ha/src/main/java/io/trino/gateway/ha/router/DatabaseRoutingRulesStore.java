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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.RoutingRuleRecord;
import io.trino.gateway.ha.persistence.dao.RoutingRulesDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementException;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Database-backed implementation of {@link RoutingRulesStore}.
 * Stores routing rules in PostgreSQL, MySQL, or Oracle for persistence across pod restarts.
 * Rules are returned in evaluation order: priority ascending, then name in case-sensitive ascending order.
 */
public class DatabaseRoutingRulesStore
        implements RoutingRulesStore
{
    private static final Logger log = Logger.get(DatabaseRoutingRulesStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final String ACTIONS_READ_ERROR = "actions is not a JSON array of strings";
    // Sorted in Java because ORDER BY name depends on each database's collation
    private static final Comparator<RoutingRule> EVALUATION_ORDER = Comparator.comparing(RoutingRule::priority).thenComparing(RoutingRule::name);
    private final RoutingRulesDao dao;

    public DatabaseRoutingRulesStore(JdbcConnectionManager connectionManager)
    {
        this(requireNonNull(connectionManager, "connectionManager is null").getJdbi());
    }

    public DatabaseRoutingRulesStore(Jdbi jdbi)
    {
        this.dao = requireNonNull(jdbi, "jdbi is null").onDemand(RoutingRulesDao.class);
    }

    @Override
    public List<RoutingRule> getRoutingRules()
    {
        return readRoutingRules().rules();
    }

    @Override
    public StoredRoutingRules readRoutingRules()
    {
        List<RoutingRule> rules = new ArrayList<>();
        ImmutableMap.Builder<String, String> readErrors = ImmutableMap.builder();
        for (RoutingRuleRecord record : dao.findAll()) {
            // Rows written with SQL are not validated. Returning no actions makes the rule fail
            // validation, so it is skipped in routing and shown as not applied, and the other rules still load.
            List<String> actions = readActions(record.actions()).orElseGet(() -> {
                readErrors.put(record.name(), ACTIONS_READ_ERROR);
                log.debug("Routing rule '%s' actions are not a JSON array of strings: %s", record.name(), record.actions());
                return List.of();
            });
            rules.add(new RoutingRule(record.name(), record.description(), record.priority(), actions, record.condition()));
        }
        rules.sort(EVALUATION_ORDER);
        return new StoredRoutingRules(rules, readErrors.buildOrThrow());
    }

    @Override
    public List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
    {
        requireNonNull(routingRule, "routingRule is null");
        int updated = dao.update(
                routingRule.name(),
                routingRule.description(),
                routingRule.priority(),
                toActionsJson(routingRule.actions()),
                routingRule.condition());
        if (updated == 0) {
            throw new IllegalArgumentException("Routing rule not found: " + routingRule.name());
        }
        return rulesAfterWrite(routingRule.name());
    }

    @Override
    public List<RoutingRule> createRoutingRule(RoutingRule routingRule)
    {
        requireNonNull(routingRule, "routingRule is null");
        try {
            dao.create(
                    routingRule.name(),
                    routingRule.description(),
                    routingRule.priority(),
                    toActionsJson(routingRule.actions()),
                    routingRule.condition());
        }
        catch (StatementException e) {
            if (dao.countByName(routingRule.name()) > 0) {
                throw new IllegalArgumentException("Routing rule already exists: " + routingRule.name(), e);
            }
            throw e;
        }
        return rulesAfterWrite(routingRule.name());
    }

    @Override
    public List<RoutingRule> deleteRoutingRule(String name)
    {
        requireNonNull(name, "name is null");
        if (dao.delete(name) == 0) {
            throw new IllegalArgumentException("Routing rule not found: " + name);
        }
        return rulesAfterWrite(name);
    }

    private List<RoutingRule> rulesAfterWrite(String name)
    {
        try {
            return getRoutingRules();
        }
        catch (RuntimeException e) {
            throw new RoutingRulesReloadException(name, e);
        }
    }

    private String toActionsJson(List<String> actions)
    {
        try {
            return OBJECT_MAPPER.writeValueAsString(actions);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize actions to JSON", e);
        }
    }

    private static Optional<List<String>> readActions(String actionsJson)
    {
        try {
            List<String> actions = OBJECT_MAPPER.readValue(actionsJson, STRING_LIST_TYPE);
            if (actions == null || actions.stream().anyMatch(Objects::isNull)) {
                return Optional.empty();
            }
            return Optional.of(actions);
        }
        catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
