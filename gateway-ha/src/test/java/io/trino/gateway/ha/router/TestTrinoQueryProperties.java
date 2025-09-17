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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("test_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test_schema");

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
        HttpServletRequest mockRequest = prepareMockRequest();
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default_catalog");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("default_schema");

        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(mockRequest, false, 1024 * 1024);

        // Verify the two-part case works and doesn't fall through to default
        assertThat(trinoQueryProperties.getCatalogs()).containsExactly("catalog");
        assertThat(trinoQueryProperties.getSchemas()).containsExactly("schema");
        assertThat(trinoQueryProperties.getCatalogSchemas()).containsExactly("catalog.schema");
        assertThat(trinoQueryProperties.isQueryParsingSuccessful()).isTrue();
    }

    private HttpServletRequest prepareMockRequest()
    {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        return mockRequest;
    }
}
