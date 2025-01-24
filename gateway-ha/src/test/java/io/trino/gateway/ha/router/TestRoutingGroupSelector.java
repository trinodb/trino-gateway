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

import com.google.common.collect.ImmutableSet;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.sql.tree.QualifiedName;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static io.trino.gateway.ha.router.RoutingGroupSelector.ROUTING_GROUP_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingGroupSelector
{
    public static final String TRINO_SOURCE_HEADER = "X-Trino-Source";
    public static final String TRINO_CLIENT_TAGS_HEADER = "X-Trino-Client-Tags";

    private static final String DEFAULT_CATALOG = "default_catalog";
    private static final String DEFAULT_SCHEMA = "default_schema";

    RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();

    @BeforeAll
    void initialize()
    {
        requestAnalyzerConfig.setAnalyzeRequest(true);
    }

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
    void testByRoutingGroupHeader()
    {
        HttpServletRequest mockRequest = prepareMockRequest();

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
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath, requestAnalyzerConfig);

        HttpServletRequest mockRequest = prepareMockRequest();

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl");
    }

    @Test
    void testGetUserFromBasicAuth()
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);

        String encodedUsernamePassword = Base64.getEncoder().encodeToString("will:supersecret".getBytes(UTF_8));
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getHeader("Authorization")).thenReturn("Basic " + encodedUsernamePassword);

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("will-group");
    }

    @Test
    void testTrinoQueryPropertiesQueryDetails()
            throws IOException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        String query = "SELECT x.*, y.*, z.* FROM catx.schemx.tblx x, schemy.tbly y, tblz z";
        Reader reader = new StringReader(query);
        BufferedReader bufferedReader = new BufferedReader(reader);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn("cat_default");
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn("schem_\\\"default");

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("tbl-group");
    }

    @Test
    void testTrinoQueryPropertiesCatalogSchemas()
            throws IOException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        String query = "SELECT x.*, y.* FROM catx.nondefault.tblx x, caty.default.tbly y";
        Reader reader = new StringReader(query);
        BufferedReader bufferedReader = new BufferedReader(reader);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn("catx");
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn("default");

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("catalog-schema-group");
    }

    @Test
    void testTrinoQueryPropertiesSessionDefaults()
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();

        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn("other_catalog");
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn("other_schema");

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("defaults-group");
    }

    @Test
    void testTrinoQueryPropertiesQueryType()
            throws IOException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        String query = "INSERT INTO foo SELECT 1";
        Reader reader = new StringReader(query);
        BufferedReader bufferedReader = new BufferedReader(reader);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("type-group");
    }

    @Test
    void testTrinoQueryPropertiesResourceGroupQueryType()
            throws IOException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader("CREATE TABLE cat.schem.foo (c1 int)")));

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("resource-group-type-group");
    }

    @Test
    void testTrinoQueryPropertiesAlternateStatementFormat()
            throws IOException
    {
        requestAnalyzerConfig.setClientsUseV2Format(true);
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        String body = "{\"preparedStatements\" : {\"statement1\":\"INSERT INTO foo SELECT 1\"}, \"query\": \"EXECUTE statement1\"}";
        Reader reader = new StringReader(body);
        BufferedReader bufferedReader = new BufferedReader(reader);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("type-group");
    }

    @Test
    void testTrinoQueryPropertiesPreparedStatementInHeader()
            throws IOException
    {
        String encodedStatements = "statement1=SELECT+%27s1%27+c%0A%0A,statement2=SELECT+%27s2%27+c%0A%0A,statement3=SELECT%0A++%27%2C%27+comma%0A%2C+%27%3D%27+eq%0A%0A,statement4=SELECT%0A++c1%0A%2C+c2%0AFROM%0A++foo%0A";

        String body = "EXECUTE statement4";
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_trino_query_properties.yml", requestAnalyzerConfig);
        Reader reader = new StringReader(body);
        BufferedReader bufferedReader = new BufferedReader(reader);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_PREPARED_STATEMENT_HEADER_NAME)).thenReturn(encodedStatements);
        when(mockRequest.getHeaders(TrinoQueryProperties.TRINO_PREPARED_STATEMENT_HEADER_NAME)).thenReturn(Collections.enumeration(Arrays.asList(encodedStatements.split(","))));
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn("cat");
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn("schem");

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("statement-header-group");
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngineSpecialLabel(String rulesConfigPath)
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath, requestAnalyzerConfig);

        HttpServletRequest mockRequest = prepareMockRequest();

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
                RoutingGroupSelector.byRoutingRulesEngine(rulesConfigPath, requestAnalyzerConfig);

        HttpServletRequest mockRequest = prepareMockRequest();
        // even though special label is present, query is not from airflow.
        // should return no match
        when(mockRequest.getHeader(TRINO_CLIENT_TAGS_HEADER)).thenReturn(
                "email=test@example.com,label=special");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isNull();
    }

    @Test
    void testByRoutingRulesEngineFileChange()
            throws Exception
    {
        File file = File.createTempFile("routing_rules", ".yml");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), UTF_8)) {
            writer.write(
                    "---\n"
                            + "name: \"airflow1\"\n"
                            + "description: \"original rule\"\n"
                            + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
                            + "actions:\n"
                            + "  - \"result.put(\\\"routingGroup\\\", \\\"etl\\\")\"");
        }
        long lastModified = file.lastModified();

        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRulesEngine(file.getPath(), requestAnalyzerConfig);

        HttpServletRequest mockRequest = prepareMockRequest();

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), UTF_8)) {
            writer.write(
                    "---\n"
                            + "name: \"airflow2\"\n"
                            + "description: \"updated rule\"\n"
                            + "condition: \"request.getHeader(\\\"X-Trino-Source\\\") == \\\"airflow\\\"\"\n"
                            + "actions:\n"
                            + "  - \"result.put(\\\"routingGroup\\\", \\\"etl2\\\")\""); // change from etl to etl2
        }
        assertThat(file.setLastModified(lastModified + 1000)).isTrue();

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("etl2");
        file.deleteOnExit();
    }

    private Stream<Arguments> provideTableExtractionQueries()
    {
        return Stream.of(
                Arguments.of("ALTER TABLE cat.schem.tbl ADD COLUMN extraInfo VARCHAR WITH (nullable = true, encoding = 'plain')",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("ANALYZE cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("CREATE CATALOG kat USING iceberg", ImmutableSet.of("kat"), ImmutableSet.of(), ImmutableSet.of()),
                Arguments.of("CREATE MATERIALIZED VIEW cat.mvschem.mv AS SELECT c1, c2 from cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem", "mvschem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"), QualifiedName.of("cat", "mvschem", "mv"))),
                Arguments.of("CREATE SCHEMA kat.schem", ImmutableSet.of("kat"), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("CREATE SCHEMA schem", ImmutableSet.of(DEFAULT_CATALOG), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("CREATE TABLE cat.schem.tbl(c1 varchar)",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("CREATE VIEW cat.vwschem.vw AS SELECT c1, c2 from cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem", "vwschem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"), QualifiedName.of("cat", "vwschem", "vw"))),
                Arguments.of("CREATE TABLE cat.schem2.tbl2 AS SELECT c1, c2 from cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem", "schem2"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"), QualifiedName.of("cat", "schem2", "tbl2"))),
                Arguments.of("DROP CATALOG kat", ImmutableSet.of("kat"), ImmutableSet.of(), ImmutableSet.of()),
                Arguments.of("DROP SCHEMA kat.schem", ImmutableSet.of("kat"), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("DROP SCHEMA schem", ImmutableSet.of(DEFAULT_CATALOG), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("DROP TABLE cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("ALTER MATERIALIZED VIEW cat.mvschem.mv RENAME TO cat.mvschem2.mv2",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("mvschem", "mvschem2"),
                        ImmutableSet.of(QualifiedName.of("cat", "mvschem", "mv"), QualifiedName.of("cat", "mvschem2", "mv2"))),
                Arguments.of("ALTER SCHEMA kat.schem RENAME TO schem2", ImmutableSet.of("kat"), ImmutableSet.of("schem", "schem2"), ImmutableSet.of()),
                Arguments.of("ALTER TABLE cat.schem.tbl RENAME TO schem2.tbl2",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem", "schem2"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"), QualifiedName.of("cat", "schem2", "tbl2"))),
                Arguments.of("ALTER VIEW cat.schem.vw RENAME TO schem2.vw2",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem", "schem2"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "vw"), QualifiedName.of("cat", "schem2", "vw2"))),
                Arguments.of("ALTER TABLE cat.schem.tbl SET PROPERTIES property_name = 'expression'",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("DESCRIBE cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("SHOW CREATE SCHEMA cat.schem",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of()),
                Arguments.of("SHOW CREATE SCHEMA schem",
                        ImmutableSet.of(DEFAULT_CATALOG),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of()),
                Arguments.of("SHOW CREATE TABLE cat.schem.tbl",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("SHOW CREATE VIEW cat.schem.vw",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "vw"))),
                Arguments.of("SHOW SCHEMAS FROM kat", ImmutableSet.of("kat"), ImmutableSet.of(), ImmutableSet.of()),
                Arguments.of("SHOW SCHEMAS", ImmutableSet.of(DEFAULT_CATALOG), ImmutableSet.of(), ImmutableSet.of()),
                Arguments.of("SHOW TABLES FROM kat.schem", ImmutableSet.of("kat"), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("SHOW TABLES", ImmutableSet.of(DEFAULT_CATALOG), ImmutableSet.of(DEFAULT_SCHEMA), ImmutableSet.of()),
                Arguments.of("ALTER SCHEMA kat.schem SET AUTHORIZATION will", ImmutableSet.of("kat"), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("ALTER SCHEMA schem SET AUTHORIZATION will", ImmutableSet.of(DEFAULT_CATALOG), ImmutableSet.of("schem"), ImmutableSet.of()),
                Arguments.of("ALTER TABLE cat.schem.tbl SET AUTHORIZATION will",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))),
                Arguments.of("ALTER VIEW cat.schem.vw SET AUTHORIZATION will",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "vw"))),
                Arguments.of("SELECT * FROM TABLE(kat.system.funk(arg => 'expr'))",
                        ImmutableSet.of("kat"),
                        ImmutableSet.of("system"),
                        ImmutableSet.of(QualifiedName.of("kat", "system", "funk"))),
                Arguments.of("EXECUTE IMMEDIATE 'SELECT * FROM cat.schem.tbl'",
                        ImmutableSet.of("cat"),
                        ImmutableSet.of("schem"),
                        ImmutableSet.of(QualifiedName.of("cat", "schem", "tbl"))));
    }

    @ParameterizedTest
    @MethodSource("provideTableExtractionQueries")
    void testTrinoQueryPropertiesTableExtraction(String query, Set<String> catalogs, Set<String> schemas, Set<QualifiedName> tables)
            throws IOException
    {
        BufferedReader bufferedReader = new BufferedReader(new StringReader(query));
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn(DEFAULT_CATALOG);
        when(mockRequest.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn(DEFAULT_SCHEMA);

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                mockRequest,
                requestAnalyzerConfig.isClientsUseV2Format(),
                requestAnalyzerConfig.getMaxBodySize());

        assertThat(trinoQueryProperties.getTables()).isEqualTo(tables);
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(schemas);
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(catalogs);
    }

    @Test
    void testWithQueryNameExcluded()
            throws IOException
    {
        String query = """
                WITH dos AS (SELECT c1 from cat.schem.tbl1),
                uno as (SELECT c1 FROM dos)
                SELECT c1 FROM uno, dos
                """;
        HttpServletRequest mockRequestWithDefaults = prepareMockRequest();
        when(mockRequestWithDefaults.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequestWithDefaults.getHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME)).thenReturn(DEFAULT_CATALOG);
        when(mockRequestWithDefaults.getHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME)).thenReturn(DEFAULT_SCHEMA);

        TrinoQueryProperties trinoQueryPropertiesWithDefaults = new TrinoQueryProperties(
                mockRequestWithDefaults,
                requestAnalyzerConfig.isClientsUseV2Format(),
                requestAnalyzerConfig.getMaxBodySize());
        Set<QualifiedName> tablesWithDefaults = trinoQueryPropertiesWithDefaults.getTables();
        assertThat(tablesWithDefaults).containsExactly(QualifiedName.of("cat", "schem", "tbl1"));

        HttpServletRequest mockRequestNoDefaults = prepareMockRequest();
        when(mockRequestNoDefaults.getReader()).thenReturn(new BufferedReader(new StringReader(query)));

        TrinoQueryProperties trinoQueryPropertiesNoDefaults = new TrinoQueryProperties(
                mockRequestNoDefaults,
                requestAnalyzerConfig.isClientsUseV2Format(),
                requestAnalyzerConfig.getMaxBodySize());
        Set<QualifiedName> tablesNoDefaults = trinoQueryPropertiesNoDefaults.getTables();
        assertThat(tablesNoDefaults).containsExactly(QualifiedName.of("cat", "schem", "tbl1"));
    }

    private HttpServletRequest prepareMockRequest()
    {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        return mockRequest;
    }

    @Test
    void testLongQuery()
            throws IOException
    {
        BufferedReader bufferedReader = Files.newBufferedReader(Path.of("src/test/resources/wide_select.sql"), UTF_8);
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(bufferedReader);
        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                mockRequest,
                requestAnalyzerConfig.isClientsUseV2Format(),
                requestAnalyzerConfig.getMaxBodySize());
        assertThat(trinoQueryProperties.tablesContains("kat.schem.widetable")).isTrue();
    }
}
