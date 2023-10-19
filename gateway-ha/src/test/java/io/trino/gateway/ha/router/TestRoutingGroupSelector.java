package io.trino.gateway.ha.router;

import static io.trino.gateway.ha.router.RoutingGroupSelector.ROUTING_GROUP_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileWriter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
@TestInstance(Lifecycle.PER_CLASS)
public class TestRoutingGroupSelector {
  public static final String TRINO_SOURCE_HEADER = "X-Trino-Source";
  public static final String TRINO_CLIENT_TAGS_HEADER = "X-Trino-Client-Tags";

  @Test
  public void testByRoutingGroupHeader() {
    HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    // If the header is present the routing group is the value of that header.
    when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn("batch_backend");
    assertEquals("batch_backend",
        RoutingGroupSelector.byRoutingGroupHeader().findRoutingGroup(mockRequest));

    // If the header is not present just return null.
    when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn(null);
    assertNull(RoutingGroupSelector.byRoutingGroupHeader().findRoutingGroup(mockRequest));
  }


  static Stream<String> provideRoutingRuleConfigFiles() {
    String rulesDir = "src/test/resources/rules/";
    return Stream.of(
        rulesDir + "routing_rules_atomic.yml",
        rulesDir + "routing_rules_composite.yml",
        rulesDir + "routing_rules_priorities.yml",
        rulesDir + "routing_rules_if_statements.yml"
    );
  }

  @ParameterizedTest
  @MethodSource("provideRoutingRuleConfigFiles")
  void testByRoutingRulesEngine(String rulesConfigPath) {
    RoutingGroupSelector routingGroupSelector =
        RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

    HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
    assertEquals("etl",
        routingGroupSelector.findRoutingGroup(mockRequest));
  }

  @ParameterizedTest
  @MethodSource("provideRoutingRuleConfigFiles")
  void testByRoutingRulesEngineSpecialLabel(String rulesConfigPath) {
    RoutingGroupSelector routingGroupSelector =
        RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

    HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
    when(mockRequest.getHeader(TRINO_CLIENT_TAGS_HEADER)).thenReturn(
        "email=test@example.com,label=special");
    assertEquals("etl-special",
        routingGroupSelector.findRoutingGroup(mockRequest));
  }

  @ParameterizedTest
  @MethodSource("provideRoutingRuleConfigFiles")
  void testByRoutingRulesEngineNoMatch(String rulesConfigPath) {
    RoutingGroupSelector routingGroupSelector =
        RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath);

    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    // even though special label is present, query is not from airflow.
    // should return no match
    when(mockRequest.getHeader(TRINO_CLIENT_TAGS_HEADER)).thenReturn(
        "email=test@example.com,label=special");
    assertNull(routingGroupSelector.findRoutingGroup(mockRequest));
  }

  //Todo: The functionality of reading the file before every request needs to be smarter
  @Test
  public void testByRoutingRulesEngineFileChange() throws Exception {
    File file = File.createTempFile("routing_rules", ".yml");

    FileWriter fw = new FileWriter(file);
    fw.write(
        "---\n"
            + "name: \"airflow\"\n"
            + "description: \"if query from airflow, route to etl group\"\n"
            + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
            + "actions:\n"
            + "  - \"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"");
    fw.close();

    RoutingGroupSelector routingGroupSelector =
        RoutingGroupSelector.byRoutingRulesEngine(file.getPath());

    HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
    assertEquals("etl",
        routingGroupSelector.findRoutingGroup(mockRequest));

    fw = new FileWriter(file);
    fw.write(
        "---\n"
            + "name: \"airflow\"\n"
            + "description: \"if query from airflow, route to etl group\"\n"
            + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
            + "actions:\n"
            + "  - \"result.put(\\\"routingGroup\\\", \\\"etl2\\\")\""); // change from etl to etl2
    fw.close();

    when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
    assertEquals("etl2",
        routingGroupSelector.findRoutingGroup(mockRequest));
    file.deleteOnExit();
  }
}
