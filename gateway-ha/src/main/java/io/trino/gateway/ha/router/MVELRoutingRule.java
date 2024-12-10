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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.mvel2.MVEL.compileExpression;
import static org.mvel2.MVEL.executeExpression;

public class MVELRoutingRule
        extends RoutingRule<Serializable>
{
    @JsonCreator
    public MVELRoutingRule(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("condition") Serializable condition,
            @JsonProperty("actions") List<Serializable> actions)
    {
        super(
                name,
                description,
                priority,
                condition instanceof String stringCondition ? compileExpression(stringCondition) : condition,
                actions.stream().map(action -> {
                    if (action instanceof String stringAction) {
                        return compileExpression(stringAction);
                    }
                    else {
                        return action;
                    }
                }).collect(toImmutableList()));
    }

    @Override
    public void evaluate(Map<String, Object> variables)
    {
        if ((boolean) executeExpression(condition, variables)) {
            actions.forEach(action -> executeExpression(action, variables));
        }
    }
}
