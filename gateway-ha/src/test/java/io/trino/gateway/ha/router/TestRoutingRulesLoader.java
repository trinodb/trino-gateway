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
import io.trino.gateway.ha.router.RoutingRulesLoader.LoadedRoutingRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TestRoutingRulesLoader
{
    private static final RoutingRule HIGH = rule("high", 10, "true");
    private static final RoutingRule LOW = rule("low", 0, "true");
    private static final RoutingRule BAD_MVEL = rule("bad-mvel", 5, "request.getHeader(");

    @Test
    void testSkipsInvalidRulesAndSortsByPriority()
    {
        TestingStore store = new TestingStore(new StoredRoutingRules(List.of(HIGH, BAD_MVEL, LOW), Map.of()));

        LoadedRoutingRules loaded = new RoutingRulesLoader(store).load();

        assertThat(loaded.rules()).extracting(io.trino.gateway.ha.router.RoutingRule::getName).containsExactly("low", "high");
        assertThat(loaded.skippedRulesCount()).isEqualTo(1);
        assertThat(loaded.changedOrRemovedRuleNames()).isEmpty();
    }

    @Test
    void testReusesCompiledRulesWhenUnchanged()
    {
        TestingStore store = new TestingStore(new StoredRoutingRules(List.of(LOW, HIGH), Map.of()));
        RoutingRulesLoader loader = new RoutingRulesLoader(store);

        LoadedRoutingRules first = loader.load();
        LoadedRoutingRules second = loader.load();

        assertThat(second.rules()).isSameAs(first.rules());
        assertThat(second.changedOrRemovedRuleNames()).isEmpty();
    }

    @Test
    void testReportsChangedAndRemovedRules()
    {
        TestingStore store = new TestingStore(new StoredRoutingRules(List.of(LOW, HIGH), Map.of()));
        RoutingRulesLoader loader = new RoutingRulesLoader(store);
        loader.load();

        store.storedRules = new StoredRoutingRules(List.of(rule("low", 1, "true"), rule("added", 2, "true")), Map.of());
        LoadedRoutingRules loaded = loader.load();

        assertThat(loaded.changedOrRemovedRuleNames()).containsExactlyInAnyOrder("low", "high");
    }

    private static RoutingRule rule(String name, int priority, String condition)
    {
        return new RoutingRule(name, "", priority, List.of("result.put(\"routingGroup\", \"%s\")".formatted(name)), condition);
    }

    private static class TestingStore
            implements RoutingRulesStore
    {
        private volatile StoredRoutingRules storedRules;

        TestingStore(StoredRoutingRules storedRules)
        {
            this.storedRules = storedRules;
        }

        @Override
        public StoredRoutingRules readRoutingRules()
        {
            return storedRules;
        }

        @Override
        public List<RoutingRule> getRoutingRules()
        {
            return storedRules.rules();
        }

        @Override
        public List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RoutingRule> createRoutingRule(RoutingRule routingRule)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RoutingRule> deleteRoutingRule(String name)
        {
            throw new UnsupportedOperationException();
        }
    }
}
