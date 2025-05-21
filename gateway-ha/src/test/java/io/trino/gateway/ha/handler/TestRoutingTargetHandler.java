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
package io.trino.gateway.ha.handler;

import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.OAuth2GatewayCookieConfiguration;
import io.trino.gateway.ha.config.OAuth2GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestRoutingTargetHandler
{
    private RoutingManager routingManager;
    private RoutingGroupSelector routingGroupSelector;
    private HaGatewayConfiguration haGatewayConfiguration;
    private RoutingTargetHandler routingTargetHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp()
    {
        // Initialize the cookie configuration providers
        GatewayCookieConfigurationPropertiesProvider cookieProvider = GatewayCookieConfigurationPropertiesProvider.getInstance();
        cookieProvider.initialize(new GatewayCookieConfiguration());

        OAuth2GatewayCookieConfigurationPropertiesProvider oauthCookieProvider = OAuth2GatewayCookieConfigurationPropertiesProvider.getInstance();
        oauthCookieProvider.initialize(new OAuth2GatewayCookieConfiguration());

        routingManager = mock(RoutingManager.class);
        routingGroupSelector = mock(RoutingGroupSelector.class);
        haGatewayConfiguration = mock(HaGatewayConfiguration.class);

        // Setup configuration
        when(haGatewayConfiguration.getStatementPaths()).thenReturn(java.util.List.of("/v1/statement"));
        when(haGatewayConfiguration.getExtraWhitelistPaths()).thenReturn(java.util.List.of());
        when(haGatewayConfiguration.getRequestAnalyzerConfig()).thenReturn(mock(io.trino.gateway.ha.config.RequestAnalyzerConfig.class));

        routingTargetHandler = new RoutingTargetHandler(routingManager, routingGroupSelector, haGatewayConfiguration);

        // Setup request
        request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getRemoteHost()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURI()).thenReturn("/v1/query");
        when(request.getCookies()).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        try {
            when(request.getInputStream()).thenReturn(new jakarta.servlet.ServletInputStream()
            {
                @Override
                public boolean isFinished()
                {
                    return true;
                }

                @Override
                public boolean isReady()
                {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener)
                {
                }

                @Override
                public int read()
                            throws IOException
                {
                    return -1;
                }
            });
        }
        catch (IOException e) {
            throw new RuntimeException("Error setting up mock request", e);
        }
    }

    @Test
    void testNonExistentRoutingGroupReturns404()
    {
        // Clear all existing mocks to ensure clean test
        routingManager = mock(RoutingManager.class);
        routingGroupSelector = mock(RoutingGroupSelector.class);

        // Recreate handler with clean mocks
        routingTargetHandler = new RoutingTargetHandler(routingManager, routingGroupSelector, haGatewayConfiguration);

        // Setup behavior explicitly to ensure the NotFoundException is thrown
        when(routingGroupSelector.findRoutingGroup(any())).thenReturn("non_existent_group");
        when(routingManager.provideBackendForRoutingGroup(eq("non_existent_group"), any()))
                .thenThrow(new NotFoundException("Routing group does not exist: non_existent_group"));

        // Use assertThatThrownBy for cleaner test
        assertThatThrownBy(() -> routingTargetHandler.getRoutingDestination(request))
                .isInstanceOf(WebApplicationException.class)
                .hasFieldOrPropertyWithValue("response.status", Response.Status.NOT_FOUND.getStatusCode())
                .extracting(e -> ((WebApplicationException) e).getResponse().getEntity())
                .isEqualTo("Routing group does not exist: non_existent_group");
    }

    @Test
    void testQueryValidationErrorReturnsBadRequest()
    {
        // Setup behavior to simulate query validation errors from external API
        List<String> validationErrors = List.of(
                "MissingPartitionInQueryError: Query must include a WHERE clause when using SELECT *",
                "UnsafeJoinError: JOIN without ON clause detected");

        when(routingGroupSelector.findRoutingGroup(any()))
                .thenThrow(new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity(validationErrors)
                                .build()));

        // Verify the BAD_REQUEST WebApplicationException is thrown with the validation errors
        assertThatThrownBy(() -> routingTargetHandler.getRoutingDestination(request))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> {
                    WebApplicationException exception = (WebApplicationException) e;
                    assertThat(exception.getResponse().getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
                    assertThat(exception.getResponse().getEntity()).isEqualTo(validationErrors);
                });
    }
}
