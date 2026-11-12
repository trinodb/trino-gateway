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

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class TestRuleEvaluationErrors
{
    @Test
    void testKeepsFirstErrorPerRule()
    {
        RuleEvaluationErrors errors = new RuleEvaluationErrors();

        assertThat(errors.record("rule", "first", () -> true)).isTrue();
        assertThat(errors.record("rule", "second", () -> true)).isFalse();

        assertThat(errors.snapshot()).containsExactlyEntriesOf(Map.of("rule", "first"));
    }

    @Test
    void testIgnoresErrorFromReplacedRules()
    {
        RuleEvaluationErrors errors = new RuleEvaluationErrors();

        assertThat(errors.record("rule", "stale", () -> false)).isFalse();

        assertThat(errors.snapshot()).isEmpty();
    }

    @Test
    void testDropsErrorWhenRulesAreReplacedWhileRecording()
    {
        RuleEvaluationErrors errors = new RuleEvaluationErrors();
        // Current before the error is written, replaced right after
        AtomicInteger checks = new AtomicInteger();

        assertThat(errors.record("rule", "stale", () -> checks.getAndIncrement() == 0)).isTrue();

        assertThat(errors.snapshot()).isEmpty();
    }

    @Test
    void testClearsOnlyGivenRules()
    {
        RuleEvaluationErrors errors = new RuleEvaluationErrors();
        errors.record("changed", "error", () -> true);
        errors.record("unchanged", "error", () -> true);

        errors.clear(Set.of("changed", "removed"));

        assertThat(errors.snapshot()).containsOnlyKeys("unchanged");
        assertThat(errors.record("changed", "new error", () -> true)).isTrue();
    }
}
