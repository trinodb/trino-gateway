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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.domain.RoutingRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

class TestRoutingRulesManager
{
    private RoutingRulesConfiguration routingRulesConfiguration;

    private ObjectMapper yamlReader;

    private RoutingRulesManager routingRulesManager;

    @BeforeEach
    void setUp()
    {
        routingRulesConfiguration = new RoutingRulesConfiguration();
        routingRulesManager = new RoutingRulesManager();
        yamlReader = new ObjectMapper(new YAMLFactory());
    }

    @Test
    void testGetRoutingRules()
            throws IOException
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_atomic.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);

        List<RoutingRules> result = routingRulesManager.getRoutingRules(routingRulesConfiguration, yamlReader);

        assertThat(2).isEqualTo(result.size());
        assertThat("airflow").isEqualTo(result.get(0).name());
        assertThat("if query from airflow, route to etl group").isEqualTo(result.get(0).description());
        assertThat("request.getHeader(\"X-Trino-Source\") == \"airflow\" && (request.getHeader(\"X-Trino-Client-Tags\") == null || request.getHeader(\"X-Trino-Client-Tags\").isEmpty())").isEqualTo(result.get(0).condition());
        assertThat("result.put(\"routingGroup\", \"etl\")").isEqualTo(result.get(0).actions().get(0));
        assertThat(1).isEqualTo(result.get(0).actions().size());
        assertThat("airflow special").isEqualTo(result.get(1).name());
    }

    @Test
    void testRoutingRulesNoSuchFileException()
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_test.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);

        assertThatException().isThrownBy(() -> {
            routingRulesManager.getRoutingRules(routingRulesConfiguration, yamlReader);
        }).withCauseExactlyInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testUpdateRoutingRulesFile()
            throws IOException
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_update.yml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        RoutingRules routingRules = new RoutingRules("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        List<RoutingRules> updatedRoutingRules = routingRulesManager.updateRoutingRules(routingRules, routingRulesConfiguration, yamlReader);
        assertThat("result.put(\"routingGroup\", \"adhoc\")").isEqualTo(updatedRoutingRules.get(0).actions().get(0));
        assertThat("request.getHeader(\"X-Trino-Source\") == \"JDBC\"").isEqualTo(updatedRoutingRules.get(0).condition());

        RoutingRules originalRoutingRules = new RoutingRules("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"etl\")"), "request.getHeader(\"X-Trino-Source\") == \"airflow\"");
        routingRulesManager.updateRoutingRules(originalRoutingRules, routingRulesConfiguration, yamlReader);
    }

    @Test
    void testUpdateRoutingRulesNoSuchFileException()
    {
        String rulesConfigPath = "src/test/resources/rules/routing_rules_updated.yaml";
        routingRulesConfiguration.setRulesConfigPath(rulesConfigPath);
        RoutingRules routingRules = new RoutingRules("airflow", "if query from airflow, route to etl group", 0, List.of("result.put(\"routingGroup\", \"adhoc\")"), "request.getHeader(\"X-Trino-Source\") == \"JDBC\"");

        assertThatException().isThrownBy(() -> {
            routingRulesManager.updateRoutingRules(routingRules, routingRulesConfiguration, yamlReader);
        }).withCauseExactlyInstanceOf(NoSuchFileException.class);
    }
}
