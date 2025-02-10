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
package io.trino.gateway.ha.domain;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * RoutingRules
 *
 * @param name name of the routing rule
 * @param description description of the routing rule. Defaults to an empty string if not provided, indicating the user intends it to be blank.
 * @param priority priority of the routing rule. Higher number represents higher priority. If two rules have same priority then order of execution is not guaranteed.
 * @param actions actions of the routing rule
 * @param condition condition of the routing rule
 */
public record RoutingRule(
        String name,
        String description,
        Integer priority,
        List<String> actions,
        String condition)
{
    public RoutingRule {
        requireNonNull(name, "name is null");
        description = requireNonNullElse(description, "");
        priority = requireNonNullElse(priority, 0);
        actions = ImmutableList.copyOf(actions);
        requireNonNull(condition, "condition is null");
    }
}
