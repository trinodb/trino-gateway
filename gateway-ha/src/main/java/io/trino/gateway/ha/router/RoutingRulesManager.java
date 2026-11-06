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

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.domain.response.RoutingRuleStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Manager for routing rules that validates rules before delegating to a {@link RoutingRulesStore}.
 * The store implementation is determined by the configuration (FILE or DATABASE).
 * After a successful modification, refreshes the routing group selector and waits for it,
 * so the change is in effect on this pod, and shown in the rule status, when the call returns.
 */
public class RoutingRulesManager
{
    private static final Logger log = Logger.get(RoutingRulesManager.class);

    // Matches the routing_rules.name column size
    private static final int MAX_NAME_BYTES = 256;
    // Bounds how long a write waits for the reload, so a slow or hung store cannot hold the request
    private static final long REFRESH_WAIT_SECONDS = 5;

    private final RoutingRulesStore store;
    private final RoutingRulesEngine rulesEngine;

    @Inject
    public RoutingRulesManager(RoutingRulesStore store, RoutingRulesEngine rulesEngine)
    {
        this.store = requireNonNull(store, "store is null");
        this.rulesEngine = requireNonNull(rulesEngine, "rulesEngine is null");
    }

    public List<RoutingRule> getRoutingRules()
    {
        return store.getRoutingRules();
    }

    /**
     * All stored rules for the admin view, each marked with whether it is applied.
     * Uses the same check as routing, so a rule the router skips is shown as not applied, with the reason.
     * A rule that failed when evaluated on this pod since it last changed is still applied, so it is shown as active with the error.
     */
    public List<RoutingRuleStatus> getRoutingRulesWithStatus()
    {
        Map<String, String> evaluationErrors = rulesEngine.getRuleEvaluationErrors();
        StoredRoutingRules storedRules = store.readRoutingRules();
        Map<String, String> readErrors = storedRules.readErrors();
        return storedRules.rules().stream()
                .map(rule -> {
                    String readError = readErrors.get(rule.name());
                    if (readError != null) {
                        return RoutingRuleStatus.notApplied(rule, "Routing rule '%s' cannot be read: %s".formatted(rule.name(), readError));
                    }
                    try {
                        MVELRoutingRule.fromRoutingRule(rule);
                    }
                    catch (IllegalArgumentException e) {
                        return RoutingRuleStatus.notApplied(rule, e.getMessage());
                    }
                    String evaluationError = evaluationErrors.get(rule.name());
                    return evaluationError == null
                            ? RoutingRuleStatus.active(rule)
                            : RoutingRuleStatus.activeWithError(rule, "Fails for some requests: " + evaluationError);
                })
                .toList();
    }

    public List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
    {
        validate(routingRule);
        return writeAndRefresh(() -> store.updateRoutingRule(routingRule));
    }

    public List<RoutingRule> createRoutingRule(RoutingRule routingRule)
    {
        validate(routingRule);
        return writeAndRefresh(() -> store.createRoutingRule(routingRule));
    }

    public List<RoutingRule> deleteRoutingRule(String name)
    {
        validateName(name);
        return writeAndRefresh(() -> store.deleteRoutingRule(name));
    }

    private List<RoutingRule> writeAndRefresh(Supplier<List<RoutingRule>> write)
    {
        List<RoutingRule> result;
        try {
            result = write.get();
        }
        catch (RoutingRulesReloadException e) {
            // The change was saved, so this pod still needs to pick it up
            triggerRefresh();
            throw e;
        }
        triggerRefresh();
        return result;
    }

    private void triggerRefresh()
    {
        // Waiting clears evaluation errors from the previous version of the rule before the UI reads the status again
        try {
            rulesEngine.triggerRefresh().get(REFRESH_WAIT_SECONDS, SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException | TimeoutException e) {
            // The change is saved; the scheduled refresh will pick it up
            log.warn(e, "Routing rules were saved, but the immediate reload did not complete within %s seconds", REFRESH_WAIT_SECONDS);
        }
    }

    private static void validate(RoutingRule routingRule)
    {
        if (routingRule == null) {
            throw new IllegalArgumentException("Routing rule is required");
        }
        validateName(routingRule.name());
        MVELRoutingRule.fromRoutingRule(routingRule);
    }

    private static void validateName(String name)
    {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Routing rule name is required");
        }
        // Oracle's VARCHAR2(256) counts bytes, so limit the encoded length rather than the character count
        if (name.getBytes(UTF_8).length > MAX_NAME_BYTES) {
            throw new IllegalArgumentException("Routing rule name is too long: maximum is %s bytes".formatted(MAX_NAME_BYTES));
        }
    }
}
