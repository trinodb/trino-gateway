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

import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

class TestRoutingRulesManager
{
    @Test
    void testGetRoutingRules()
            throws IOException
    {
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_atomic.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        RoutingRulesManager routingRulesManager = new RoutingRulesManager();

        List<RoutingRule> result = routingRulesManager.getRoutingRules(routingRulesConfiguration);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getFirst().name()).isEqualTo("airflow");
        assertThat(result.getFirst().description()).isEqualTo("if query from airflow, route to etl group");
        assertThat(result.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"airflow\" && (request.getHeader(\"X-Trino-Client-Tags\") == null || request.getHeader(\"X-Trino-Client-Tags\").isEmpty())");
        assertThat(result.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"etl\")");
        assertThat(result.getFirst().actions().size()).isEqualTo(1);
        assertThat(result.get(1).name()).isEqualTo("airflow special");
    }

    @Test
    void testRoutingRulesNoSuchFileException()
    {
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        RoutingRulesManager routingRulesManager = new RoutingRulesManager();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_test.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);

        assertThatException()
                .isThrownBy(() -> routingRulesManager.getRoutingRules(routingRulesConfiguration))
                .withRootCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testUpdateRoutingRulesFile()
            throws IOException
    {
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        RoutingRulesManager routingRulesManager = new RoutingRulesManager();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_update.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        RoutingRule routingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        List<RoutingRule> updatedRoutingRules = routingRulesManager.updateRoutingRules(routingRules, routingRulesConfiguration);
        assertThat(updatedRoutingRules.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"adhoc\")");
        assertThat(updatedRoutingRules.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        RoutingRule originalRoutingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        List<RoutingRule> updateRoutingRules = routingRulesManager.updateRoutingRules(originalRoutingRules, routingRulesConfiguration);

        assertThat(updateRoutingRules.getFirst().actions().getFirst()).isEqualTo("result.put(\"routingGroup\", \"etl\")");
        assertThat(updateRoutingRules.getFirst().condition()).isEqualTo("request.getHeader(\"X-Trino-Source\") == \"airflow\"");
    }

    @Test
    void testUpdateRoutingRulesNoSuchFileException()
    {
        RoutingRulesConfiguration routingRulesConfiguration = new RoutingRulesConfiguration();
        RoutingRulesManager routingRulesManager = new RoutingRulesManager();
        String rulesConfigPath = "src/test/resources/rules/routing_rules_updated.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        RoutingRule routingRules = new RoutingRule("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        assertThatException()
                .isThrownBy(() -> routingRulesManager.updateRoutingRules(routingRules, routingRulesConfiguration))
                .withRootCauseInstanceOf(NoSuchFileException.class);
    }
}
