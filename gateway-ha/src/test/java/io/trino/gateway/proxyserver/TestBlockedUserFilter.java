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
package io.trino.gateway.proxyserver;

import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.handler.BlockedUserService;
import io.trino.gateway.ha.router.TrinoRequestUser;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static io.trino.gateway.ha.handler.HttpUtils.V1_QUERY_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestBlockedUserFilter
{
    @Test
    void testBlocksBlockedUserOnStatementPath()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        when(blockedUserService.isBlocked("blocked_user")).thenReturn(true);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_STATEMENT_PATH, HttpMethod.POST);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of("blocked_user"), null));

        filter.filter(requestContext);

        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());
        Response response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
        assertThat(response.getEntity().toString()).contains("User is not permitted to submit queries");
    }

    @Test
    void testDoesNotBlockAllowedUserOnStatementPath()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        when(blockedUserService.isBlocked("allowed_user")).thenReturn(false);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_STATEMENT_PATH, HttpMethod.POST);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of("allowed_user"), null));

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void testDoesNotCheckBlockedUsersForNonStatementPath()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_QUERY_PATH + "/query123", HttpMethod.POST);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of("blocked_user"), null));

        filter.filter(requestContext);

        verify(blockedUserService, never()).isBlocked("blocked_user");
        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void testDoesNotAbortWhenUserIsMissing()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_STATEMENT_PATH, HttpMethod.POST);

        filter.filter(requestContext);

        verify(blockedUserService, never()).isBlocked("blocked_user");
        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void testDoesNotAbortWhenUsernameIsBlank()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_STATEMENT_PATH, HttpMethod.POST);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of(""), null));

        filter.filter(requestContext);

        verify(blockedUserService, never()).isBlocked(any());
        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void testDoesNotCheckBlockedUsersForNonPostRequests()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, new HaGatewayConfiguration());

        ContainerRequestContext requestContext = mockRequest(V1_STATEMENT_PATH + "/queued/query123/token/1", HttpMethod.GET);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of("some_user"), null));

        filter.filter(requestContext);

        verify(blockedUserService, never()).isBlocked(any());
        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void testHonorsAdditionalStatementPaths()
            throws Exception
    {
        BlockedUserService blockedUserService = mock(BlockedUserService.class);
        when(blockedUserService.isBlocked("blocked_user")).thenReturn(true);

        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setAdditionalStatementPaths(List.of("/v2/statement"));
        BlockedUserFilter filter = new BlockedUserFilter(blockedUserService, configuration);

        ContainerRequestContext requestContext = mockRequest("/v2/statement", HttpMethod.POST);
        when(requestContext.getProperty(TRINO_REQUEST_USER)).thenReturn(new TrinoRequestUser(Optional.of("blocked_user"), null));

        filter.filter(requestContext);

        verify(requestContext).abortWith(any(Response.class));
    }

    private static ContainerRequestContext mockRequest(String path, String method)
            throws Exception
    {
        ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + path));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn(method);
        return requestContext;
    }
}
