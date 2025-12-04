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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.JsonBodyGenerator;
import io.airlift.http.client.JsonResponseHandler;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.RulesExternalConfiguration;
import io.trino.gateway.ha.router.schema.ExternalRouterResponse;
import io.trino.gateway.ha.router.schema.RoutingGroupExternalBody;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;

public class ExternalRoutingGroupSelector
        implements RoutingGroupSelector
{
    private static final Logger log = Logger.get(ExternalRoutingGroupSelector.class);
    private final Set<String> excludeHeaders;
    private final URI uri;
    private final boolean propagateErrors;
    private final HttpClient httpClient;
    private final RequestAnalyzerConfig requestAnalyzerConfig;
    private static final JsonCodec<RoutingGroupExternalBody> ROUTING_GROUP_EXTERNAL_BODY_JSON_CODEC = jsonCodec(RoutingGroupExternalBody.class);
    private static final JsonResponseHandler<ExternalRouterResponse> ROUTING_GROUP_EXTERNAL_RESPONSE_JSON_RESPONSE_HANDLER =
            createJsonResponseHandler(jsonCodec(ExternalRouterResponse.class));

    @VisibleForTesting
    ExternalRoutingGroupSelector(HttpClient httpClient, RulesExternalConfiguration rulesExternalConfiguration, RequestAnalyzerConfig requestAnalyzerConfig)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.excludeHeaders = ImmutableSet.<String>builder()
                .add("Content-Length")
                .addAll(rulesExternalConfiguration.getExcludeHeaders())
                .build();
        propagateErrors = rulesExternalConfiguration.isPropagateErrors();

        this.requestAnalyzerConfig = requestAnalyzerConfig;
        try {
            this.uri = new URI(requireNonNull(rulesExternalConfiguration.getUrlPath(),
                    "Invalid URL provided, using routing group header as default."));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL provided, using "
                    + "routing group header as default.", e);
        }
    }

    @Override
    public RoutingSelectorResponse findRoutingDestination(HttpServletRequest servletRequest)
    {
        try {
            RoutingGroupExternalBody requestBody = createRequestBody(servletRequest);
            JsonBodyGenerator<RoutingGroupExternalBody> requestBodyGenerator = jsonBodyGenerator(ROUTING_GROUP_EXTERNAL_BODY_JSON_CODEC, requestBody);
            Request request = preparePost()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .addHeaders(getValidHeaders(servletRequest))
                    .setUri(uri)
                    .setBodyGenerator(requestBodyGenerator)
                    .build();

            // Execute the request and get the response
            ExternalRouterResponse response = httpClient.execute(request, ROUTING_GROUP_EXTERNAL_RESPONSE_JSON_RESPONSE_HANDLER);

            // Check the response and return the routing group
            if (response == null) {
                throw new RuntimeException("Unexpected response: null");
            }
            else if (response.errors() != null && !response.errors().isEmpty()) {
                if (propagateErrors) {
                    log.warn("Query validation failed with errors: %s", String.join(", ", response.errors()));
                    throw new WebApplicationException(
                            Response.status(Response.Status.BAD_REQUEST)
                                    .entity(response.errors())
                                    .build());
                }
            }

            // Filter out excluded headers and null values
            Map<String, String> filteredHeaders = new HashMap<>();
            if (response.externalHeaders() != null) {
                response.externalHeaders().forEach((key, value) -> {
                    if (!excludeHeaders.contains(key) && value != null) {
                        filteredHeaders.put(key, value);
                    }
                });
                // Log the headers that will be applied
                if (!filteredHeaders.isEmpty()) {
                    log.info("External routing service modified headers to: %s", filteredHeaders);
                }
            }
            return new RoutingSelectorResponse(response.routingGroup(), filteredHeaders, response.strictRouting());
        }
        catch (Exception e) {
            throwIfInstanceOf(e, WebApplicationException.class);
            log.error(e, "Error occurred while retrieving routing group "
                    + "from external routing rules processing at " + uri);
        }
        return new RoutingSelectorResponse(servletRequest.getHeader(ROUTING_GROUP_HEADER));
    }

    private RoutingGroupExternalBody createRequestBody(HttpServletRequest request)
    {
        TrinoQueryProperties trinoQueryProperties = null;
        TrinoRequestUser trinoRequestUser = null;
        if (requestAnalyzerConfig.isAnalyzeRequest()) {
            trinoQueryProperties = (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
            trinoRequestUser = (TrinoRequestUser) request.getAttribute(TRINO_REQUEST_USER);
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

    private Multimap<String, String> getValidHeaders(HttpServletRequest servletRequest)
    {
        Multimap<String, String> headers = ArrayListMultimap.create();
        for (String name : list(servletRequest.getHeaderNames())) {
            for (String value : list(servletRequest.getHeaders(name))) {
                if (!excludeHeaders.contains(name)) {
                    headers.put(name, value);
                }
            }
        }
        return headers;
    }
}
