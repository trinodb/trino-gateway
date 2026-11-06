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

import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.util.QueryRequestMock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestRulesEngineRoutingGroupSelector
{
    private static final Duration ONE_HOUR = new Duration(1, HOURS);

    private final RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();

    @Test
    void testMatchingRule()
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("etl");
            assertThat(route(selector, "jupyter")).isNull();
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRulesFireInStoreOrder()
    {
        // The store returns rules in evaluation order, so the last matching rule wins
        TestingStore store = new TestingStore(List.of(
                rule("low", 1, "low-group", "airflow"),
                rule("high", 100, "high-group", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("high-group");
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRuleChangesPickedUpAfterRefresh()
            throws ExecutionException, InterruptedException
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("etl");

            store.rules = List.of(rule("airflow", 0, "etl2", "airflow"));
            selector.triggerRefresh().get();

            assertThat(route(selector, "airflow")).isEqualTo("etl2");
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testInvalidRuleIsSkipped()
    {
        TestingStore store = new TestingStore(List.of(
                new RoutingRule("broken", "", 0, List.of("result.put(\"routingGroup\", \"broken\")"), "request.getHeader("),
                rule("airflow", 1, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("etl");
            assertThat(selector.getLoadedRulesCount()).isEqualTo(1);
            assertThat(selector.getSkippedRulesCount()).isEqualTo(1);
            assertThat(selector.getRefreshFailures()).isZero();
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRuleWithoutActionsIsSkippedAndCounted()
            throws ExecutionException, InterruptedException
    {
        // The database store returns no actions for a row whose actions JSON cannot be parsed
        TestingStore store = new TestingStore(List.of(
                rule("airflow", 0, "etl", "airflow"),
                new RoutingRule("bad-json", "", 1, List.of(), "true")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("etl");
            assertThat(selector.getSkippedRulesCount()).isEqualTo(1);
            assertThat(selector.getRefreshFailures()).isZero();

            store.rules = List.of(rule("airflow", 0, "etl", "airflow"));
            selector.triggerRefresh().get();
            assertThat(selector.getSkippedRulesCount()).isZero();
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRuleThatThrowsFailsTheRequest()
    {
        TestingStore store = new TestingStore(List.of(
                rule("airflow", 0, "etl", "airflow"),
                new RoutingRule("blocked", "", 1, List.of("Integer.parseInt(\"BLOCKED: too many tables\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            // Rules throw on purpose to reject a query, so the failure must reach the client
            assertThatThrownBy(() -> route(selector, "airflow")).hasStackTraceContaining("BLOCKED: too many tables");
            assertThatThrownBy(() -> route(selector, "airflow")).hasStackTraceContaining("BLOCKED: too many tables");
            assertThat(route(selector, "jupyter")).isNull();

            assertThat(selector.getRuleEvaluationFailures()).isEqualTo(2);
            // Only the first failure of a rule is kept
            assertThat(selector.getRuleEvaluationErrors()).containsOnlyKeys("blocked");
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRuleEvaluationErrorsClearedOnlyForChangedRules()
            throws ExecutionException, InterruptedException
    {
        RoutingRule failsForAirflow = new RoutingRule("fails-for-airflow", "", 0, List.of("result.put(\"routingGroup\", request.getHeader(\"X-Missing\").length())"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        RoutingRule failsForJupyter = new RoutingRule("fails-for-jupyter", "", 0, List.of("result.put(\"routingGroup\", request.getHeader(\"X-Missing\").length())"), "request.getHeader(\"X-Trino-Source\") == \"jupyter\"");
        TestingStore store = new TestingStore(List.of(failsForAirflow, failsForJupyter));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThatThrownBy(() -> route(selector, "airflow")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> route(selector, "jupyter")).isInstanceOf(RuntimeException.class);
            assertThat(selector.getRuleEvaluationErrors()).containsOnlyKeys("fails-for-airflow", "fails-for-jupyter");

            // Adding an unrelated rule keeps the errors of rules that still fail
            store.rules = List.of(failsForAirflow, failsForJupyter, rule("unrelated", 1, "adhoc", "dbt"));
            selector.triggerRefresh().get();
            assertThat(selector.getRuleEvaluationErrors()).containsOnlyKeys("fails-for-airflow", "fails-for-jupyter");

            // Fixing one rule clears only its error
            store.rules = List.of(rule("fails-for-airflow", 0, "etl", "airflow"), failsForJupyter, rule("unrelated", 1, "adhoc", "dbt"));
            selector.triggerRefresh().get();
            assertThat(selector.getRuleEvaluationErrors()).containsOnlyKeys("fails-for-jupyter");

            // Removing a rule clears its error
            store.rules = List.of(rule("fails-for-airflow", 0, "etl", "airflow"), rule("unrelated", 1, "adhoc", "dbt"));
            selector.triggerRefresh().get();
            assertThat(selector.getRuleEvaluationErrors()).isEmpty();
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testFixedRuleDoesNotKeepErrorsFromRequestsOnTheOldVersion()
            throws ExecutionException, InterruptedException
    {
        RoutingRule failing = new RoutingRule("flaky", "", 0, List.of("result.put(\"routingGroup\", request.getHeader(\"X-Missing\").length())"), "true");
        RoutingRule fixed = rule("flaky", 0, "etl", "airflow");
        TestingStore store = new TestingStore(List.of(failing));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        ExecutorService executor = newFixedThreadPool(8);
        try {
            selector.start();
            for (int attempt = 0; attempt < 20; attempt++) {
                store.rules = List.of(failing);
                selector.triggerRefresh().get();
                long failuresBefore = selector.getRuleEvaluationFailures();
                AtomicBoolean stop = new AtomicBoolean();
                List<Future<?>> routers = new ArrayList<>();
                for (int thread = 0; thread < 8; thread++) {
                    routers.add(executor.submit(() -> {
                        while (!stop.get()) {
                            try {
                                route(selector, "airflow");
                            }
                            catch (RuntimeException _) {
                                // Expected while the rule fails
                            }
                        }
                    }));
                }
                while (selector.getRuleEvaluationFailures() == failuresBefore) {
                    Thread.onSpinWait();
                }

                // Fix the rule while requests are still evaluating the failing version
                store.rules = List.of(fixed);
                selector.triggerRefresh().get();
                stop.set(true);
                for (Future<?> router : routers) {
                    router.get();
                }

                assertThat(selector.getRuleEvaluationErrors()).as("errors after fix #%s", attempt).isEmpty();
            }
        }
        finally {
            executor.shutdownNow();
            selector.stop();
        }
    }

    @Test
    void testStoreFailureKeepsPreviousRules()
            throws ExecutionException, InterruptedException
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "airflow")).isEqualTo("etl");

            store.failure = new RuntimeException("database unavailable");
            selector.triggerRefresh().get();

            // Previous rules still in effect
            assertThat(route(selector, "airflow")).isEqualTo("etl");
            assertThat(selector.getRefreshFailures()).isEqualTo(1);
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testStoreFailureAtStartupRoutesByHeaderUntilLoadSucceeds()
            throws ExecutionException, InterruptedException
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        store.failure = new RuntimeException("database unavailable");
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();

            // Routes by header when no rules loaded
            assertThat(route(selector, "airflow", "header-group")).isEqualTo("header-group");
            assertThat(route(selector, "airflow")).isNull();

            store.failure = null;
            selector.triggerRefresh().get();

            // Now routes by rules
            assertThat(route(selector, "airflow", "header-group")).isEqualTo("etl");
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testStartTwiceLoadsOnce()
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            // Guice calls the @PostConstruct start() after the factory method already did
            selector.start();
            selector.start();

            assertThat(store.loadCount.get()).isEqualTo(1);
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testUnchangedRulesKeepCompiledInstance()
            throws ExecutionException, InterruptedException
    {
        TestingStore store = new TestingStore(List.of(rule("airflow", 0, "etl", "airflow")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            List<io.trino.gateway.ha.router.RoutingRule> initial = selector.getCurrentRules().orElseThrow();

            // Equal by value but a different list instance, as the database returns on every read
            store.rules = List.of(rule("airflow", 0, "etl", "airflow"));
            selector.triggerRefresh().get();
            assertThat(selector.getCurrentRules().orElseThrow()).isSameAs(initial);

            store.rules = List.of(rule("airflow", 0, "etl2", "airflow"));
            selector.triggerRefresh().get();
            assertThat(selector.getCurrentRules().orElseThrow()).isNotSameAs(initial);
            assertThat(route(selector, "airflow")).isEqualTo("etl2");
        }
        finally {
            selector.stop();
        }
    }

    @Test
    void testRoutingGroupKeyResolvesThroughBothSelectorClasses()
    {
        TestingStore store = new TestingStore(List.of(
                new RoutingRule("file-key", "", 0, List.of("result.put(FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"file-group\")"), "request.getHeader(\"X-Trino-Source\") == \"file\""),
                new RoutingRule("base-key", "", 0, List.of("result.put(RulesEngineRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"base-group\")"), "request.getHeader(\"X-Trino-Source\") == \"base\"")));
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(store, ONE_HOUR, requestAnalyzerConfig);
        try {
            selector.start();
            assertThat(route(selector, "file")).isEqualTo("file-group");
            assertThat(route(selector, "base")).isEqualTo("base-group");
        }
        finally {
            selector.stop();
        }
    }

    private String route(RoutingGroupSelector selector, String source)
    {
        return route(selector, source, null);
    }

    private String route(RoutingGroupSelector selector, String source, String routingGroupHeader)
    {
        QueryRequestMock requestMock = new QueryRequestMock()
                .httpHeader("X-Trino-Source", source)
                .requestAnalyzerConfig(requestAnalyzerConfig);
        if (routingGroupHeader != null) {
            requestMock.httpHeader(RoutingGroupSelector.ROUTING_GROUP_HEADER, routingGroupHeader);
        }
        HttpServletRequest request = requestMock.getHttpServletRequest();
        return selector.findRoutingDestination(request).routingGroup();
    }

    private static RoutingRule rule(String name, int priority, String routingGroup, String source)
    {
        return new RoutingRule(
                name,
                "",
                priority,
                List.of("result.put(\"routingGroup\", \"%s\")".formatted(routingGroup)),
                "request.getHeader(\"X-Trino-Source\") == \"%s\"".formatted(source));
    }

    private static class TestingStore
            implements RoutingRulesStore
    {
        private volatile List<RoutingRule> rules;
        private volatile RuntimeException failure;
        private final AtomicInteger loadCount = new AtomicInteger();

        TestingStore(List<RoutingRule> rules)
        {
            this.rules = rules;
        }

        @Override
        public List<RoutingRule> getRoutingRules()
        {
            loadCount.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return rules;
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
