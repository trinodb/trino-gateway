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
import io.trino.gateway.ha.router.TrinoRequestUser;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Base64;

import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestQueryUserInfoParser
{
    private QueryUserInfoParser filter;

    TestQueryUserInfoParser()
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        config.setRequestAnalyzerConfig(requestAnalyzerConfig);

        PathFilter pathFilter = new PathFilter(config);

        filter = new QueryUserInfoParser(config, pathFilter);
    }

    @Test
    void testFilterSetsQueryUserInfo()
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + V1_STATEMENT_PATH));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        String encodedUsernamePassword = Base64.getEncoder().encodeToString("MrXYZ:OutInTheOpen".getBytes(UTF_8));
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + encodedUsernamePassword);

        filter.filter(requestContext);

        ArgumentCaptor<TrinoRequestUser> userCaptor = ArgumentCaptor.forClass(TrinoRequestUser.class);
        verify(requestContext).setProperty(eq(HttpUtils.TRINO_REQUEST_USER), userCaptor.capture());
    }
}
