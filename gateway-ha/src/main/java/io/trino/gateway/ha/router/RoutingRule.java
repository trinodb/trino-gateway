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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public abstract class RoutingRule<T>
        implements Comparable<RoutingRule<T>>
{
    String name;
    String description;
    Integer priority;
    T condition;
    List<T> actions;

    @JsonCreator
    public RoutingRule(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("condition") T condition,
            @JsonProperty("actions") List<T> actions)
    {
        this.name = requireNonNull(name, "name is null");
        this.description = requireNonNullElse(description, "");
        this.priority = requireNonNullElse(priority, 0);
        this.condition = requireNonNull(condition, "condition is null");
        this.actions = requireNonNull(actions, "actions is null");
    }

    public abstract void evaluate(Map<String, Object> variables);

    @Override
    public int compareTo(RoutingRule o)
    {
        if (o == null) {
            return 1;
        }
        return priority.compareTo(o.priority);
    }
}
