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

import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestRoutingRulesManager
{
    @Test
    void testGetRoutingRules()
            throws IOException
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_atomic.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        configuration.setRoutingRules(routingRulesConfiguration);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(configuration);

        List<RoutingRule> result = routingRulesManager.getRoutingRules();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo(
                new RoutingRule(
                        "airflow",
                        "if query from airflow, route to etl group",
                        null,
                        List.of("result.put(FileBasedRoutingSelector.RESULTS_ROUTING_GROUP_KEY, \"etl\")"),
                        "request.getHeader(\"X-Trino-Source\") == \"airflow\" && (request.getHeader(\"X-Trino-Client-Tags\") == null || request.getHeader(\"X-Trino-Client-Tags\").isEmpty())"));
        assertThat(result.get(1)).isEqualTo(
                new RoutingRule(
                        "airflow special",
                        "if query from airflow with special label, route to etl-special group",
                        null,
                        List.of("result.put(FileBasedRoutingSelector.RESULTS_ROUTING_GROUP_KEY, \"etl-special\")"),
                        "request.getHeader(\"X-Trino-Source\") == \"airflow\" && request.getHeader(\"X-Trino-Client-Tags\") contains \"label=special\""));
    }

    @Test
    void testRoutingRulesNoSuchFileException()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_test.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        configuration.setRoutingRules(routingRulesConfiguration);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(configuration);

        assertThatThrownBy(routingRulesManager::getRoutingRules).hasRootCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testUpdateRoutingRulesFile()
            throws IOException
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_update.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        configuration.setRoutingRules(routingRulesConfiguration);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(configuration);

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
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_updated.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        configuration.setRoutingRules(routingRulesConfiguration);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(configuration);
        RoutingRule routingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        assertThatThrownBy(() -> routingRulesManager.updateRoutingRule(routingRules)).hasRootCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testConcurrentUpdateRoutingRule()
            throws IOException
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_concurrent.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        configuration.setRoutingRules(routingRulesConfiguration);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager(configuration);

        RoutingRule routingRule1 = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        RoutingRule routingRule2 = new RoutingRule("airflow", "if query from airflow, route to adhoc group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"datagrip\"");

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        executorService.submit(() ->
        {
            try {
                routingRulesManager.updateRoutingRule(routingRule1);
            }
            catch (UncheckedIOException e) {
                throw new RuntimeException(e);
            }
        });

        executorService.submit(() ->
        {
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
