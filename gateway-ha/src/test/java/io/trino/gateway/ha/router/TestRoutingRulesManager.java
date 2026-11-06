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
import io.trino.gateway.ha.domain.response.RoutingRuleStatus;
import io.trino.gateway.ha.util.QueryRequestMock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestRoutingRulesManager
{
    private static final RoutingRulesEngine NO_OP_ENGINE = RoutingRulesEngine.NO_OP;

    @Test
    void testGetRoutingRules()
            throws IOException
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_atomic.yml";
        RoutingRulesStore store = new FileRoutingRulesStore(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);

        List<RoutingRule> result = routingRulesManager.getRoutingRules();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo(
                new RoutingRule(
                        "airflow",
                        "if query from airflow, route to etl group",
                        null,
                        List.of("result.put(FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"etl\")"),
                        "request.getHeader(\"X-Trino-Source\") == \"airflow\" && (request.getHeader(\"X-Trino-Client-Tags\") == null || request.getHeader(\"X-Trino-Client-Tags\").isEmpty())"));
        assertThat(result.get(1)).isEqualTo(
                new RoutingRule(
                        "airflow special",
                        "if query from airflow with special label, route to etl-special group",
                        null,
                        List.of("result.put(FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"etl-special\")"),
                        "request.getHeader(\"X-Trino-Source\") == \"airflow\" && request.getHeader(\"X-Trino-Client-Tags\") contains \"label=special\""));
    }

    @Test
    void testRoutingRulesNoSuchFileException()
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_test.yaml";
        RoutingRulesStore store = new FileRoutingRulesStore(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);

        assertThatThrownBy(routingRulesManager::getRoutingRules).hasRootCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testUpdateRoutingRulesFile()
            throws IOException
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_update.yml";
        RoutingRulesStore store = new FileRoutingRulesStore(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);

        RoutingRule routingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        List<RoutingRule> updatedRoutingRules = routingRulesManager.updateRoutingRule(routingRules);
        assertThat(updatedRoutingRules.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"adhoc\")");
        assertThat(updatedRoutingRules.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        RoutingRule originalRoutingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        List<RoutingRule> updateRoutingRules = routingRulesManager.updateRoutingRule(originalRoutingRules);

        assertThat(updateRoutingRules).hasSize(2);
        assertThat(updateRoutingRules.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"etl\")");
        assertThat(updateRoutingRules.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"airflow\"");
    }

    @Test
    void testUpdateRoutingRulesNoSuchFileException()
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_updated.yaml";
        RoutingRulesStore store = new FileRoutingRulesStore(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);
        RoutingRule routingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        assertThatThrownBy(() -> routingRulesManager.updateRoutingRule(routingRules)).hasRootCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testUpdateRoutingRuleWithInvalidMvelRejected()
    {
        RoutingRulesStore store = new FileRoutingRulesStore("src/test/resources/rules/routing_rules_update.yml");
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);
        RoutingRule invalidRule = new RoutingRule("airflow", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(");

        assertThatThrownBy(() -> routingRulesManager.updateRoutingRule(invalidRule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MVEL in routing rule 'airflow'");
        assertThat(routingRulesManager.getRoutingRules().getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"airflow\"");
    }

    @Test
    void testUpdateUnknownRoutingRuleRejected()
    {
        RoutingRulesStore store = new FileRoutingRulesStore("src/test/resources/rules/routing_rules_update.yml");
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);
        RoutingRule unknownRule = new RoutingRule("does-not-exist", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "true");

        assertThatThrownBy(() -> routingRulesManager.updateRoutingRule(unknownRule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist");
        assertThat(routingRulesManager.getRoutingRules()).hasSize(2);
    }

    @Test
    void testCreateAndDeleteRoutingRuleFile()
            throws IOException
    {
        Path rulesFile = Files.createTempFile("routing_rules", ".yml");
        Files.copy(Path.of("src/test/resources/rules/routing_rules_update.yml"), rulesFile, REPLACE_EXISTING);
        try {
            RoutingRulesManager routingRulesManager = new RoutingRulesManager(new FileRoutingRulesStore(rulesFile.toString()), NO_OP_ENGINE);
            RoutingRule newRule = new RoutingRule("jupyter", "route jupyter to adhoc", 2, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"jupyter\"");

            assertThat(routingRulesManager.createRoutingRule(newRule)).extracting(RoutingRule::name)
                    .containsExactly("airflow", "airflow special", "jupyter");
            assertThat(routingRulesManager.getRoutingRules()).contains(newRule);
            assertThatThrownBy(() -> routingRulesManager.createRoutingRule(newRule))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            assertThat(routingRulesManager.deleteRoutingRule("airflow")).extracting(RoutingRule::name)
                    .containsExactly("airflow special", "jupyter");
            assertThat(routingRulesManager.getRoutingRules()).extracting(RoutingRule::name)
                    .containsExactly("airflow special", "jupyter");
            assertThatThrownBy(() -> routingRulesManager.deleteRoutingRule("airflow"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
        finally {
            Files.delete(rulesFile);
        }
    }

    @Test
    void testModificationsTriggerSelectorRefresh()
            throws IOException
    {
        Path rulesFile = Files.createTempFile("routing_rules", ".yml");
        Files.copy(Path.of("src/test/resources/rules/routing_rules_update.yml"), rulesFile, REPLACE_EXISTING);
        try {
            RoutingRulesEngine selector = mock(RoutingRulesEngine.class);
            doReturn(completedFuture(null)).when(selector).triggerRefresh();
            RoutingRulesManager routingRulesManager = new RoutingRulesManager(new FileRoutingRulesStore(rulesFile.toString()), selector);
            RoutingRule newRule = new RoutingRule("jupyter", "route jupyter to adhoc", 2, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"jupyter\"");

            routingRulesManager.createRoutingRule(newRule);
            verify(selector, times(1)).triggerRefresh();

            routingRulesManager.updateRoutingRule(new RoutingRule("jupyter", "route jupyter to etl", 2, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"jupyter\""));
            verify(selector, times(2)).triggerRefresh();

            routingRulesManager.deleteRoutingRule("jupyter");
            verify(selector, times(3)).triggerRefresh();

            // A rule rejected by validation is never written, so there is nothing to refresh
            RoutingRule invalidRule = new RoutingRule("broken", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(");
            assertThatThrownBy(() -> routingRulesManager.createRoutingRule(invalidRule))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(selector, times(3)).triggerRefresh();
        }
        finally {
            Files.delete(rulesFile);
        }
    }

    @Test
    void testCreateRoutingRuleWithInvalidMvelRejected()
    {
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(new FileRoutingRulesStore("src/test/resources/rules/routing_rules_update.yml"), NO_OP_ENGINE);
        RoutingRule invalidRule = new RoutingRule("broken", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(");

        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(invalidRule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MVEL in routing rule 'broken'");
        assertThat(routingRulesManager.getRoutingRules()).hasSize(2);
    }

    @Test
    void testRoutingRulesWithStatusMarkRulesThatAreNotApplied()
    {
        RoutingRule valid = new RoutingRule("valid", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "true");
        RoutingRule badMvel = new RoutingRule("bad-mvel", "", 1, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(");
        RoutingRule noActions = new RoutingRule("no-actions", "", 2, List.of(), "true");
        // What the database store returns for a row whose actions JSON cannot be parsed
        RoutingRule badJson = new RoutingRule("bad-json", "", 3, List.of(), "true");
        RoutingRulesStore store = mock(RoutingRulesStore.class);
        when(store.readRoutingRules()).thenReturn(new StoredRoutingRules(
                List.of(valid, badMvel, noActions, badJson),
                Map.of("bad-json", "actions is not a JSON array of strings")));
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);

        List<RoutingRuleStatus> statuses = routingRulesManager.getRoutingRulesWithStatus();

        assertThat(statuses).extracting(status -> status.rule().name(), RoutingRuleStatus::active)
                .containsExactly(tuple("valid", true), tuple("bad-mvel", false), tuple("no-actions", false), tuple("bad-json", false));
        assertThat(statuses.get(0).error()).isNull();
        assertThat(statuses.get(1).error()).startsWith("Invalid MVEL in routing rule 'bad-mvel'");
        assertThat(statuses.get(2).error()).isEqualTo("Routing rule 'no-actions' must have at least one action, and actions cannot be blank");
        assertThat(statuses.get(3).error()).isEqualTo("Routing rule 'bad-json' cannot be read: actions is not a JSON array of strings");
    }

    @Test
    void testRoutingRulesWithStatusKeepRulesThatFailWhenEvaluatedActive()
    {
        RoutingRule valid = new RoutingRule("valid", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "true");
        RoutingRule runtimeNpe = new RoutingRule("runtime-npe", "", 1, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Missing\").length() > 0");
        RoutingRulesStore store = mock(RoutingRulesStore.class);
        when(store.readRoutingRules()).thenReturn(new StoredRoutingRules(List.of(valid, runtimeNpe), Map.of()));
        RoutingRulesEngine selector = mock(RoutingRulesEngine.class);
        when(selector.getRuleEvaluationErrors()).thenReturn(Map.of("runtime-npe", "NullPointerException"));
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, selector);

        List<RoutingRuleStatus> statuses = routingRulesManager.getRoutingRulesWithStatus();

        assertThat(statuses).extracting(status -> status.rule().name(), RoutingRuleStatus::active, RoutingRuleStatus::error)
                .containsExactly(
                        tuple("valid", true, null),
                        tuple("runtime-npe", true, "Fails for some requests: NullPointerException"));
    }

    @Test
    void testFixedRuleHasNoErrorAsSoonAsUpdateReturns()
            throws IOException
    {
        Path rulesFile = Files.createTempFile("routing_rules", ".yml");
        Files.copy(Path.of("src/test/resources/rules/routing_rules_update.yml"), rulesFile, REPLACE_EXISTING);
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        RulesEngineRoutingGroupSelector selector = new RulesEngineRoutingGroupSelector(
                new FileRoutingRulesStore(rulesFile.toString()),
                new Duration(1, HOURS),
                requestAnalyzerConfig);
        try {
            RoutingRulesManager routingRulesManager = new RoutingRulesManager(new FileRoutingRulesStore(rulesFile.toString()), selector);
            selector.start();
            RoutingRule failing = new RoutingRule("airflow", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Missing\").length() > 0");
            routingRulesManager.updateRoutingRule(failing);
            assertThatThrownBy(() -> selector.findRoutingDestination(new QueryRequestMock().requestAnalyzerConfig(requestAnalyzerConfig).getHttpServletRequest()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(routingRulesManager.getRoutingRulesWithStatus().getFirst().error()).startsWith("Fails for some requests:");

            RoutingRule fixed = new RoutingRule("airflow", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
            routingRulesManager.updateRoutingRule(fixed);

            assertThat(routingRulesManager.getRoutingRulesWithStatus().getFirst())
                    .extracting(RoutingRuleStatus::active, RoutingRuleStatus::error)
                    .containsExactly(true, null);
        }
        finally {
            selector.stop();
            Files.delete(rulesFile);
        }
    }

    @Test
    void testSavedChangeStillRefreshesWhenReloadFails()
    {
        RoutingRule rule = new RoutingRule("new", "", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "true");
        RoutingRulesStore store = mock(RoutingRulesStore.class);
        when(store.createRoutingRule(rule)).thenThrow(new RoutingRulesReloadException("new", new RuntimeException("connection reset")));
        RoutingRulesEngine selector = mock(RoutingRulesEngine.class);
        doReturn(completedFuture(null)).when(selector).triggerRefresh();
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, selector);

        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(rule))
                .isInstanceOf(RoutingRulesReloadException.class)
                .hasMessage("Routing rule 'new' was saved, but reloading the rule list failed: connection reset");
        verify(selector, times(1)).triggerRefresh();
    }

    @Test
    void testInvalidRoutingRuleInputRejected()
    {
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(new FileRoutingRulesStore("src/test/resources/rules/routing_rules_update.yml"), NO_OP_ENGINE);
        List<String> actions = List.of("result.put(\"routingGroup\", \"etl\")");

        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule is required");
        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(new RoutingRule(" ", "", 0, actions, "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule name is required");
        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(new RoutingRule("a".repeat(257), "", 0, actions, "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule name is too long: maximum is 256 bytes");
        // 86 three-byte characters is 258 bytes, over the limit even though it is only 86 characters
        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(new RoutingRule("规".repeat(86), "", 0, actions, "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule name is too long: maximum is 256 bytes");
        assertThatThrownBy(() -> routingRulesManager.updateRoutingRule(new RoutingRule("airflow", "", 0, actions, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule 'airflow' must have a condition");
        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(new RoutingRule("no-actions", "", 0, List.of(), "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have at least one action");
        assertThatThrownBy(() -> routingRulesManager.createRoutingRule(new RoutingRule("blank-action", "", 0, List.of(" "), "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actions cannot be blank");
        assertThatThrownBy(() -> routingRulesManager.deleteRoutingRule(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule name is required");
        assertThatThrownBy(() -> routingRulesManager.deleteRoutingRule(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing rule name is required");

        assertThat(routingRulesManager.getRoutingRules()).hasSize(2);
    }

    @Test
    void testConcurrentUpdateRoutingRule()
            throws IOException
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_concurrent.yml";
        RoutingRulesStore store = new FileRoutingRulesStore(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(store, NO_OP_ENGINE);

        RoutingRule routingRule1 = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        RoutingRule routingRule2 = new RoutingRule("airflow", "if query from airflow, route to adhoc group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"datagrip\"");

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        executorService.submit(() -> {
            try {
                routingRulesManager.updateRoutingRule(routingRule1);
            }
            catch (UncheckedIOException e) {
                throw new RuntimeException(e);
            }
        });

        executorService.submit(() -> {
            try {
                routingRulesManager.updateRoutingRule(routingRule2);
            }
            catch (UncheckedIOException e) {
                throw new RuntimeException(e);
            }
        });

        executorService.shutdown();
        List<RoutingRule> updatedRoutingRules = routingRulesManager.getRoutingRules();
        assertThat(updatedRoutingRules).hasSize(1);
        assertThat(updatedRoutingRules.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"datagrip\"");
        assertThat(updatedRoutingRules.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"adhoc\")");
        assertThat(updatedRoutingRules.getFirst().description()).isEqualTo("if query from airflow, route to adhoc group");
    }
}
