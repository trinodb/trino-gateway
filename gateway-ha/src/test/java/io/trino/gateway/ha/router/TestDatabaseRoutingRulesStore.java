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

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.persistence.FlywayMigration;
import io.trino.gateway.ha.persistence.RecordAndAnnotatedConstructorMapper;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static io.trino.gateway.ha.util.TestcontainersUtils.createPostgreSqlContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestDatabaseRoutingRulesStore
{
    private final PostgreSQLContainer postgresql = createPostgreSqlContainer();
    private Jdbi jdbi;
    private DatabaseRoutingRulesStore store;

    @BeforeAll
    void setup()
    {
        postgresql.start();
        FlywayMigration.migrate(new DataStoreConfiguration(
                postgresql.getJdbcUrl(),
                postgresql.getUsername(),
                postgresql.getPassword(),
                postgresql.getDriverClassName(),
                true,
                4,
                true,
                null));

        jdbi = Jdbi.create(postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.registerRowMapper(new RecordAndAnnotatedConstructorMapper());
        store = new DatabaseRoutingRulesStore(jdbi);
    }

    @AfterAll
    void teardown()
    {
        postgresql.stop();
    }

    @BeforeEach
    void cleanupTable()
    {
        jdbi.useHandle(handle -> handle.execute("DELETE FROM routing_rules"));
    }

    @Test
    void testGetRoutingRules()
    {
        insertRule("airflow", "Route airflow queries to ETL", 10, "[\"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"]", "request.getHeader(\"X-Trino-Source\") == \"airflow\"");

        List<RoutingRule> rules = store.getRoutingRules();
        assertThat(rules).containsExactly(new RoutingRule(
                "airflow",
                "Route airflow queries to ETL",
                10,
                List.of("result.put(\"routingGroup\", \"etl\")"),
                "request.getHeader(\"X-Trino-Source\") == \"airflow\""));
    }

    @Test
    void testUpdateRoutingRule()
    {
        insertRule("airflow", "Route airflow queries to ETL", 10, "[\"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"]", "true");

        RoutingRule updatedRule = new RoutingRule(
                "airflow",
                "Route airflow queries to ADHOC",
                20,
                List.of("result.put(\"routingGroup\", \"adhoc\")", "state.put(\"logged\", true)"),
                "request.getHeader(\"X-Trino-Source\") == \"airflow-v2\"");

        assertThat(store.updateRoutingRule(updatedRule)).containsExactly(updatedRule);
        assertThat(store.getRoutingRules()).containsExactly(updatedRule);
    }

    @Test
    void testUpdateUnknownRuleFails()
    {
        RoutingRule rule = new RoutingRule("missing", "", 5, List.of("result.put(\"routingGroup\", \"new\")"), "true");

        assertThatThrownBy(() -> store.updateRoutingRule(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThat(store.getRoutingRules()).isEmpty();
    }

    @Test
    void testCreateRoutingRule()
    {
        RoutingRule rule = new RoutingRule("airflow", "Route airflow queries to ETL", 10, List.of("result.put(\"routingGroup\", \"etl\")"), "true");

        assertThat(store.createRoutingRule(rule)).containsExactly(rule);
        assertThatThrownBy(() -> store.createRoutingRule(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThat(store.getRoutingRules()).containsExactly(rule);
    }

    @Test
    void testDeleteRoutingRule()
    {
        insertRule("keep", "", 1, "[]", "true");
        insertRule("remove", "", 2, "[]", "true");

        assertThat(store.deleteRoutingRule("remove")).extracting(RoutingRule::name).containsExactly("keep");
        assertThatThrownBy(() -> store.deleteRoutingRule("remove"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testRulesReturnedInEvaluationOrder()
    {
        insertRule("high", "", 100, "[]", "true");
        insertRule("low", "", 1, "[]", "true");
        insertRule("tie-b", "", 50, "[]", "true");
        insertRule("tie-a", "", 50, "[]", "true");
        insertRule("Tie-c", "", 50, "[]", "true");

        // Ties are broken case-sensitively, regardless of the database collation
        assertThat(store.getRoutingRules())
                .extracting(RoutingRule::name)
                .containsExactly("low", "Tie-c", "tie-a", "tie-b", "high");
    }

    @Test
    void testRowWithInvalidActionsJsonHasNoActions()
    {
        insertRule("airflow", "", 1, "[\"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"]", "true");
        insertRule("bad-json", "", 2, "not json", "true");
        insertRule("not-a-list", "", 3, "{\"a\": 1}", "true");
        insertRule("null-action", "", 4, "[null]", "true");
        insertRule("json-null", "", 5, "null", "true");

        // The other rules still load; the shared validation then skips the rules without actions
        assertThat(store.getRoutingRules())
                .extracting(RoutingRule::name, RoutingRule::actions)
                .containsExactly(
                        tuple("airflow", List.of("result.put(\"routingGroup\", \"etl\")")),
                        tuple("bad-json", List.of()),
                        tuple("not-a-list", List.of()),
                        tuple("null-action", List.of()),
                        tuple("json-null", List.of()));
        assertThat(store.readRoutingRules().readErrors())
                .containsOnlyKeys("bad-json", "not-a-list", "null-action", "json-null")
                .containsValue("actions is not a JSON array of strings");

        // A bad row must not make writes fail after they are saved
        RoutingRule newRule = new RoutingRule("new", "", 6, List.of("result.put(\"routingGroup\", \"adhoc\")"), "true");
        assertThat(store.createRoutingRule(newRule))
                .extracting(RoutingRule::name)
                .containsExactly("airflow", "bad-json", "not-a-list", "null-action", "json-null", "new");

        // Replacing the actions clears the read error
        store.updateRoutingRule(new RoutingRule("bad-json", "", 2, List.of("result.put(\"routingGroup\", \"etl\")"), "true"));
        assertThat(store.readRoutingRules().readErrors()).containsOnlyKeys("not-a-list", "null-action", "json-null");
    }

    private void insertRule(String name, String description, int priority, String actionsJson, String condition)
    {
        jdbi.useHandle(handle -> handle.createUpdate(
                        "INSERT INTO routing_rules (name, description, priority, actions, rule_condition) VALUES (:name, :description, :priority, :actions, :condition)")
                .bind("name", name)
                .bind("description", description)
                .bind("priority", priority)
                .bind("actions", actionsJson)
                .bind("condition", condition)
                .execute());
    }
}
