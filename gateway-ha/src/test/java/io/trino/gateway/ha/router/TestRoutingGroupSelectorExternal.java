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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import io.trino.gateway.ha.router.schema.ExternalRouterResponse;
import io.trino.gateway.ha.router.schema.RoutingGroupExternalBody;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static io.trino.gateway.ha.router.RoutingGroupSelector.ROUTING_GROUP_HEADER;
import static io.trino.gateway.ha.router.TrinoQueryProperties.TRINO_CATALOG_HEADER_NAME;
import static io.trino.gateway.ha.router.TrinoQueryProperties.TRINO_SCHEMA_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestRoutingGroupSelectorExternal
{
    RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
    private HttpClient httpClient;
    private static final JsonResponseHandler<ExternalRouterResponse> ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER =
            createJsonResponseHandler(jsonCodec(ExternalRouterResponse.class));

    @BeforeAll
    void initialize()
            throws Exception
    {
        requestAnalyzerConfig.setAnalyzeRequest(true);
        httpClient = Mockito.mock(HttpClient.class);
    }

    static RulesExternalConfiguration provideRoutingRuleExternalConfig()
    {
        RulesExternalConfiguration restConfig = new RulesExternalConfiguration();
        restConfig.setUrlPath("http://localhost:8080/api/public/gateway_rules");
        restConfig.setExcludeHeaders(List.of("Authorization"));
        return restConfig;
    }

    @Test
    void testByRoutingRulesExternalEngine()
            throws URISyntaxException
    {
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        HttpServletRequest mockRequest = prepareMockRequest();

        // Create a mock response
        ExternalRouterResponse mockResponse = new ExternalRouterResponse("test-group", null, ImmutableMap.of());

        // Create ArgumentCaptor
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<JsonResponseHandler<ExternalRouterResponse>> handlerCaptor = ArgumentCaptor.forClass(JsonResponseHandler.class);

        // Mock the behavior of httpClient.execute
        when(httpClient.execute(requestCaptor.capture(), handlerCaptor.capture())).thenReturn(mockResponse);

        // Create a request body generator
        RoutingGroupExternalBody requestBody = createRequestBody(mockRequest);
        JsonBodyGenerator<RoutingGroupExternalBody> requestBodyGenerator = JsonBodyGenerator.jsonBodyGenerator(JsonCodec.jsonCodec(RoutingGroupExternalBody.class), requestBody);

        // Create a request
        Request request = preparePost()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setUri(new URI(rulesExternalConfiguration.getUrlPath()))  // Replace with actual URI
                .setBodyGenerator(requestBodyGenerator)
                .build();

        // Execute the request
        ExternalRouterResponse response = httpClient.execute(request, ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER);

        // Verify the response
        assertThat(response.routingGroup())
                .isEqualTo("test-group");

        // Verify that the execute method was called with the correct parameters
        verify(httpClient, times(1)).execute(any(Request.class), eq(ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER));

        // Verify the captured arguments if needed
        assertThat(request.getUri()).isEqualTo(requestCaptor.getValue().getUri());
        assertThat(ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER).isEqualTo(handlerCaptor.getValue());
    }

    @Test
    void testApiFailure()
    {
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);

        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        // Set a mock header for ROUTING_GROUP_HEADER
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn("default-group-api-failure");
        // Create a mock response that returns error in List<String>
        ExternalRouterResponse mockResponse = new ExternalRouterResponse("fail-group", List.of("test-api-failure", "400 error"), ImmutableMap.of());

        // Create ArgumentCaptor
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<JsonResponseHandler<ExternalRouterResponse>> handlerCaptor = ArgumentCaptor.forClass(JsonResponseHandler.class);

        // Mock the behavior of httpClient.execute
        when(httpClient.execute(requestCaptor.capture(), handlerCaptor.capture())).thenReturn(mockResponse);
        String routingGroup = routingGroupSelector.findRoutingDestination(mockRequest).routingGroup();

        // Verify the response
        assertThat(routingGroup).isEqualTo("default-group-api-failure");
    }

    @Test
    void testNullUri()
    {
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        rulesExternalConfiguration.setUrlPath(null);

        // Assert that a RuntimeException is thrown with message
        assertThatThrownBy(() -> RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid URL provided, using routing group header as default.");
    }

    @Test
    void testExcludeHeader()
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException
    {
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        rulesExternalConfiguration.setExcludeHeaders(List.of("test-exclude-header"));

        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);

        // Mock headers to be read by mockRequest
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        List<String> customHeaderNames = List.of("test-exclude-header", "not-excluded-header");
        List<String> customExcludeHeaderValues = List.of("test-excludeHeader-value");
        List<String> customValidHeaderValues = List.of("not-excludeHeader-value");
        Enumeration<String> headerNamesEnumeration = Collections.enumeration(customHeaderNames);
        when(mockRequest.getHeaderNames()).thenReturn(headerNamesEnumeration);
        when(mockRequest.getHeaders("test-exclude-header")).thenReturn(Collections.enumeration(customExcludeHeaderValues));
        when(mockRequest.getHeaders("not-excluded-header")).thenReturn(Collections.enumeration(customValidHeaderValues));

        // Use reflection to get valid headers after removing excludeHeaders headers
        Method getValidHeaders = ExternalRoutingGroupSelector.class.getDeclaredMethod("getValidHeaders", HttpServletRequest.class);
        getValidHeaders.setAccessible(true);

        @SuppressWarnings("unchecked")
        Multimap<String, String> validHeaders = (Multimap<String, String>) getValidHeaders.invoke(routingGroupSelector, mockRequest);
        assertThat(validHeaders.size()).isEqualTo(1);
    }

    @Test
    void testFindRoutingDestinationWithHeaderValues()
            throws Exception
    {
        // Setup
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        RoutingGroupSelector selector = RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        String header_key = "X-Header";
        String header_value = "some-value";

        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                ImmutableList.of(),
                ImmutableMap.of(header_key, header_value));

        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingSelectorResponse routingSelectorResponse = selector.findRoutingDestination(mockRequest);

        // Verify
        assertThat(routingSelectorResponse.routingGroup()).isNotNull().isEqualTo("test-group");

        // Verify header was set correctly
        assertThat(routingSelectorResponse.externalHeaders().get(header_key)).isNotNull().isEqualTo(header_value);

    }
    @Test
    void testExcludedHeaders()
            throws Exception
    {
        // Setup
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        rulesExternalConfiguration.setExcludeHeaders(ImmutableList.of("X-Custom-Header"));
        RoutingGroupSelector selector = RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        String allowed_header_key = "X-Header";
        String allowed_header_value = "should-be-set";
        String excluded_header_key = "X-Custom-Header";
        String excluded_header_value = "excluded-value";


        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                ImmutableList.of(),
                ImmutableMap.of(
                        allowed_header_key, allowed_header_value,
                        excluded_header_key, excluded_header_value));

        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingSelectorResponse routingSelectorResponse = selector.findRoutingDestination(mockRequest);

        // Verify
        assertThat(routingSelectorResponse.routingGroup()).isNotNull().isEqualTo("test-group");
        assertThat(routingSelectorResponse.externalHeaders().get(allowed_header_key)).isNotNull().isEqualTo(allowed_header_value);
        assertThat(routingSelectorResponse.externalHeaders().get(excluded_header_key)).isNull();
    }
    @Test
    void testHeaderModificationWithErrors()
            throws Exception
    {
        // Setup
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        RoutingGroupSelector selector = RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        String header_key = "X-Header";
        String header_value = "should-be-null";

        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "test-group",
                ImmutableList.of("Error occurred"),
                ImmutableMap.of(header_key, header_value));

        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingSelectorResponse routingSelectorResponse = selector.findRoutingDestination(mockRequest);

        // Verify
        assertThat(routingSelectorResponse.routingGroup()).isNull();
        assertThat(routingSelectorResponse.externalHeaders().get(header_key)).isNull();
    }

    @Test
    void testHeaderModificationWithEmptyRoutingGroup()
            throws Exception
    {
        // Setup
        RulesExternalConfiguration rulesExternalConfiguration = provideRoutingRuleExternalConfig();
        RoutingGroupSelector selector = RoutingGroupSelector.byRoutingExternal(httpClient, rulesExternalConfiguration, requestAnalyzerConfig);
        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        String header_key = "X-Empty-Group-Header";
        String header_value = "should-be-set";

        ExternalRouterResponse mockResponse = new ExternalRouterResponse(
                "",
                ImmutableList.of(),
                ImmutableMap.of(header_key, header_value));

        when(httpClient.execute(any(), any())).thenReturn(mockResponse);

        // Execute
        RoutingSelectorResponse routingSelectorResponse = selector.findRoutingDestination(mockRequest);

        // Verify
        assertThat(routingSelectorResponse.routingGroup()).isEmpty();
        assertThat(routingSelectorResponse.externalHeaders().get(header_key)).isNotNull().isEqualTo(header_value);
    }

    private HttpServletRequest prepareMockRequest()
    {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        return mockRequest;
    }

    private void setMockHeaders(HttpServletRequest mockRequest)
    {
        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default");
        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test");
        when(mockRequest.getHeader(USER_HEADER)).thenReturn("user");

        List<String> defaultHeaderNames = List.of("Accept-Encoding");
        List<String> defaultAcceptEncodingValues = List.of("gzip", "deflate", "br");
        Enumeration<String> headerNamesEnumeration = Collections.enumeration(defaultHeaderNames);

        when(mockRequest.getHeaderNames()).thenReturn(headerNamesEnumeration);
        for (String name : defaultHeaderNames) {
            when(mockRequest.getHeaders(name)).thenReturn(Collections.enumeration(defaultAcceptEncodingValues));
        }
    }

    private RoutingGroupExternalBody createRequestBody(HttpServletRequest request)
    {
        TrinoQueryProperties trinoQueryProperties = null;
        TrinoRequestUser trinoRequestUser = null;
        if (requestAnalyzerConfig.isAnalyzeRequest()) {
            trinoQueryProperties = new TrinoQueryProperties(
                    request,
                    requestAnalyzerConfig.isClientsUseV2Format(),
                    requestAnalyzerConfig.getMaxBodySize());
            trinoRequestUser = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig).getInstance(request);
        }

        return new RoutingGroupExternalBody(
                Optional.ofNullable(trinoQueryProperties),
                Optional.ofNullable(trinoRequestUser),
                "application/json",
                request.getRemoteUser(),
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getSession(false),
                request.getRemoteAddr(),
                request.getRemoteHost(),
                request.getParameterMap());
    }
}


