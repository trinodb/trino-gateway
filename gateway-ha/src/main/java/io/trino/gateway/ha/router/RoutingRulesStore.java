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

import io.trino.gateway.ha.domain.RoutingRule;

import java.util.List;
import java.util.Map;

/**
 * Interface for storing and retrieving routing rules.
 * Implementations can use different backends (file, database, etc.).
 */
public interface RoutingRulesStore
{
    /**
     * Get all routing rules. The file store returns them in file order;
     * the database store returns them in evaluation order (priority ascending, then name).
     */
    List<RoutingRule> getRoutingRules();

    /**
     * Get all routing rules, as {@link #getRoutingRules()} does, together with the rules whose stored values could not be read.
     */
    default StoredRoutingRules readRoutingRules()
    {
        return new StoredRoutingRules(getRoutingRules(), Map.of());
    }

    /**
     * Replace the existing routing rule that has the same name.
     *
     * @return the updated list of all routing rules
     * @throws IllegalArgumentException if no rule with that name exists
     */
    List<RoutingRule> updateRoutingRule(RoutingRule routingRule);

    /**
     * Add a new routing rule.
     *
     * @return the updated list of all routing rules
     * @throws IllegalArgumentException if a rule with that name already exists
     */
    List<RoutingRule> createRoutingRule(RoutingRule routingRule);

    /**
     * Remove the routing rule with the given name.
     *
     * @return the updated list of all routing rules
     * @throws IllegalArgumentException if no rule with that name exists
     */
    List<RoutingRule> deleteRoutingRule(String name);
}
