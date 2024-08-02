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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesRESTConfiguration;
import io.trino.gateway.ha.router.schema.RoutingGroupRESTApiBody;
import io.trino.gateway.ha.router.schema.RoutingGroupRESTApiResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Set;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;

public class RESTApiRoutingGroupSelector
        implements RoutingGroupSelector
{
    private static final Logger log = Logger.get(RESTApiRoutingGroupSelector.class);
    private final Set<String> blacklistHeaders;
    private final URI uri;
    private final HttpClient httpClient;
//    private final RequestAnalyzerConfig requestAnalyzerConfig;
//    private final TrinoRequestUser.TrinoRequestUserProvider trinoRequestUserProvider;
    private static final JsonCodec<RoutingGroupRESTApiBody> ROUTING_GROUP_REST_API_BODY_JSON_CODEC = jsonCodec(RoutingGroupRESTApiBody.class);
    private static final JsonResponseHandler<RoutingGroupRESTApiResponse> ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER =
            createJsonResponseHandler(jsonCodec(RoutingGroupRESTApiResponse.class));

    RESTApiRoutingGroupSelector(RulesRESTConfiguration rulesRESTConfiguration, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        Set<String> defaultBlacklistHeaders = Set.of("Content-Length");
        this.blacklistHeaders = ImmutableSet.<String>builder()
                .addAll(defaultBlacklistHeaders)
                .addAll(rulesRESTConfiguration.getBlackListHeaders())
                .build();

//        this.requestAnalyzerConfig = requestAnalyzerConfig;
//        trinoRequestUserProvider = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig);

        URI uri = null;
        try {
            requireNonNull(rulesRESTConfiguration.getUrlPath(),
                    "Invalid url provided, using routing group header as default.");
            uri = new URI(rulesRESTConfiguration.getUrlPath());
        }
        catch (Exception e) {
            throw new RuntimeException("Invalid url provided, using "
                    + "routing group header as default.", e);
        }
        this.uri = uri;
        this.httpClient = new JettyHttpClient(new HttpClientConfig());
    }

    @Override
    public String findRoutingGroup(HttpServletRequest servletRequest)
    {
        Request request;
        JsonBodyGenerator<RoutingGroupRESTApiBody> requestBodyGenerator;
        try {
            RoutingGroupRESTApiBody requestBody = createRequestBody(servletRequest);
            requestBodyGenerator = jsonBodyGenerator(ROUTING_GROUP_REST_API_BODY_JSON_CODEC, requestBody);
            request = preparePost()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .addHeaders(getValidHeaders(servletRequest))
                    .setUri(uri)
                    .setBodyGenerator(requestBodyGenerator)
                    .build();

            // Execute the request and get the response
            RoutingGroupRESTApiResponse response = httpClient.execute(request, ROUTING_GROUP_REST_API_JSON_RESPONSE_HANDLER);

            // Check the response and return the routing group
            if (response == null) {
                throw new RuntimeException("Unexpected response: null");
            }
            else if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                throw new RuntimeException("Response with error: " + String.join(", ", response.getErrors()));
            }
            return response.getRoutingGroup();
        }
        catch (Exception e) {
            log.error(e, "Error occurred while retrieving routing group "
                    + "from Routing Rules API API at " + uri);
        }
        return servletRequest.getHeader(ROUTING_GROUP_HEADER);
    }

    private RoutingGroupRESTApiBody createRequestBody(HttpServletRequest request)
    {
        // TODO: Serialize TrinoQueryProperties, TrinoRequestUser
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

    private Multimap<String, String> getValidHeaders(HttpServletRequest servletRequest)
    {
        Multimap<String, String> headers = ArrayListMultimap.create();
        for (String name : list(servletRequest.getHeaderNames())) {
            for (String value : list(servletRequest.getHeaders(name))) {
                // Add all headers to ListMultimap except those in blacklist
                if (!blacklistHeaders.contains(name)) {
                    headers.put(name, value);
                }
            }
        }
        return headers;
    }
}
