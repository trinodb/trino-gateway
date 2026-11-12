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
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.router.RoutingRulesLoader.LoadedRoutingRules;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.weakref.jmx.Managed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Evaluates MVEL routing rules read from a {@link RoutingRulesStore}. FILE and DATABASE rules
 * share this class, so they load, compile, and skip invalid rules the same way. Rules are
 * reloaded in the background every {@code rulesRefreshPeriod}. A rule that is not valid is
 * logged and skipped, and the others still apply. If a reload fails, the previously loaded
 * rules stay in use. Until the first successful load, requests are routed by the routing
 * group header. Rules are evaluated by ascending priority; equal priorities keep the store's order.
 */
public class RulesEngineRoutingGroupSelector
        implements RoutingGroupSelector, RoutingRulesEngine
{
    public static final String RESULTS_ROUTING_GROUP_KEY = "routingGroup";

    private static final Logger log = Logger.get(RulesEngineRoutingGroupSelector.class);

    private final RoutingRulesLoader rulesLoader;
    private final long refreshPeriodMillis;
    private final boolean analyzeRequest;
    private final ScheduledExecutorService refreshExecutor;
    private final AtomicReference<Optional<List<RoutingRule>>> rules = new AtomicReference<>(Optional.empty());
    private final RuleEvaluationErrors ruleEvaluationErrors = new RuleEvaluationErrors();
    private final AtomicLong refreshFailures = new AtomicLong();
    private final AtomicLong ruleEvaluationFailures = new AtomicLong();
    private final AtomicInteger skippedRulesCount = new AtomicInteger();
    private final AtomicLong lastSuccessfulRefreshMillis = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean();

    public RulesEngineRoutingGroupSelector(
            RoutingRulesStore store,
            Duration rulesRefreshPeriod,
            RequestAnalyzerConfig requestAnalyzerConfig)
    {
        this.rulesLoader = new RoutingRulesLoader(requireNonNull(store, "store is null"));
        this.refreshPeriodMillis = rulesRefreshPeriod.toMillis();
        this.analyzeRequest = requestAnalyzerConfig.isAnalyzeRequest();
        this.refreshExecutor = newSingleThreadScheduledExecutor(daemonThreadsNamed("routing-rules-refresh"));
    }

    @PostConstruct
    public void start()
    {
        if (started.compareAndSet(false, true)) {
            refresh();
            refreshExecutor.scheduleWithFixedDelay(this::refresh, refreshPeriodMillis, refreshPeriodMillis, MILLISECONDS);
        }
    }

    @PreDestroy
    public void stop()
    {
        refreshExecutor.shutdownNow();
    }

    @Override
    public Future<?> triggerRefresh()
    {
        return refreshExecutor.submit(this::refresh);
    }

    @Override
    public Map<String, String> getRuleEvaluationErrors()
    {
        return ruleEvaluationErrors.snapshot();
    }

    @Override
    public RoutingSelectorResponse findRoutingDestination(HttpServletRequest request)
    {
        Optional<List<RoutingRule>> currentRules = rules.get();
        if (currentRules.isEmpty()) {
            return new RoutingSelectorResponse(request.getHeader(ROUTING_GROUP_HEADER));
        }

        Map<String, String> result = new HashMap<>();
        Map<String, Object> state = new HashMap<>();

        Map<String, Object> data;
        if (analyzeRequest) {
            TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
            TrinoRequestUser trinoRequestUser = (TrinoRequestUser) request.getAttribute(TRINO_REQUEST_USER);
            data = ImmutableMap.of("request", request, TRINO_QUERY_PROPERTIES, trinoQueryProperties, TRINO_REQUEST_USER, trinoRequestUser);
        }
        else {
            data = ImmutableMap.of("request", request);
        }

        for (RoutingRule rule : currentRules.get()) {
            try {
                if (rule.evaluateCondition(data, state)) {
                    log.debug(
                            "%s evaluated to true on request: %s",
                            rule,
                            OAuth2RoutingUtils.redactForLog(request.getRequestURI(), request.getQueryString()));
                    rule.evaluateAction(result, data, state);
                }
            }
            catch (RuntimeException e) {
                ruleEvaluationFailures.incrementAndGet();
                String error = requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
                // Logging every failure would write a stack trace per query on the request threads
                if (ruleEvaluationErrors.record(rule.getName(), error, () -> isCurrent(currentRules.get()))) {
                    log.warn(e, "Routing rule '%s' failed to evaluate. Further failures of this rule are logged at DEBUG until the rule changes", rule.getName());
                }
                else {
                    log.debug(e, "Routing rule '%s' failed to evaluate", rule.getName());
                }
                // Rules rely on throwing to reject a query, so the request must still fail
                throw e;
            }
        }
        return new RoutingSelectorResponse(result.get(RESULTS_ROUTING_GROUP_KEY));
    }

    private boolean isCurrent(List<RoutingRule> evaluatedRules)
    {
        return rules.get().orElse(null) == evaluatedRules;
    }

    private void refresh()
    {
        try {
            LoadedRoutingRules loadedRules = rulesLoader.load();
            List<RoutingRule> loaded = loadedRules.rules();
            skippedRulesCount.set(loadedRules.skippedRulesCount());
            Optional<List<RoutingRule>> previous = rules.get();
            if (previous.isPresent() && previous.get().equals(loaded)) {
                // Rules unchanged, no need to update
                lastSuccessfulRefreshMillis.set(System.currentTimeMillis());
                return;
            }
            rules.set(Optional.of(loaded));
            ruleEvaluationErrors.clear(loadedRules.changedOrRemovedRuleNames());
            lastSuccessfulRefreshMillis.set(System.currentTimeMillis());
            if (previous.isEmpty()) {
                log.info("Loaded %d routing rules", loaded.size());
            }
            else {
                log.info("Reloaded %d routing rules", loaded.size());
            }
        }
        catch (Throwable e) {
            // Catch Throwable to prevent an Error from canceling future scheduled runs
            refreshFailures.incrementAndGet();
            if (rules.get().isEmpty()) {
                log.error(e, "Failed to load routing rules, routing by the %s header until a refresh succeeds", ROUTING_GROUP_HEADER);
            }
            else {
                log.warn(e, "Failed to refresh routing rules (failure #%d), keeping the previously loaded rules", refreshFailures.get());
            }
        }
    }

    @Managed
    public long getRefreshFailures()
    {
        return refreshFailures.get();
    }

    @Managed
    public long getRuleEvaluationFailures()
    {
        return ruleEvaluationFailures.get();
    }

    @Managed
    public long getSecondsSinceLastSuccessfulRefresh()
    {
        long lastRefresh = lastSuccessfulRefreshMillis.get();
        if (lastRefresh == 0) {
            return -1; // No successful refresh yet
        }
        return (System.currentTimeMillis() - lastRefresh) / 1000;
    }

    @Managed
    public int getLoadedRulesCount()
    {
        return rules.get().map(List::size).orElse(0);
    }

    @Managed
    public int getSkippedRulesCount()
    {
        return skippedRulesCount.get();
    }

    // For testing
    Optional<List<RoutingRule>> getCurrentRules()
    {
        return rules.get();
    }
}
