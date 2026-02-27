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
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import io.trino.gateway.ha.util.QueryRequestMock;
import io.trino.sql.tree.QualifiedName;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.router.RoutingSelector.ROUTING_GROUP_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingSelector
{
    public static final String TRINO_SOURCE_HEADER = "X-Trino-Source";
    public static final String TRINO_CLIENT_TAGS_HEADER = "X-Trino-Client-Tags";

    private static final String DEFAULT_CATALOG = "default_catalog";
    private static final String DEFAULT_SCHEMA = "default_schema";
    private final Duration oneHourRefreshPeriod = new Duration(1, HOURS);

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
                rulesDir + "routing_rules_priorities.yml",
                rulesDir + "routing_rules_if_statements.yml",
                rulesDir + "routing_rules_state.yml");
    }

    @Test
    void testByRoutingGroupHeader()
    {
        HttpServletRequest mockRequest = prepareMockRequest();

        // If the header is present the routing group is the value of that header.
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn("batch_backend");
        RoutingSelector routingSelector = RoutingSelector.byRoutingGroupHeader();
        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isEqualTo("batch_backend");

        // If the header is not present just return null.
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn(null);
        routingSelector = RoutingSelector.byRoutingGroupHeader();
        routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isNull();
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngine(String rulesConfigPath)
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(rulesConfigPath, oneHourRefreshPeriod, requestAnalyzerConfig);

        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader(TRINO_SOURCE_HEADER, "airflow")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();
        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("etl");
    }

    @Test
    void testGetUserFromBasicAuth()
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        String encodedUsernamePassword = Base64.getEncoder().encodeToString("will:supersecret".getBytes(UTF_8));
        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader("Authorization", "Basic " + encodedUsernamePassword)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isEqualTo("will-group");
    }

    @Test
    void testTrinoQueryPropertiesQueryDetails()
            throws IOException
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);
        String query = "SELECT x.*, y.*, z.* FROM catx.schemx.tblx x, schemy.tbly y, tblz z";

        HttpServletRequest mockRequest = new QueryRequestMock().query(query)
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, "cat_default")
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, "schem_\\\"default")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();
        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isEqualTo("tbl-group");
    }

    @Test
    void testTrinoQueryPropertiesCatalogSchemas()
            throws IOException
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);
        String query = "SELECT x.*, y.* FROM catx.nondefault.tblx x, caty.default.tbly y";

        HttpServletRequest mockRequest = new QueryRequestMock().query(query)
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, "catx")
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, "default")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("catalog-schema-group");
    }

    @Test
    void testTrinoQueryPropertiesSessionDefaults()
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, "other_catalog")
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, "other_schema")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("defaults-group");
    }

    @Test
    void testTrinoQueryPropertiesQueryType()
            throws IOException
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);
        String query = "INSERT INTO foo SELECT 1";
        HttpServletRequest mockRequest = new QueryRequestMock()
                .query(query)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("type-group");
    }

    @Test
    void testTrinoQueryPropertiesResourceGroupQueryType()
            throws IOException
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);
        String query = "CREATE TABLE cat.schem.foo (c1 int)";
        HttpServletRequest mockRequest = new QueryRequestMock()
                .query(query)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("resource-group-type-group");
    }

    @Test
    void testTrinoQueryPropertiesAlternateStatementFormat()
            throws IOException
    {
        requestAnalyzerConfig.setClientsUseV2Format(true);
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);
        String body = "{\"preparedStatements\" : {\"statement1\":\"INSERT INTO foo SELECT 1\"}, \"query\": \"EXECUTE statement1\"}";
        HttpServletRequest mockRequest = new QueryRequestMock().query(body)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("type-group");
    }

    @Test
    void testTrinoQueryPropertiesPreparedStatementInHeader()
            throws IOException
    {
        String encodedStatements = "statement1=SELECT+%27s1%27+c%0A%0A,statement2=SELECT+%27s2%27+c%0A%0A,statement3=SELECT%0A++%27%2C%27+comma%0A%2C+%27%3D%27+eq%0A%0A,statement4=SELECT%0A++c1%0A%2C+c2%0AFROM%0A++foo%0A";

        String body = "EXECUTE statement4";
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addAll(TrinoQueryProperties.TRINO_PREPARED_STATEMENT_HEADER_NAME, Arrays.asList(encodedStatements.split(",")));

        HttpServletRequest mockRequest = new QueryRequestMock().query(body).httpHeaders(headers)
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, "cat")
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, "schem")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("statement-header-group");
    }

    @Test
    void testTrinoQueryPropertiesParsingError()
            throws IOException
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(
                        "src/test/resources/rules/routing_rules_trino_query_properties.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        // Invalid SQL that will cause a ParsingException
        String invalidQuery = "SELECT * FROM table WHERE column = ";
        HttpServletRequest mockRequest = new QueryRequestMock()
                .query(invalidQuery)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        // When parsing fails, the query should route to the default "no-match" group
        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("no-match");

        // Verify that the TrinoQueryProperties indicates a parsing failure
        TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) mockRequest.getAttribute(TRINO_QUERY_PROPERTIES);
        assertThat(trinoQueryProperties).isNotNull();
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isFalse();
        assertThat(trinoQueryProperties.getErrorMessage()).isPresent();
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngineSpecialLabel(String rulesConfigPath)
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(rulesConfigPath, oneHourRefreshPeriod, requestAnalyzerConfig);

        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader(TRINO_SOURCE_HEADER, "airflow")
                .httpHeader(TRINO_CLIENT_TAGS_HEADER, "email=test@example.com,label=special")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("etl-special");
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleConfigFiles")
    void testByRoutingRulesEngineNoMatch(String rulesConfigPath)
    {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(rulesConfigPath, oneHourRefreshPeriod, requestAnalyzerConfig);

        // even though special label is present, query is not from airflow.
        // should return no match
        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader(TRINO_CLIENT_TAGS_HEADER, "email=test@example.com,label=special")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isNull();
    }

    @Test
    void testByRoutingRulesEngineFileChange()
            throws Exception
    {
        File file = File.createTempFile("routing_rules", ".yml");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), UTF_8)) {
            writer.write(
                    """
                            ---
                            name: "airflow1"
                            description: "original rule"
                            condition: "request.getHeader(\\"X-Trino-Source\\") == \\"airflow\\""
                            actions:
                              - "result.put(\\"routingGroup\\", \\"etl\\")\"""");
        }

        Duration refreshPeriod = new Duration(1, MILLISECONDS);
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine(file.getPath(), refreshPeriod, requestAnalyzerConfig);

        HttpServletRequest mockRequest = new QueryRequestMock()
                .httpHeader(TRINO_SOURCE_HEADER, "airflow")
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        String routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();
        assertThat(routingGroup).isEqualTo("etl");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), UTF_8)) {
            writer.write(
                    """
                            ---
                            name: "airflow2"
                            description: "updated rule"
                            condition: "request.getHeader(\\"X-Trino-Source\\") == \\"airflow\\""
                            actions:
                              - "result.put(\\"routingGroup\\", \\"etl2\\")\""""); // change from etl to etl2
        }
        Thread.sleep(2 * refreshPeriod.toMillis());

        when(mockRequest.getHeader(TRINO_SOURCE_HEADER)).thenReturn("airflow");
        routingGroup = routingSelector.findRoutingDestination(mockRequest).routingGroup();

        assertThat(routingGroup).isEqualTo("etl2");

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
        HttpServletRequest mockRequest = new QueryRequestMock().query(query)
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, DEFAULT_CATALOG)
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, DEFAULT_SCHEMA)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) mockRequest.getAttribute(TRINO_QUERY_PROPERTIES);

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

        HttpServletRequest mockRequestNoDefaults  = new QueryRequestMock().query(query)
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, DEFAULT_CATALOG)
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, DEFAULT_SCHEMA)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        TrinoQueryProperties trinoQueryPropertiesWithDefaults = getTrinoQueryProps(mockRequestNoDefaults);
        Set<QualifiedName> tablesWithDefaults = trinoQueryPropertiesWithDefaults.getTables();
        assertThat(tablesWithDefaults).containsExactly(QualifiedName.of("cat", "schem", "tbl1"));
        when(mockRequestNoDefaults.getReader()).thenReturn(new BufferedReader(Reader.of(query)));

        TrinoQueryProperties trinoQueryPropertiesNoDefaults = (TrinoQueryProperties) mockRequestNoDefaults.getAttribute(TRINO_QUERY_PROPERTIES);
        Set<QualifiedName> tablesNoDefaults = trinoQueryPropertiesNoDefaults.getTables();
        assertThat(tablesNoDefaults).containsExactly(QualifiedName.of("cat", "schem", "tbl1"));
    }

    private TrinoQueryProperties getTrinoQueryProps(HttpServletRequest request)
    {
        return (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
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
        String query = Files.readString(Path.of("src/test/resources/wide_select.sql"), UTF_8);

        HttpServletRequest mockRequest  = new QueryRequestMock().query(query)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        TrinoQueryProperties trinoQueryProperties = (TrinoQueryProperties) mockRequest.getAttribute(TRINO_QUERY_PROPERTIES);
        assertThat(trinoQueryProperties.tablesContains("kat.schem.widetable")).isTrue();
    }

    @Test
    void testPinByRoutingCluster() {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_group_and_cluster.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        HttpServletRequest mockRequest  = new QueryRequestMock()
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, DEFAULT_CATALOG)
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, DEFAULT_SCHEMA)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();

        when(mockRequest.getHeader("X-Trino-User")).thenReturn("user1");

        RoutingSelectorResponse routingSelectorResponse = routingSelector.findRoutingDestination(mockRequest);

        assertThat(routingSelectorResponse.routingGroup()).isNull();
        assertThat(routingSelectorResponse.routingCluster()).isEqualTo("cluster01");
    }

    @Test
    void testHigherPriorityRoutingRuleWins() {
        RoutingSelector routingSelector =
                RoutingSelector.byRoutingRulesEngine("src/test/resources/rules/routing_rules_group_and_cluster.yml",
                        oneHourRefreshPeriod,
                        requestAnalyzerConfig);

        HttpServletRequest mockRequestForGroup  = new QueryRequestMock()
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, DEFAULT_CATALOG)
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, DEFAULT_SCHEMA)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();
        when(mockRequestForGroup.getHeader("X-Trino-User")).thenReturn("user2");

        RoutingSelectorResponse responseForGroup = routingSelector.findRoutingDestination(mockRequestForGroup);

        // For user2: routingCluster has higher priority than routingGroup (see routing_rules_group_and_cluster.yml).
        // The capped LinkedHashMap keeps only the last (highest-priority) entry, so the routingGroup is evicted.
        assertThat(responseForGroup.routingGroup()).isNull();
        assertThat(responseForGroup.routingCluster()).isEqualTo("adhoc01");

        HttpServletRequest mockRequestForCluster  = new QueryRequestMock()
                .httpHeader(TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME, DEFAULT_CATALOG)
                .httpHeader(TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME, DEFAULT_SCHEMA)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();
        when(mockRequestForCluster.getHeader("X-Trino-User")).thenReturn("user3");

        RoutingSelectorResponse responseForCluster = routingSelector.findRoutingDestination(mockRequestForCluster);

        // For user3: routingGroup has higher priority than routingCluster (see routing_rules_group_and_cluster.yml).
        assertThat(responseForCluster.routingGroup()).isEqualTo("adhoc");
        assertThat(responseForCluster.routingCluster()).isNull();
    }
}
