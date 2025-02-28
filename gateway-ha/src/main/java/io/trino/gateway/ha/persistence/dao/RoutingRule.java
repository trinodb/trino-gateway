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
package io.trino.gateway.ha.persistence.dao;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * RoutingRules
 *
 * @param name name of the routing rule
 * @param description description of the routing rule. Defaults to an empty string if not provided, indicating the user intends it to be blank.
 * @param priority priority of the routing rule. Higher number represents higher priority. If two rules have same priority then order of execution is not guaranteed.
 * @param actions actions of the routing rule
 * @param condition condition of the routing rule
 * @param routingRuleEngine the engine used for rule evaluation
 */

public record RoutingRule(
        @JsonProperty("name") @ColumnName("name") String name,
        @JsonProperty("description") @ColumnName("description") String description,
        @JsonProperty("priority") @ColumnName("priority") Integer priority,
        // "conditionExpression" is used as a column name because "condition" is a reserved word in MySQL
        @JsonProperty("condition") @ColumnName("conditionExpression") String condition,
        @JsonProperty("actions") @ColumnName("actions") List<String> actions,
        @JsonProperty("routingRuleEngine") @ColumnName("routingRuleEngine") RoutingRuleEngine routingRuleEngine)
{
    public RoutingRule
    {
        requireNonNull(name);
        requireNonNull(condition);
        requireNonNull(actions);
    }
}
