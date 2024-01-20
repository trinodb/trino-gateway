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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileWriter;
import java.util.stream.Stream;

import static io.trino.gateway.ha.router.RoutingGroupSelector.ROUTING_GROUP_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class TestRoutingGroupSelector
{
    public static final String TRINO_SOURCE_HEADER = "X-Trino-Source";
    public static final String TRINO_CLIENT_TAGS_HEADER = "X-Trino-Client-Tags";

    static Stream<String> provideRoutingRuleConfigFiles()
    {
        String rulesDir = "src/test/resources/rules/";
        return Stream.of(
                rulesDir + "routing_rules_atomic.yml",
                rulesDir + "routing_rules_composite.yml",
                rulesDir + "routing_rules_priorities.yml",
                rulesDir + "routing_rules_if_statements.yml");
    }

    @Test
    public void testByRoutingGroupHeader()
    {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        // If the header is present the routing group is the value of that header.
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn("batch_backend");
        assertThat(RoutingGroupSelector.byRoutingGroupHeader().findRoutingGroup(mockRequest))
                .isEqualTo("batch_backend");

        // If the header is not present just return null.
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn(null);
        assertThat(RoutingGroupSelector.byRoutingGroupHeader().findRoutingGroup(mockRequest)).isNull();
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngine(String rulesConfigPath)
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl");
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngineSpecialLabel(String rulesConfigPath)
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        when(mockRequest.getHeader(TRINO_CLIENT_TAGS_HEADER)).thenReturn(
                "email=test@example.com,label=special");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl-special");
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngineNoMatch(String rulesConfigPath)
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        // even though special label is present, query is not from airflow.
        // should return no match
        when(mockRequest.getHeader(TRINO_CLIENT_TAGS_HEADER)).thenReturn(
                "email=test@example.com,label=special");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isNull();
    }

    @Test
    public void testByRoutingRulesEngineFileChange()
            throws Exception
    {
        File file = File.createTempFile("routing_rules", ".yml");

        FileWriter fw = new FileWriter(file, UTF_8);
        fw.write(
                "---\n"
                        + "name: \"airflow1\"\n"
                        + "description: \"original rule\"\n"
                        + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
                        + "actions:\n"
                        + "  - \"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"");
        fw.close();
        long lastModifed = file.lastModified();

        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(file.getPath());

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl");

        fw = new FileWriter(file, UTF_8);
        fw.write(
                "---\n"
                        + "name: \"airflow2\"\n"
                        + "description: \"updated rule\"\n"
                        + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
                        + "actions:\n"
                        + "  - \"result.put(\\\"routingGroup\\\", \\\"etl2\\\")\""); // change from etl to etl2
        fw.close();
        assertThat(file.setLastModified(lastModifed + 1000)).isTrue();

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl2");
        file.deleteOnExit();
    }
}
