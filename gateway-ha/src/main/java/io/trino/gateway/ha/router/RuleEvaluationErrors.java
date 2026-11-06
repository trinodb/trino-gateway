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

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * The first error each rule failed with when evaluated, kept until the rule changes.
 */
final class RuleEvaluationErrors
{
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    /**
     * Records the error unless the rule already has one, or the rules it was evaluated in are no longer in use.
     *
     * @param evaluatedRulesAreCurrent whether the rules the failing rule was evaluated in are still in use
     * @return true if this is the rule's first error since it last changed
     */
    boolean record(String ruleName, String error, BooleanSupplier evaluatedRulesAreCurrent)
    {
        if (!evaluatedRulesAreCurrent.getAsBoolean() || errors.putIfAbsent(ruleName, error) != null) {
            return false;
        }
        // A refresh may have replaced this rule while it was recorded; drop the error of the old version
        if (!evaluatedRulesAreCurrent.getAsBoolean()) {
            errors.remove(ruleName, error);
        }
        return true;
    }

    /**
     * Call after the new rules are in use, so a request still on the old rules cannot record an error that outlives them.
     */
    void clear(Set<String> ruleNames)
    {
        errors.keySet().removeAll(ruleNames);
    }

    Map<String, String> snapshot()
    {
        return ImmutableMap.copyOf(errors);
    }
}
