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
package io.trino.gateway.ha.security;

import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.handler.HttpUtils;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.TrinoQueryProperties;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestQueryMetadataParser
{
    private QueryMetadataParser filter;
    private RequestAnalyzerConfig requestAnalyzerConfig;
    private PathFilter pathFilter;

    TestQueryMetadataParser()
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        config.setRequestAnalyzerConfig(requestAnalyzerConfig);
        pathFilter = new PathFilter(config);
        filter = new QueryMetadataParser(config, pathFilter);
    }

    @Test
    void testFilterSetsTrinoQueryPropertiesWithEntityBody()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.V1_STATEMENT_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        String query = "Select xyz from cat1.schema1.table1";
        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(requestContext.getEntityStream()).thenReturn(entityStream);
        when(requestContext.hasEntity()).thenReturn(true);
        filter.filter(requestContext);

        ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
        verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
        verify((ContainerRequest) requestContext).bufferEntity();
        verify(requestContext).getEntityStream();
    }

    @Test
    void testFilterSetsTrinoQueryPropertiesWithNoV1Statement()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.OAUTH_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        String query = "Select xyz from cat1.schema1.table1";
        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(requestContext.getEntityStream()).thenReturn(entityStream);
        when(requestContext.hasEntity()).thenReturn(true);
        filter.filter(requestContext);

        ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
        verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
        verify((ContainerRequest) requestContext).bufferEntity();
        verify(requestContext).getEntityStream();
    }

    @Test
    void testFilterSetsTrinoQueryPropertiesWithNoMedia()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.V1_STATEMENT_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        String query = "Select xyz from cat1.schema1.table1";
        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(requestContext.getEntityStream()).thenReturn(entityStream);
        when(requestContext.hasEntity()).thenReturn(true);
        filter.filter(requestContext);

        ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
        verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
        TrinoQueryProperties queryProperties = (TrinoQueryProperties) requestContext.getProperty(TRINO_QUERY_PROPERTIES);
        assertThat(queryProperties).isEqualTo(null);
        verify((ContainerRequest) requestContext).bufferEntity();
    }

    @Test
    void testFilterSetsTrinoQueryPropertiesWithNoQueryText()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.V1_STATEMENT_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        String query = "";
        InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(requestContext.getEntityStream()).thenReturn(entityStream);
        when(requestContext.hasEntity()).thenReturn(true);
        filter.filter(requestContext);

        ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
        verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
        verify((ContainerRequest) requestContext).bufferEntity();
        verify(requestContext).getEntityStream();

        TrinoQueryProperties queryProperties = (TrinoQueryProperties) requestContext.getProperty(TRINO_QUERY_PROPERTIES);
        assertThat(queryProperties).isEqualTo(null);
    }

    @Test
    void testFilterSetsTrinoQueryPropertiesWithNoPostPayload()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.V1_STATEMENT_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        filter.filter(requestContext);

        ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
        verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
        verify((ContainerRequest) requestContext).bufferEntity();
    }
}
