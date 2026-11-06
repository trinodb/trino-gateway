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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

/**
 * Loads the rules from a {@link RoutingRulesStore} and compiles them, skipping and logging the rules that are not valid.
 * Stored rules are records with value-based equals, so the loader only recompiles when they change.
 * Not thread-safe; called from the single refresh thread.
 */
final class RoutingRulesLoader
{
    private static final Logger log = Logger.get(RoutingRulesLoader.class);

    private final RoutingRulesStore store;
    @Nullable
    private StoredRoutingRules lastStoredRules;
    @Nullable
    private LoadedRoutingRules lastLoadedRules;

    RoutingRulesLoader(RoutingRulesStore store)
    {
        this.store = requireNonNull(store, "store is null");
    }

    LoadedRoutingRules load()
    {
        StoredRoutingRules storedRules = store.readRoutingRules();
        if (lastLoadedRules != null && storedRules.equals(lastStoredRules)) {
            return new LoadedRoutingRules(lastLoadedRules.rules(), lastLoadedRules.skippedRulesCount(), ImmutableSet.of());
        }
        logNewReadErrors(storedRules.readErrors());
        LoadedRoutingRules loaded = compile(storedRules.rules(), changedOrRemovedRuleNames(storedRules.rules()));
        lastStoredRules = storedRules;
        lastLoadedRules = loaded;
        return loaded;
    }

    private void logNewReadErrors(Map<String, String> readErrors)
    {
        // Every refresh reads the store again, so log a rule only when it first becomes unreadable
        Map<String, String> previousReadErrors = lastStoredRules == null ? Map.of() : lastStoredRules.readErrors();
        readErrors.forEach((name, error) -> {
            if (!error.equals(previousReadErrors.get(name))) {
                log.error("Routing rule '%s' cannot be read: %s", name, error);
            }
        });
    }

    private Set<String> changedOrRemovedRuleNames(List<io.trino.gateway.ha.domain.RoutingRule> storedRules)
    {
        if (lastStoredRules == null) {
            return ImmutableSet.of();
        }
        Set<io.trino.gateway.ha.domain.RoutingRule> stored = ImmutableSet.copyOf(storedRules);
        return lastStoredRules.rules().stream()
                .filter(rule -> !stored.contains(rule))
                .map(io.trino.gateway.ha.domain.RoutingRule::name)
                .collect(toImmutableSet());
    }

    private static LoadedRoutingRules compile(List<io.trino.gateway.ha.domain.RoutingRule> storedRules, Set<String> changedOrRemovedRuleNames)
    {
        List<RoutingRule> compiled = new ArrayList<>();
        int skipped = 0;
        for (io.trino.gateway.ha.domain.RoutingRule rule : storedRules) {
            try {
                compiled.add(MVELRoutingRule.fromRoutingRule(rule));
            }
            catch (IllegalArgumentException e) {
                skipped++;
                log.error(e, "Skipping routing rule '%s' because it is not valid", rule.name());
            }
        }
        // A stable sort, so equal priorities keep the store's order: file order for FILE, name for DATABASE
        compiled.sort(Comparator.comparing(RoutingRule::getPriority));
        return new LoadedRoutingRules(ImmutableList.copyOf(compiled), skipped, changedOrRemovedRuleNames);
    }

    /**
     * @param changedOrRemovedRuleNames names of the previously stored rules that are no longer stored with the same value
     */
    record LoadedRoutingRules(List<RoutingRule> rules, int skippedRulesCount, Set<String> changedOrRemovedRuleNames) {}
}
