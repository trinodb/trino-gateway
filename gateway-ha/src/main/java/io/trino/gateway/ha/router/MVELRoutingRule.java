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
import com.google.common.collect.ImmutableMap;
import org.mvel2.ParserContext;
import org.mvel2.debug.DebugTools;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.mvel2.MVEL.compileExpression;
import static org.mvel2.MVEL.executeExpression;
import static org.mvel2.debug.DebugTools.decompile;

public class MVELRoutingRule
        implements RoutingRule
{
    String name;
    String description;
    Integer priority;
    Serializable condition;
    List<Serializable> actions;
    ParserContext parserContext = new ParserContext();

    @JsonCreator
    public MVELRoutingRule(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("condition") Serializable condition,
            @JsonProperty("actions") List<Serializable> actions)
    {
        initializeParserContext(parserContext);

        this.name = requireNonNull(name, "name is null");
        this.description = requireNonNullElse(description, "");
        this.priority = requireNonNullElse(priority, 0);
        this.condition = requireNonNull(
                condition instanceof String stringCondition ? compileExpression(stringCondition, parserContext) : condition,
                "condition is null");
        this.actions = actions.stream().map(this::compileExpressionIfNecessary).collect(toImmutableList());
    }

    private Serializable compileExpressionIfNecessary(Serializable expression)
    {
        if (expression instanceof String stringExpression) {
            return compileExpression(stringExpression, parserContext);
        }
        return expression;
    }

    private void initializeParserContext(ParserContext parserContext)
    {
        parserContext.addPackageImport("java.util");

        // Members of java.lang, excluding potential security hazards such as Process and Runtime
        parserContext.addImport(Boolean.class);
        parserContext.addImport(Byte.class);
        parserContext.addImport(Character.class);
        parserContext.addImport(Double.class);
        parserContext.addImport(Enum.class);
        parserContext.addImport(Exception.class);
        parserContext.addImport(Float.class);
        parserContext.addImport(Integer.class);
        parserContext.addImport(Long.class);
        parserContext.addImport(Math.class);
        parserContext.addImport(Short.class);
        parserContext.addImport(StrictMath.class);
        parserContext.addImport(String.class);
        parserContext.addImport(StringBuffer.class);
        parserContext.addImport(StringBuilder.class);
        parserContext.addImport(FileBasedRoutingGroupSelector.class);
    }

    @Override
    public Integer getPriority()
    {
        return priority;
    }

    @Override
    public int compareTo(RoutingRule o)
    {
        if (o == null) {
            return 1;
        }
        return priority.compareTo(o.getPriority());
    }

    @Override
    public boolean evaluateCondition(Map<String, Object> data, Map<String, Object> state)
    {
        ImmutableMap.Builder<String, Object> variablesBuilder = ImmutableMap.builder();
        variablesBuilder.putAll(data);
        variablesBuilder.put("state", state);
        return (boolean) executeExpression(condition, variablesBuilder.build());
    }

    @Override
    public void evaluateAction(Map<String, String> result, Map<String, Object> data, Map<String, Object> state)
    {
        ImmutableMap.Builder<String, Object> variablesBuilder = ImmutableMap.builder();
        variablesBuilder.putAll(data);
        variablesBuilder.put("result", result);
        variablesBuilder.put("state", state);
        actions.forEach(action -> executeExpression(action, variablesBuilder.build()));
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .add("description", description)
                .add("priority", priority)
                .add("condition", decompile(condition))
                .add("actions", String.join(",", actions.stream().map(DebugTools::decompile).toList()))
                .add("parserContext", parserContext)
                .toString();
    }
}
