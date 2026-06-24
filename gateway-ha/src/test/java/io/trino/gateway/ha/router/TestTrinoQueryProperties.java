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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import io.airlift.json.JsonCodec;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Call;
import io.trino.sql.tree.DropFunction;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.ResetSession;
import io.trino.sql.tree.SetSession;
import io.trino.sql.tree.Statement;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.trino.gateway.ha.router.TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME;
import static io.trino.gateway.ha.router.TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestTrinoQueryProperties
{
    @Test
    void testJsonCreator()
    {
        JsonCodec<TrinoQueryProperties> codec = JsonCodec.jsonCodec(TrinoQueryProperties.class);
        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                "SELECT c1 from c.s.t1",
                "SELECT",
                "SELECT",
                ImmutableList.of("c.s.t1"),
                Optional.empty(),
                Optional.empty(),
                ImmutableSet.of("c"),
                ImmutableSet.of("s"),
                ImmutableSet.of("c.s"),
                true,
                Optional.empty());

        String trinoQueryPropertiesJson = codec.toJson(trinoQueryProperties);
        TrinoQueryProperties deserializedTrinoQueryProperties = codec.fromJson(trinoQueryPropertiesJson);

        assertThat(deserializedTrinoQueryProperties.getBody()).isEqualTo(trinoQueryProperties.getBody());
        assertThat(deserializedTrinoQueryProperties.getQueryType()).isEqualTo(trinoQueryProperties.getQueryType());
        assertThat(deserializedTrinoQueryProperties.getResourceGroupQueryType()).isEqualTo(trinoQueryProperties.getResourceGroupQueryType());
        assertThat(deserializedTrinoQueryProperties.getTables()).isEqualTo(trinoQueryProperties.getTables());
        assertThat(deserializedTrinoQueryProperties.getDefaultCatalog()).isEqualTo(trinoQueryProperties.getDefaultCatalog());
        assertThat(deserializedTrinoQueryProperties.getDefaultSchema()).isEqualTo(trinoQueryProperties.getDefaultSchema());
        assertThat(deserializedTrinoQueryProperties.getSchemas()).isEqualTo(trinoQueryProperties.getSchemas());
        assertThat(deserializedTrinoQueryProperties.getCatalogs()).isEqualTo(trinoQueryProperties.getCatalogs());
        assertThat(deserializedTrinoQueryProperties.getCatalogSchemas()).isEqualTo(trinoQueryProperties.getCatalogSchemas());
        assertThat(deserializedTrinoQueryProperties.isNewQuerySubmission()).isEqualTo(trinoQueryProperties.isNewQuerySubmission());
        assertThat(deserializedTrinoQueryProperties.isQueryParsingSuccessful()).isEqualTo(trinoQueryProperties.isQueryParsingSuccessful());
        assertThat(deserializedTrinoQueryProperties.getErrorMessage()).isEqualTo(trinoQueryProperties.getErrorMessage());
    }

    @Test
    void testJsonCreatorWithEmptyProperties()
    {
        JsonCodec<TrinoQueryProperties> codec = JsonCodec.jsonCodec(TrinoQueryProperties.class);
        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                "",
                "",
                "",
                ImmutableList.of(),
                Optional.empty(),
                Optional.empty(),
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of(),
                true,
                Optional.empty());

        String trinoQueryPropertiesJson = codec.toJson(trinoQueryProperties);
        TrinoQueryProperties deserializedTrinoQueryProperties = codec.fromJson(trinoQueryPropertiesJson);

        assertThat(deserializedTrinoQueryProperties.getBody()).isEqualTo(trinoQueryProperties.getBody());
        assertThat(deserializedTrinoQueryProperties.getQueryType()).isEqualTo(trinoQueryProperties.getQueryType());
        assertThat(deserializedTrinoQueryProperties.getResourceGroupQueryType()).isEqualTo(trinoQueryProperties.getResourceGroupQueryType());
        assertThat(deserializedTrinoQueryProperties.getTables()).isEqualTo(trinoQueryProperties.getTables());
        assertThat(deserializedTrinoQueryProperties.getDefaultCatalog()).isEqualTo(trinoQueryProperties.getDefaultCatalog());
        assertThat(deserializedTrinoQueryProperties.getDefaultSchema()).isEqualTo(trinoQueryProperties.getDefaultSchema());
        assertThat(deserializedTrinoQueryProperties.getSchemas()).isEqualTo(trinoQueryProperties.getSchemas());
        assertThat(deserializedTrinoQueryProperties.getCatalogs()).isEqualTo(trinoQueryProperties.getCatalogs());
        assertThat(deserializedTrinoQueryProperties.getCatalogSchemas()).isEqualTo(trinoQueryProperties.getCatalogSchemas());
        assertThat(deserializedTrinoQueryProperties.isNewQuerySubmission()).isEqualTo(trinoQueryProperties.isNewQuerySubmission());
        assertThat(deserializedTrinoQueryProperties.isQueryParsingSuccessful()).isEqualTo(trinoQueryProperties.isQueryParsingSuccessful());
        assertThat(deserializedTrinoQueryProperties.getErrorMessage()).isEqualTo(trinoQueryProperties.getErrorMessage());
    }

    @Test
    void testCreateSchemaSinglePart()
            throws IOException
    {
        String query = "CREATE SCHEMA myschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Single part schema should use the default catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("default_catalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("myschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("default_catalog.myschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testCreateSchemaTwoPart()
            throws IOException
    {
        String query = "CREATE SCHEMA mycatalog.myschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Two-part schema should use a specified catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("mycatalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("myschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("mycatalog.myschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testDropSchemaSinglePart()
            throws IOException
    {
        String query = "DROP SCHEMA testschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Single part schema should use the default catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("test_catalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("testschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("test_catalog.testschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testDropSchemaTwoPart()
            throws IOException
    {
        String query = "DROP SCHEMA testcatalog.testschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Two-part schema should use a specified catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("testcatalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("testschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("testcatalog.testschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testShowTablesSinglePart()
            throws IOException
    {
        String query = "SHOW TABLES FROM myschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Single part schema should use the default catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("default_catalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("myschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("default_catalog.myschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testShowTablesTwoPart()
            throws IOException
    {
        String query = "SHOW TABLES FROM mycatalog.myschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Two-part schema should use a specified catalog
        assertThat(trinoQueryProperties.getCatalogs()).isEqualTo(ImmutableSet.of("mycatalog"));
        assertThat(trinoQueryProperties.getSchemas()).isEqualTo(ImmutableSet.of("myschema"));
        assertThat(trinoQueryProperties.getCatalogSchemas()).isEqualTo(ImmutableSet.of("mycatalog.myschema"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testSchemaQualifiedNameWithoutDefaults()
            throws IOException
    {
        String query = "CREATE SCHEMA myschema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        // Don't set default catalog or schema headers

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // The exception is caught and stored in errorMessage, not re-thrown
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isFalse();
        assertThat(trinoQueryProperties.getErrorMessage()).isPresent();
        assertThat(trinoQueryProperties.getErrorMessage().get()).contains("Name not fully qualified");
    }

    @Test
    void testSchemaQualifiedNameWithEmptyDefaults()
            throws IOException
    {
        String query = "SHOW TABLES"; // This will use empty schema optional
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // When schema optional is empty, it should use defaults
        assertThat(trinoQueryProperties.getCatalogs()).contains("test_catalog");
        assertThat(trinoQueryProperties.getSchemas()).contains("test_schema");
        assertThat(trinoQueryProperties.getCatalogSchemas()).contains("test_catalog.test_schema");
    }

    @Test
    void testSchemaQualifiedNameWithInvalidParts()
            throws IOException
    {
        // Note: This test demonstrates that the default case in the switch statement is
        // reached for schemas with >2 parts, which will log an error but continue processing.
        // Since we cannot easily mock the SQL parser to create a QualifiedName with >2 parts
        // from a valid SQL statement, this test documents the expected behavior.

        // Test that a valid two-part schema name works correctly (boundary case)
        String query = "CREATE SCHEMA catalog.schema";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Verify the two-part case works and doesn't fall through to default
        assertThat(trinoQueryProperties.getCatalogs()).containsExactly("catalog");
        assertThat(trinoQueryProperties.getSchemas()).containsExactly("schema");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactly("catalog.schema");
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testSimpleJoinWithDifferentCatalogs()
            throws IOException
    {
        String query = "SELECT * FROM catalog1.schema1.table1 t1 JOIN catalog2.schema2.table2 t2 ON t1.id = t2.id";
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract both catalogs and schemas
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("schema1", "schema2");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder("catalog1.schema1", "catalog2.schema2");
        assertThat(trinoQueryProperties.getTables()).hasSize(2);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testComplexJoinWithMultipleCatalogs()
            throws IOException
    {
        String query =
                """
                SELECT t1.name, t2.value, t3.description, t4.status
                FROM catalog1.sales.customers t1
                JOIN catalog1.sales.orders t2 ON t1.id = t2.customer_id
                LEFT JOIN catalog2.inventory.products t3 ON t2.product_id = t3.id
                RIGHT JOIN catalog3.analytics.metrics t4 ON t3.category = t4.category
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract all three catalogs and their respective schemas
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2", "catalog3");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("sales", "inventory", "analytics");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.sales", "catalog2.inventory", "catalog3.analytics");
        assertThat(trinoQueryProperties.getTables()).hasSize(4);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testMixedQualifiedNamesInJoin()
            throws IOException
    {
        String query =
                """
                SELECT *
                FROM catalog1.schema1.table1 t1
                JOIN schema2.table2 t2 ON t1.id = t2.id
                JOIN table3 t3 ON t2.ref = t3.ref
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should handle mix of fully qualified, schema qualified, and unqualified names
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "default_catalog");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("schema1", "schema2", "default_schema");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.schema1", "default_catalog.schema2", "default_catalog.default_schema");
        assertThat(trinoQueryProperties.getTables()).hasSize(3);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testSubqueryJoinWithMultipleCatalogs()
            throws IOException
    {
        String query =
                """
                SELECT main.id, sub.total
                FROM catalog1.sales.orders main
                JOIN (
                    SELECT customer_id, SUM(amount) as total
                    FROM catalog2.billing.payments
                    WHERE status = 'completed'
                    GROUP BY customer_id
                ) sub ON main.customer_id = sub.customer_id
                WHERE main.order_date >= '2023-01-01'
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract catalogs and schemas from both main query and subquery
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("sales", "billing");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder("catalog1.sales", "catalog2.billing");
        assertThat(trinoQueryProperties.getTables()).hasSize(2);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testUnionWithMultipleCatalogs()
            throws IOException
    {
        String query =
                """
                SELECT name, 'active' as status FROM catalog1.users.active_users
                UNION ALL
                SELECT name, 'inactive' as status FROM catalog2.users.inactive_users
                UNION ALL
                SELECT name, 'pending' as status FROM catalog3.admin.pending_users
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract all catalogs and schemas from UNION query
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2", "catalog3");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("users", "admin");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.users", "catalog2.users", "catalog3.admin");
        assertThat(trinoQueryProperties.getTables()).hasSize(3);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testCTEWithMultipleCatalogs()
            throws IOException
    {
        String query =
                """
                WITH sales_summary AS (
                    SELECT customer_id, SUM(amount) as total_sales
                    FROM catalog1.sales.transactions
                    GROUP BY customer_id
                ),
                customer_info AS (
                    SELECT id, name, region
                    FROM catalog2.crm.customers
                    WHERE active = true
                )
                SELECT c.name, c.region, s.total_sales
                FROM customer_info c
                JOIN sales_summary s ON c.id = s.customer_id
                JOIN catalog3.geo.regions r ON c.region = r.code
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract catalogs and schemas from CTE and main query, ignoring temporary tables
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2", "catalog3");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("sales", "crm", "geo");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.sales", "catalog2.crm", "catalog3.geo");
        assertThat(trinoQueryProperties.getTables()).hasSize(3); // Should not include CTE tables
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testInsertSelectWithMultipleCatalogs()
            throws IOException
    {
        String query =
                """
                INSERT INTO catalog1.warehouse.inventory (product_id, quantity, location)
                SELECT p.id, s.available_qty, w.default_location
                FROM catalog2.products.items p
                JOIN catalog2.stock.availability s ON p.id = s.product_id
                JOIN catalog3.locations.warehouses w ON s.warehouse_id = w.id
                WHERE s.available_qty > 0
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should extract catalogs and schemas from both INSERT target and SELECT source
        assertThat(trinoQueryProperties.getCatalogs()).containsExactlyInAnyOrder("catalog1", "catalog2", "catalog3");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("warehouse", "products", "stock", "locations");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.warehouse", "catalog2.products", "catalog2.stock", "catalog3.locations");
        assertThat(trinoQueryProperties.getTables()).hasSize(4);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testSameCatalogDifferentSchemas()
            throws IOException
    {
        String query =
                """
                SELECT o.order_id, c.name, p.price
                FROM catalog1.sales.orders o
                JOIN catalog1.customers.profiles c ON o.customer_id = c.id
                JOIN catalog1.products.items p ON o.product_id = p.id
                """;
        ContainerRequestContext mockRequest = prepareMockRequest(query);
        when(mockRequest.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Should have one catalog but multiple schemas
        assertThat(trinoQueryProperties.getCatalogs()).containsExactly("catalog1");
        assertThat(trinoQueryProperties.getSchemas()).containsExactlyInAnyOrder("sales", "customers", "products");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactlyInAnyOrder(
                "catalog1.sales", "catalog1.customers", "catalog1.products");
        assertThat(trinoQueryProperties.getTables()).hasSize(3);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testQueryParsingWhenContentTypeHasNoCharset()
            throws IOException
    {
        String query = "SELECT * FROM mycatalog.myschema.mytable";
        ContainerRequestContext mockRequest = prepareMockRequest(query, MediaType.valueOf("application/json"));

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Trino HTTP clients commonly omit the charset parameter; treat the body as UTF-8 so routing rules still see the parsed query.
        assertThat(trinoQueryProperties.getCatalogs()).containsExactly("mycatalog");
        assertThat(trinoQueryProperties.getSchemas()).containsExactly("myschema");
        assertThat(trinoQueryProperties.getTables()).hasSize(1);
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    @Test
    void testQueryParsingSkippedForNonUtf8Charset()
            throws IOException
    {
        String query = "SELECT * FROM mycatalog.myschema.mytable";
        ContainerRequestContext mockRequest = prepareMockRequest(query, MediaType.valueOf("application/json; charset=ISO-8859-1"));

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // An explicit non-UTF-8 charset is still skipped, since the body bytes cannot be safely decoded as UTF-8.
        assertThat(trinoQueryProperties.getBody()).isEmpty();
        assertThat(trinoQueryProperties.getCatalogs()).isEmpty();
        assertThat(trinoQueryProperties.getSchemas()).isEmpty();
        assertThat(trinoQueryProperties.getTables()).isEmpty();
    }

    @Test
    void testWithoutMediaType()
    {
        String query = "SELECT * FROM mycatalog.myschema.mytable";
        ContainerRequestContext mockRequest = prepareMockRequest(query, null);

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Some Trino clients (e.g. dbt-trino) omit the media type
        assertThat(trinoQueryProperties.getCatalogs()).containsExactly("mycatalog");
        assertThat(trinoQueryProperties.getSchemas()).containsExactly("myschema");
        assertThat(trinoQueryProperties.getTables()).containsExactly(QualifiedName.of("mycatalog", "myschema", "mytable"));
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    // SQL sample for every io.trino.sql.tree statement that names an object (table/view/branch/schema) via a
    // bare QualifiedName field — the kind visitNode()'s child recursion never reaches, so each needs an
    // explicit switch case. This list is the single source of truth: testObjectNameStatementExtractsItsObject
    // runs each sample through extraction (so removing a switch case fails), and the drift guard
    // testEveryObjectNameStatementIsExtracted fails when a Trino upgrade adds a QualifiedName-bearing
    // statement missing from it. Samples are fully qualified (c.s.t / c.s) so no request header defaults
    // are needed.
    private static final List<String> OBJECT_NAME_STATEMENT_SAMPLES = ImmutableList.<String>builder()
            .add("ALTER TABLE c.s.t ADD COLUMN x bigint")                       // AddColumn
            .add("ANALYZE c.s.t")                                              // Analyze
            .add("COMMENT ON TABLE c.s.t IS 'x'")                              // Comment
            .add("CREATE BRANCH b IN TABLE c.s.t")                            // CreateBranch
            .add("CREATE MATERIALIZED VIEW c.s.t AS SELECT 1 x")             // CreateMaterializedView
            .add("CREATE SCHEMA c.s")                                         // CreateSchema
            .add("CREATE TABLE c.s.t (x bigint)")                            // CreateTable
            .add("CREATE TABLE c.s.t AS SELECT 1 x")                         // CreateTableAsSelect
            .add("CREATE VIEW c.s.t AS SELECT 1 x")                          // CreateView
            .add("DROP BRANCH b IN TABLE c.s.t")                             // DropBranch
            .add("ALTER TABLE c.s.t DROP COLUMN x")                          // DropColumn
            .add("ALTER TABLE c.s.t ALTER COLUMN x DROP DEFAULT")            // DropDefaultValue
            .add("DROP MATERIALIZED VIEW c.s.t")                            // DropMaterializedView
            .add("ALTER TABLE c.s.t ALTER COLUMN x DROP NOT NULL")          // DropNotNullConstraint
            .add("DROP SCHEMA c.s")                                         // DropSchema
            .add("DROP TABLE c.s.t")                                        // DropTable
            .add("DROP VIEW c.s.t")                                         // DropView
            .add("ALTER BRANCH fb IN TABLE c.s.t FAST FORWARD TO tb")       // FastForwardBranch
            .add("INSERT INTO c.s.t VALUES (1)")                            // Insert
            .add("REFRESH MATERIALIZED VIEW c.s.t")                         // RefreshMaterializedView
            .add("ALTER VIEW c.s.t REFRESH")                                // RefreshView
            .add("ALTER TABLE c.s.t RENAME COLUMN a TO b")                  // RenameColumn
            .add("ALTER MATERIALIZED VIEW c.s.t RENAME TO c.s.t2")          // RenameMaterializedView
            .add("ALTER SCHEMA c.s RENAME TO s2")                           // RenameSchema
            .add("ALTER TABLE c.s.t RENAME TO c.s.t2")                      // RenameTable
            .add("ALTER VIEW c.s.t RENAME TO c.s.t2")                       // RenameView
            .add("ALTER TABLE c.s.t SET AUTHORIZATION u")                   // SetAuthorizationStatement
            .add("ALTER TABLE c.s.t ALTER COLUMN a SET DATA TYPE bigint")   // SetColumnType
            .add("ALTER TABLE c.s.t ALTER COLUMN a SET DEFAULT 1")          // SetDefaultValue
            .add("ALTER TABLE c.s.t SET PROPERTIES x = 1")                  // SetProperties
            .add("SHOW BRANCHES FROM TABLE c.s.t")                          // ShowBranches
            .add("SHOW COLUMNS FROM c.s.t")                                 // ShowColumns
            .add("SHOW CREATE TABLE c.s.t")                                 // ShowCreate
            .add("TRUNCATE TABLE c.s.t")                                    // TruncateTable
            .build();

    // QualifiedName-bearing statements whose name is not a routable table/catalog/schema object:
    // Call (procedure), DropFunction (routine), SET/RESET SESSION (session property path).
    private static final Set<Class<? extends Statement>> QUALIFIED_NAME_NOT_AN_OBJECT =
            ImmutableSet.<Class<? extends Statement>>builder()
                    .add(Call.class)
                    .add(DropFunction.class)
                    .add(ResetSession.class)
                    .add(SetSession.class)
                    .build();

    private static final SqlParser SQL_PARSER = new SqlParser();

    static Stream<String> objectNameStatementSamples()
    {
        return OBJECT_NAME_STATEMENT_SAMPLES.stream();
    }

    // Behavioral check: each sampled statement must actually expose its object to routing. Because the
    // assertion runs the statement through extraction, removing or breaking its visitNode() case fails here
    // (a parallel hand-maintained set would not).
    @ParameterizedTest
    @MethodSource("objectNameStatementSamples")
    void testObjectNameStatementExtractsItsObject(@Language("sql") String sql)
    {
        TrinoQueryProperties properties = new TrinoQueryProperties(prepareMockRequest(sql), false, 1024 * 1024);

        assertThat(properties.isQueryParsingSuccessful()).as(sql).isTrue();
        boolean extractedItsObject = properties.getTables().contains(QualifiedName.of("c", "s", "t"))
                || properties.getSchemas().contains("s");
        assertThat(extractedItsObject)
                .as("'%s' did not expose its object to routing; visitNode() is likely missing this statement's case", sql)
                .isTrue();
    }

    // Drift guard: discover every concrete Statement that carries a bare QualifiedName and fail if one is
    // neither sampled (and therefore extracted) above nor explicitly excluded. This catches a Trino upgrade
    // that adds a QualifiedName-bearing statement before it can silently misroute.
    @Test
    void testEveryObjectNameStatementIsExtracted()
            throws IOException
    {
        Set<Class<? extends Statement>> sampledStatements = OBJECT_NAME_STATEMENT_SAMPLES.stream()
                .map(sql -> SQL_PARSER.createStatement(sql).getClass())
                .collect(Collectors.toSet());

        Set<Class<?>> withQualifiedNameGetter = ClassPath.from(Statement.class.getClassLoader())
                .getTopLevelClasses("io.trino.sql.tree").stream()
                .map(ClassPath.ClassInfo::load)
                .filter(Statement.class::isAssignableFrom)
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .filter(TestTrinoQueryProperties::hasQualifiedNameGetter)
                .collect(Collectors.toSet());

        // Fail-open guard: if the classpath scan cannot enumerate the package it returns empty and the check
        // below would pass vacuously. Requiring it to rediscover every sampled statement proves it ran.
        assertThat(withQualifiedNameGetter)
                .as("classpath scan did not enumerate io.trino.sql.tree; the drift guard would pass vacuously")
                .containsAll(sampledStatements);

        Set<Class<?>> unhandled = withQualifiedNameGetter.stream()
                .filter(clazz -> !sampledStatements.contains(clazz))
                .filter(clazz -> !QUALIFIED_NAME_NOT_AN_OBJECT.contains(clazz))
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Class::getName))));

        assertThat(unhandled)
                .as(
                        """
                        TrinoQueryProperties extracts table/catalog/schema names from each statement so routing rules \
                        can match on them. The Statement types below expose a QualifiedName but have no visitNode() \
                        case in TrinoQueryProperties, so their object name is silently dropped and the query can be \
                        misrouted.

                        This usually means dep.trino.version (gateway-ha/pom.xml) was bumped and Trino added new \
                        QualifiedName-bearing statements. For each type, add a visitNode() case in TrinoQueryProperties \
                        plus a sample query to OBJECT_NAME_STATEMENT_SAMPLES, or list it in QUALIFIED_NAME_NOT_AN_OBJECT \
                        if its QualifiedName is not a routable object.

                        Unextracted types: %s""",
                        unhandled)
                .isEmpty();
    }

    @Test
    void testCommentOnSinglePartColumnIsExtractedWithoutError()
    {
        // The parser accepts a single-part column name; it has no table prefix, so nothing is extracted and
        // extraction must not throw (it previously built an empty QualifiedName, throwing IllegalArgumentException).
        TrinoQueryProperties properties = new TrinoQueryProperties(prepareMockRequest("COMMENT ON COLUMN a IS 'x'"), false, 1024 * 1024);

        assertThat(properties.isQueryParsingSuccessful()).isTrue();
        assertThat(properties.getTables()).isEmpty();
    }

    @Test
    void testCommentOnColumnExtractsTheColumnsTable()
    {
        // COMMENT ON COLUMN a.b targets column b of table a; the table prefix a resolves with header defaults.
        ContainerRequestContext request = prepareMockRequest("COMMENT ON COLUMN a.b IS 'x'");
        when(request.getHeaderString(TRINO_CATALOG_HEADER_NAME)).thenReturn("dc");
        when(request.getHeaderString(TRINO_SCHEMA_HEADER_NAME)).thenReturn("ds");
        TrinoQueryProperties properties = new TrinoQueryProperties(request, false, 1024 * 1024);

        assertThat(properties.isQueryParsingSuccessful()).isTrue();
        assertThat(properties.getTables()).containsExactly(QualifiedName.of("dc", "ds", "a"));
    }

    private static boolean hasQualifiedNameGetter(Class<?> clazz)
    {
        return Arrays.stream(clazz.getMethods())
                .anyMatch(method -> method.getParameterCount() == 0 && method.getReturnType().equals(QualifiedName.class));
    }

    private ContainerRequestContext prepareMockRequest(String query)
    {
        return prepareMockRequest(query, MediaType.valueOf("application/json; charset=UTF-8"));
    }

    private ContainerRequestContext prepareMockRequest(String query, MediaType mediaType)
    {
        ContainerRequestContext mockRequest = mock(ContainerRequestContext.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.hasEntity()).thenReturn(true);

        when(mockRequest.getMediaType()).thenReturn(mediaType);

        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(mockRequest.getEntityStream()).thenReturn(entityStream);

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        when(mockRequest.getHeaders()).thenReturn(headers);

        return mockRequest;
    }
}
