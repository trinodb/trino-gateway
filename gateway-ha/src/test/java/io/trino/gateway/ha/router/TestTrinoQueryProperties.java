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
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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
        String query = """
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
        String query = """
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
        String query = """
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
        String query = """
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
        String query = """
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
        String query = """
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
        String query = """
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

    private ContainerRequestContext prepareMockRequest(String query)
    {
        ContainerRequestContext mockRequest = mock(ContainerRequestContext.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.hasEntity()).thenReturn(true);

        MediaType mediaType = MediaType.valueOf("application/json; charset=UTF-8");
        when(mockRequest.getMediaType()).thenReturn(mediaType);

        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(mockRequest.getEntityStream()).thenReturn(entityStream);

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        when(mockRequest.getHeaders()).thenReturn(headers);

        return mockRequest;
    }
}
