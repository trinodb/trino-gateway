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

import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.schema.ExternalRouterResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestRoutingTargetHandler
{
    private RoutingManager routingManager;
    private HttpClient httpClient;
    private HttpServletRequest request;

    private RoutingTargetHandler handler;
    private HaGatewayConfiguration config;

    static HaGatewayConfiguration provideGatewayConfiguration()
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.setRequestAnalyzerConfig(new RequestAnalyzerConfig());

        config.getRouting().setDefaultRoutingGroup("default-group");

        // Configure excluded headers
        RulesExternalConfiguration rulesExternalConfig = new RulesExternalConfiguration();
        rulesExternalConfig.setExcludeHeaders(List.of("Authorization", "Cookie"));
        rulesExternalConfig.setUrlPath("http://localhost:8080/api/routing");
        config.getRoutingRules().setRulesExternalConfiguration(rulesExternalConfig);

        // Initialize cookie configuration
        GatewayCookieConfiguration cookieConfig = new GatewayCookieConfiguration();
        cookieConfig.setEnabled(false); // Disable cookies for testing
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(cookieConfig);

        return config;
    }

    private HttpServletRequest prepareMockRequest()
    {
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);
        when(mockRequest.getHeader(USER_HEADER)).thenReturn("test-user");

        // Set up header names enumeration
        List<String> headerNames = List.of(
                USER_HEADER,
                "Authorization",
                "Cookie");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.enumeration(headerNames));

        // Set up individual header values
        when(mockRequest.getHeader("Authorization")).thenReturn("secret-token");
        when(mockRequest.getHeader("Cookie")).thenReturn("session-id");

        // Set up header values enumeration for each header
        when(mockRequest.getHeaders(USER_HEADER)).thenReturn(Collections.enumeration(List.of("test-user")));
        when(mockRequest.getHeaders("Authorization")).thenReturn(Collections.enumeration(List.of("secret-token")));
        when(mockRequest.getHeaders("Cookie")).thenReturn(Collections.enumeration(List.of("session-id")));

        return mockRequest;
    }

    @BeforeAll
    void setUp()
    {
        config = provideGatewayConfiguration();
        httpClient = Mockito.mock(HttpClient.class);
        routingManager = Mockito.mock(RoutingManager.class);
        when(routingManager.provideBackendConfiguration(any(), any())).thenReturn(new ProxyBackendConfiguration());
        request = prepareMockRequest();

        // Initialize the handler with the configuration
        handler = new RoutingTargetHandler(
                routingManager,
                RoutingGroupSelector.byRoutingExternal(httpClient, config.getRoutingRules().getRulesExternalConfiguration(), config.getRequestAnalyzerConfig()), config);
    }

    @Test
    void testBasicHeaderModification()
            throws Exception
    {
        // Setup routing group selector response
        Map<String, String> modifiedHeaders = ImmutableMap.of(
                "X-Original-Header", "new-value",
                "X-New-Header", "new-value");
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                Collections.emptyList(),
                modifiedHeaders);
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify
        assertThat(response.modifiedRequest().getHeader("X-Original-Header"))
                .isEqualTo("new-value");
        assertThat(response.modifiedRequest().getHeader("X-New-Header"))
                .isEqualTo("new-value");
    }

    @Test
    void testExcludedHeaders()
            throws Exception
    {
        // Setup routing group selector response
        Map<String, String> modifiedHeaders = ImmutableMap.of(
                "Authorization", "new-token",
                "Cookie", "new-session");
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                Collections.emptyList(),
                modifiedHeaders);
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify sensitive headers are not modified
        assertThat(response.modifiedRequest().getHeader("Authorization"))
                .isEqualTo("secret-token");
        assertThat(response.modifiedRequest().getHeader("Cookie"))
                .isEqualTo("session-id");
    }

    @Test
    void testNoHeaderModification()
            throws Exception
    {
        // Setup routing group selector response with no header modifications
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                Collections.emptyList(),
                ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify original headers are preserved
        assertThat(response.modifiedRequest().getHeader("X-Original-Header"))
                .isNull();
    }

    @Test
    void testEmptyHeader()
            throws Exception
    {
        // Setup routing group selector response
        Map<String, String> modifiedHeaders = ImmutableMap.of(
                "X-Empty-Header", "",
                "X-New-Header", "new-value");
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                Collections.emptyList(),
                modifiedHeaders);
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify
        assertThat(response.modifiedRequest().getHeader("X-Empty-Header"))
                .isEmpty();
        assertThat(response.modifiedRequest().getHeader("X-New-Header"))
                .isEqualTo("new-value");
    }

    @Test
    void testEmptyRoutingGroup()
            throws Exception
    {
        // Setup routing group selector response with empty routing group
        Map<String, String> modifiedHeaders = ImmutableMap.of(
                "X-Empty-Group-Header", "should-be-set");
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "",
                Collections.emptyList(),
                modifiedHeaders);
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify that when no routing group header is set, we default to "adhoc"
        assertThat(response.routingDestination().routingGroup()).isEqualTo("default-group");
        assertThat(response.modifiedRequest().getHeader("X-Empty-Group-Header"))
                .isEqualTo("should-be-set");
    }

    @Test
    void testResponsePropertiesNull()
    {
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(null, null, ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        RoutingTargetResponse result = handler.resolveRouting(request);

        assertThat(result.routingDestination().routingGroup()).isEqualTo("default-group");
    }

    @Test
    void testResponseGroupSetResponseErrorsNull()
    {
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group", null, ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        RoutingTargetResponse result = handler.resolveRouting(request);

        assertThat(result.routingDestination().routingGroup()).isEqualTo("test-group");
    }

    @Test
    void testPropagateErrorsFalseResponseGroupNullResponseErrorsSet()
    {
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(null, List.of("some-error"), ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        RoutingTargetResponse result = handler.resolveRouting(request);

        assertThat(result.routingDestination().routingGroup()).isEqualTo("default-group");
    }

    @Test
    void testPropagateErrorsFalseResponseGroupAndErrorsSet()
    {
        ExternalRouterResponse mockResponse = new ExternalRouterResponse("test-group", List.of("some-error"), ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        RoutingTargetResponse result = handler.resolveRouting(request);

        assertThat(result.routingDestination().routingGroup()).isEqualTo("test-group");
    }

    @Test
    void testPropagateErrorsTrueResponseGroupNullResponseErrorsSet()
    {
        RoutingTargetHandler handler = createHandlerWithPropagateErrorsTrue();

        config.getRoutingRules().getRulesExternalConfiguration().setPropagateErrors(true);
        ExternalRouterResponse mockResponse = new ExternalRouterResponse(null, List.of("some-error"), ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        assertThatThrownBy(() -> handler.resolveRouting(request))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void testPropagateErrorsTrueResponseGroupAndErrorsSet()
    {
        RoutingTargetHandler handler = createHandlerWithPropagateErrorsTrue();

        ExternalRouterResponse response = new ExternalRouterResponse("test-group", List.of("some-error"), ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(response);

        assertThatThrownBy(() -> handler.resolveRouting(request))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void testRoutingDestinationContainsBackendName()
    {
        // Setup backend configuration with a specific name
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("my-cluster");
        backend.setProxyTo("http://localhost:8080");
        backend.setExternalUrl("https://trino.example.com");
        when(routingManager.provideBackendConfiguration(any(), any())).thenReturn(backend);

        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                Collections.emptyList(),
                ImmutableMap.of());
        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingTargetResponse response = handler.resolveRouting(request);

        // Verify backend name is passed through RoutingDestination
        assertThat(response.routingDestination().name()).isEqualTo("my-cluster");
    }

    private RoutingTargetHandler createHandlerWithPropagateErrorsTrue()
    {
        config.getRoutingRules().getRulesExternalConfiguration().setPropagateErrors(true);
        return new RoutingTargetHandler(
                routingManager,
                RoutingGroupSelector.byRoutingExternal(httpClient, config.getRoutingRules().getRulesExternalConfiguration(), config.getRequestAnalyzerConfig()), config);
    }
}
