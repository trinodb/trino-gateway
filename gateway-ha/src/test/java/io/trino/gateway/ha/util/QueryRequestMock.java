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
package io.trino.gateway.ha.util;

import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.handler.HttpUtils;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.TrinoQueryProperties;
import io.trino.gateway.ha.router.TrinoRequestUser;
import io.trino.gateway.ha.security.QueryMetadataParser;
import io.trino.gateway.ha.security.QueryUserInfoParser;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QueryRequestMock
{
    private RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
    private ContainerRequestContext requestContext = mock(ContainerRequest.class);
    private HttpServletRequest mockRequest = mock(HttpServletRequest.class);

    private void setDefaultMockParams()
    {
        when(requestContext.getMethod()).thenReturn("POST");
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        try {
            when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + HttpUtils.V1_STATEMENT_PATH));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
    }

    public QueryRequestMock requestAnalyzerConfig(RequestAnalyzerConfig config)
    {
        requestAnalyzerConfig = config;
        return this;
    }

    public QueryRequestMock query(String query)
            throws IOException
    {
        if (!query.isEmpty()) {
            MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
            when(requestContext.getMediaType()).thenReturn(mediaType);
            InputStream entityStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
            when(requestContext.getEntityStream()).thenReturn(entityStream);
            when(requestContext.hasEntity()).thenReturn(true);
        }
        else {
            when(requestContext.hasEntity()).thenReturn(false);
        }

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(query.getBytes(UTF_8));
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        when(mockRequest.getInputStream()).thenReturn(new ServletInputStream()
        {
            @Override
            public boolean isFinished()
            {
                return byteArrayInputStream.available() > 0;
            }

            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener)
            {}

            @Override
            public int read()
                    throws IOException
            {
                return byteArrayInputStream.read();
            }

            @Override
            public int read(byte[] b, int off, int len)
                    throws IOException
            {
                return byteArrayInputStream.read(b, off, len);
            }
        });

        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(query)));
        when(mockRequest.getQueryString()).thenReturn("");
        return this;
    }

    public QueryRequestMock httpHeader(String name, String value)
    {
        when(requestContext.getHeaderString(name)).thenReturn(value);
        when(mockRequest.getHeader(name)).thenReturn(value);
        return this;
    }

    public QueryRequestMock httpHeaders(MultivaluedMap<String, String> headers)
    {
        when(requestContext.getHeaders()).thenReturn(headers);
        return this;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        setDefaultMockParams();

        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.setRequestAnalyzerConfig(requestAnalyzerConfig);

        PathFilter pathFilter = new PathFilter(config.getStatementPaths(), config.getExtraWhitelistPaths());

        QueryUserInfoParser userInfoParser = new QueryUserInfoParser(config, pathFilter);
        try {
            userInfoParser.filter(requestContext);
            ArgumentCaptor<TrinoRequestUser> captorUserInfo = ArgumentCaptor.forClass(TrinoRequestUser.class);
            verify(requestContext).setProperty(eq(TRINO_REQUEST_USER), captorUserInfo.capture());
            when(mockRequest.getAttribute(TRINO_REQUEST_USER)).thenReturn(captorUserInfo.getValue());
        }
        catch (IOException ex) {
            return null;
        }

        QueryMetadataParser queryMetadataParser = new QueryMetadataParser(config, pathFilter);
        try {
            if (requestAnalyzerConfig.isAnalyzeRequest()) {
                queryMetadataParser.filter(requestContext);
                ArgumentCaptor<TrinoQueryProperties> captor = ArgumentCaptor.forClass(TrinoQueryProperties.class);
                verify(requestContext).setProperty(eq(TRINO_QUERY_PROPERTIES), captor.capture());
                when(mockRequest.getAttribute(TRINO_QUERY_PROPERTIES)).thenReturn(captor.getValue());
            }
        }
        catch (IOException ex) {
            return null;
        }
        return mockRequest;
    }
}
