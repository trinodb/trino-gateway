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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestMvelExpressionValidator
{
    @Test
    void testAllowsBenignExpressions()
    {
        List<String> benign = List.of(
                "request.getHeader(\"X-Trino-Source\") == \"airflow\"",
                "request.getHeader(\"X-Trino-Source\") == \"airflow\" && request.getHeader(\"X-Trino-Client-Tags\") contains \"label=special\"",
                "result.put(\"routingGroup\", \"etl\")",
                "result.put(FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"etl\")",
                "state.put(\"triggeredRules\", new HashSet())",
                "state.get(\"triggeredRules\").contains(\"airflow\")",
                "trinoQueryProperties.getTables().size() > 0",
                "trinoRequestUser.userExistsAndEquals(\"alice\")",
                "request.getHeader(\"X-Trino-Source\").toLowerCase().startsWith(\"airflow\")",
                "Math.max(1, 2) == 2",
                // qualified names of the imported classes are allowed
                "trinoQueryProperties.getDefaultCatalog().equals(java.util.Optional.of(\"other_catalog\"))",
                "state.put(\"triggeredRules\", new java.util.HashSet())",
                "java.lang.Math.max(1, 2) == 2",
                "result.put(io.trino.gateway.ha.router.FileBasedRoutingGroupSelector.RESULTS_ROUTING_GROUP_KEY, \"etl\")",
                // a qualified class name appearing only as inert literal data must not be rejected
                "request.getHeader(\"X-Trino-Source\") == \"java.io.File\"",
                // a forbidden word appearing only as inert literal data must not be rejected
                "request.getHeader(\"X-Trino-Source\") == \"getClass\"",
                "result.put(\"routingGroup\", \"class\")");

        for (String expression : benign) {
            assertThatCode(() -> MvelExpressionValidator.validate("rule", expression))
                    .as("benign expression: %s", expression)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void testRejectsReflectiveAndRuntimeExpressions()
    {
        List<String> forbidden = List.of(
                // the run-6 reflection pivot and its building blocks
                "\"\".getClass().forName(\"java.lang.Runtime\").getMethod(\"getRuntime\").invoke(null).exec(\"id\") != null",
                "\"\".getClass()",
                "request.class.getName() != null",
                "request[\"class\"].getName() != null",
                "Runtime.getRuntime()",
                "System.exit(0) == null",
                "new ProcessBuilder(\"id\").start() != null",
                "Class.forName(\"java.lang.Runtime\")",
                "something.getDeclaredField(\"x\").setAccessible(true)",
                "x.getClass().newInstance()",
                "Thread.currentThread().getName() != null",
                "Thread.sleep(1000)");

        for (String expression : forbidden) {
            assertThatThrownBy(() -> MvelExpressionValidator.validate("rule", expression))
                    .as("forbidden expression: %s", expression)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("forbidden expression construct");
        }
    }

    @Test
    void testRejectsQualifiedClassNamesAndImports()
    {
        List<String> forbidden = List.of(
                "java.nio.file.Files.readString(java.nio.file.Path.of(\"/etc/hosts\")) != null",
                "new java.io.File(\"/tmp/x\").exists()",
                "new java.util.Scanner(new java.io.FileInputStream(\"/etc/hosts\")).nextLine() != null",
                "new javax.naming.InitialContext() != null",
                "java.lang.ProcessHandle.current().pid() > 0",
                "java . io . File",
                "java.util.concurrent.Executors.newSingleThreadExecutor() != null",
                "java.lang.ProcessBuilder",
                "import java.io.*; new File(\"/tmp/x\").exists()",
                "import_static java.lang.Math.max; max(1, 2) == 2");

        for (String expression : forbidden) {
            assertThatThrownBy(() -> MvelExpressionValidator.validate("rule", expression))
                    .as("forbidden expression: %s", expression)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("forbidden expression construct");
        }
    }

    @Test
    void testMvelRoutingRuleRejectsQualifiedClassNameInCondition()
    {
        assertThatThrownBy(() -> new MVELRoutingRule(
                "evil",
                "",
                0,
                "java.nio.file.Files.readString(java.nio.file.Path.of(\"/etc/hosts\")) != null",
                List.of("result.put(\"routingGroup\", \"etl\")")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden expression construct 'java.nio.file.Files'");
    }

    @Test
    void testMvelRoutingRuleRejectsQualifiedClassNameInAction()
    {
        assertThatThrownBy(() -> new MVELRoutingRule(
                "evil",
                "",
                0,
                "true",
                List.of("java.nio.file.Files.writeString(java.nio.file.Path.of(\"/tmp/x\"), \"x\")")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden expression construct 'java.nio.file.Files'");
    }

    @Test
    void testMvelRoutingRuleRejectsMaliciousCondition()
    {
        String payload = "\"\".getClass().forName(\"java.lang.Runtime\").getMethod(\"getRuntime\").invoke(null).exec(\"id\") != null";
        assertThatThrownBy(() -> new MVELRoutingRule("evil", "", 0, payload, List.of("result.put(\"routingGroup\", \"etl\")")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden expression construct");
    }

    @Test
    void testMvelRoutingRuleRejectsMaliciousAction()
    {
        assertThatThrownBy(() -> new MVELRoutingRule(
                "evil",
                "",
                0,
                "true",
                List.of("\"\".getClass().forName(\"java.lang.Runtime\").getMethod(\"getRuntime\").invoke(null).exec(\"id\")")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden expression construct");
    }

    @Test
    void testMvelRoutingRuleAcceptsBenignRule()
    {
        assertThatCode(() -> new MVELRoutingRule(
                "airflow",
                "if query from airflow, route to etl group",
                0,
                "request.getHeader(\"X-Trino-Source\") == \"airflow\"",
                List.of("result.put(\"routingGroup\", \"etl\")")))
                .doesNotThrowAnyException();
    }
}
