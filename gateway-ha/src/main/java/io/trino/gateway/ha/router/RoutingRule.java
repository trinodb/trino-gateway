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

import java.util.Map;

public interface RoutingRule
        extends Comparable<RoutingRule>
{
    boolean evaluateCondition(Map<String, Object> data, Map<String, Object> state);

    void evaluateAction(Map<String, String> result, Map<String, Object> data, Map<String, Object> state);

    Integer getPriority();

    static RoutingRule fromPersistedRoutingRule(io.trino.gateway.ha.persistence.dao.RoutingRule rule)
    {
        return switch (rule.routingRuleEngine()) {
            case MVEL -> new MVELRoutingRule(rule.name(), rule.description(), rule.priority(), rule.condition(), rule.actions());
            // type will not be defined in legacy file based rules, but they are all MVEL
            case null -> new MVELRoutingRule(rule.name(), rule.description(), rule.priority(), rule.condition(), rule.actions());
        };
    }
}
