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

import com.google.common.collect.Multimap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesRESTConfiguration;
import io.trino.gateway.ha.router.schema.RoutingGroupRESTApiBody;
import io.trino.gateway.ha.router.schema.RoutingGroupRESTApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.gateway.ha.router.RoutingGroupSelector.ROUTING_GROUP_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestRoutingGroupSelectorRestAPI
{
    RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
    Field fHttpClient;
    private static final JsonResponseHandler<RoutingGroupRESTApiResponse> ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER =
            createJsonResponseHandler(jsonCodec(RoutingGroupRESTApiResponse.class));

    @BeforeAll
    void initialize()
            throws Exception
    {
        requestAnalyzerConfig.setAnalyzeRequest(true);
        fHttpClient = RESTApiRoutingGroupSelector.class.getDeclaredField("httpClient");
        fHttpClient.setAccessible(true);
    }

    static Stream<RulesRESTConfiguration> provideRoutingRuleRestConfig()
    {
        RulesRESTConfiguration restConfig = new RulesRESTConfiguration();
        restConfig.setUrlPath("http://localhost:8080/api/public/gateway_rules");
        restConfig.setBlackListHeaders(new ArrayList<>(List.of("Authorization")));
        return Stream.of(restConfig);
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleRestConfig")
    void testByRoutingRulesRESTEngine(RulesRESTConfiguration rulesRESTConfiguration)
            throws URISyntaxException, IllegalAccessException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRestfulApi(rulesRESTConfiguration, requestAnalyzerConfig);

        // Use reflection to mock the HttpClient used within RoutingGroupSelector class
        fHttpClient.set(routingGroupSelector, mock(HttpClient.class));

        HttpServletRequest mockRequest = prepareMockRequest();

        // Create a mock response
        RoutingGroupRESTApiResponse mockResponse = new RoutingGroupRESTApiResponse("test-group", null);

        // Create ArgumentCaptor
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        ArgumentCaptor<JsonResponseHandler> handlerCaptor = ArgumentCaptor.forClass(JsonResponseHandler.class);

        // Mock the behavior of httpClient.execute
        when(((HttpClient) fHttpClient.get(routingGroupSelector)).execute(requestCaptor.capture(), handlerCaptor.capture())).thenReturn(mockResponse);

        // Create a request body generator
        RoutingGroupRESTApiBody requestBody = createRequestBody(mockRequest);
        JsonBodyGenerator<RoutingGroupRESTApiBody> requestBodyGenerator = JsonBodyGenerator.jsonBodyGenerator(JsonCodec.jsonCodec(RoutingGroupRESTApiBody.class), requestBody);

        // Create a request
        Request request = preparePost()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setUri(new URI(rulesRESTConfiguration.getUrlPath()))  // Replace with actual URI
                .setBodyGenerator(requestBodyGenerator)
                .build();

        // Execute the request
        RoutingGroupRESTApiResponse response = ((HttpClient) fHttpClient.get(routingGroupSelector)).execute(request, ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER);

        // Verify the response
        assertThat(response.getRoutingGroup())
                .isEqualTo("test-group");

        // Verify that the execute method was called with the correct parameters
        verify((HttpClient) fHttpClient.get(routingGroupSelector), times(1)).execute(any(Request.class), eq(ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER));

        // Verify the captured arguments if needed
        assertThat(request.getUri()).isEqualTo(requestCaptor.getValue().getUri());
        assertThat(ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER).isEqualTo(handlerCaptor.getValue());
    }

    @ParameterizedTest
    @MethodSource("provideRoutingRuleRestConfig")
    void testRESTApiFailure(RulesRESTConfiguration rulesRESTConfiguration)
            throws IllegalAccessException, URISyntaxException
    {
        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRestfulApi(rulesRESTConfiguration, requestAnalyzerConfig);

        // Use reflection to mock the HttpClient used within RoutingGroupSelector class
        fHttpClient.set(routingGroupSelector, mock(HttpClient.class));

        HttpServletRequest mockRequest = prepareMockRequest();
        setMockHeaders(mockRequest);
        // Set a mock header for ROUTING_GROUP_HEADER
        when(mockRequest.getHeader(ROUTING_GROUP_HEADER)).thenReturn("default-group-api-failure");
        // Create a mock response that returns error in List<String>
        RoutingGroupRESTApiResponse mockResponse = new RoutingGroupRESTApiResponse("fail-group", List.of("test-api-failure", "400 error"));

        // Create ArgumentCaptor
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        ArgumentCaptor<JsonResponseHandler> handlerCaptor = ArgumentCaptor.forClass(JsonResponseHandler.class);

        // Mock the behavior of httpClient.execute
        when(((HttpClient) fHttpClient.get(routingGroupSelector)).execute(requestCaptor.capture(), handlerCaptor.capture())).thenReturn(mockResponse);

        // Verify the response
        assertThat(routingGroupSelector.findRoutingGroup(mockRequest))
                .isEqualTo("default-group-api-failure");
    }

    @Test
    void testNullUri()
    {
        RulesRESTConfiguration restConfig = new RulesRESTConfiguration();
        restConfig.setBlackListHeaders(new ArrayList<>(List.of("Authorization")));

        // Assert that a RuntimeException is thrown with message
        assertThatThrownBy(() -> RoutingGroupSelector.byRoutingRestfulApi(restConfig, requestAnalyzerConfig))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid url provided, using routing group header as default.");
    }

    @Test
    void testBlackListHeader()
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException
    {
        // set custom RulesRESTConfiguration config
        RulesRESTConfiguration restConfig = new RulesRESTConfiguration();
        restConfig.setUrlPath("http://localhost:8080/api/public/gateway_rules");
        restConfig.setBlackListHeaders(new ArrayList<>(List.of("test-blackList-header")));

        RoutingGroupSelector routingGroupSelector =
                RoutingGroupSelector.byRoutingRestfulApi(restConfig, requestAnalyzerConfig);

        // Mock headers to be read by mockRequest
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        List<String> customHeaderNames = List.of("test-blackList-header", "not-blacklisted-header");
        List<String> customBlackListHeaderValues = List.of("test-blacklist-value");
        List<String> customValidHeaderValues = List.of("not-blacklist-value");
        Enumeration<String> headerNamesEnumeration = Collections.enumeration(customHeaderNames);
        when(mockRequest.getHeaderNames()).thenReturn(headerNamesEnumeration);
        when(mockRequest.getHeaders("test-blackList-header")).thenReturn(Collections.enumeration(customBlackListHeaderValues));
        when(mockRequest.getHeaders("not-blacklisted-header")).thenReturn(Collections.enumeration(customValidHeaderValues));

        // Use reflection to get valid headers after removing blacklist headers
        Method getValidHeaders = RESTApiRoutingGroupSelector.class.getDeclaredMethod("getValidHeaders", HttpServletRequest.class);
        getValidHeaders.setAccessible(true);

        @SuppressWarnings("unchecked")
        Multimap<String, String> validHeaders = (Multimap<String, String>) getValidHeaders.invoke(routingGroupSelector, mockRequest);
        assertThat(validHeaders.size()).isEqualTo(1);
    }

    private HttpServletRequest prepareMockRequest()
    {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
        return mockRequest;
    }

    private void setMockHeaders(HttpServletRequest mockRequest)
    {
//        when(mockRequest.getHeader(TRINO_CATALOG_HEADER_NAME)).thenReturn("default");
//        when(mockRequest.getHeader(TRINO_SCHEMA_HEADER_NAME)).thenReturn("test");
//        when(mockRequest.getHeader(USER_HEADER)).thenReturn("user");

        List<String> defaultHeaderNames = List.of("Accept-Encoding");
        List<String> defaultAcceptEncodingValues = Arrays.asList("gzip", "deflate", "br");
        Enumeration<String> headerNamesEnumeration = Collections.enumeration(defaultHeaderNames);

        when(mockRequest.getHeaderNames()).thenReturn(headerNamesEnumeration);
        for (String name : defaultHeaderNames) {
            when(mockRequest.getHeaders(name)).thenReturn(Collections.enumeration(defaultAcceptEncodingValues));
        }
    }

    private RoutingGroupRESTApiBody createRequestBody(HttpServletRequest request)
    {
//        TrinoQueryProperties trinoQueryProperties = null;
//        TrinoRequestUser trinoRequestUser = null;
//        if (requestAnalyzerConfig.isAnalyzeRequest()) {
//            trinoQueryProperties = new TrinoQueryProperties(request, requestAnalyzerConfig);
//            trinoRequestUser = trinoRequestUserProvider.getInstance(request);
//        }

        return new RoutingGroupRESTApiBody(
//                Optional.ofNullable(trinoQueryProperties),
//                Optional.ofNullable(trinoRequestUser),
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
