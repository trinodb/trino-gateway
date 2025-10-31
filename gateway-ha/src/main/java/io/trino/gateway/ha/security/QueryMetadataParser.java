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

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.TrinoQueryProperties;
import io.trino.gateway.ha.security.util.GatewayFilterPriorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;

/**
 *
 * This filter parses the query statement and stores the TrinoQueryProperties object
 * as a property to be accessed in the later processing
 */
@PreMatching
@Priority(GatewayFilterPriorities.PRE_AUTHORIZATION)
public class QueryMetadataParser
        implements ContainerRequestFilter
{
    private static final Logger log = Logger.get(QueryMetadataParser.class);
    private static final int MAX_QUERY_TEXT_LOG_LENGTH = 100;
    private final boolean isAnalyzeRequest;
    private final boolean isClientsUseV2Format;
    private final int maxBodySize;
    private final PathFilter pathFilter;

    @Inject
    public QueryMetadataParser(HaGatewayConfiguration config, PathFilter pathFilter)
    {
        RequestAnalyzerConfig analyzerConfig = config.getRequestAnalyzerConfig();
        this.isAnalyzeRequest = analyzerConfig.isAnalyzeRequest();
        this.isClientsUseV2Format = analyzerConfig.isClientsUseV2Format();
        this.maxBodySize = analyzerConfig.getMaxBodySize();
        this.pathFilter = pathFilter;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (path == null || !isAnalyzeRequest || !pathFilter.isPathWhiteListed(path)) {
            return;
        }

        log.debug("Processing query metadata for path: %s", path);
        // Buffer the entity (aka body of the request) for future reads during request processing
        ContainerRequest jerseyRequest = (ContainerRequest) requestContext;
        jerseyRequest.bufferEntity();

        TrinoQueryProperties queryProps;
        try {
            queryProps = new TrinoQueryProperties(requestContext, isClientsUseV2Format, maxBodySize);
        }
        catch (Exception ex) {
            log.warn(ex, "Failed to parse query properties for query text: [%s]. Error: %s. Using empty properties.",
                    getQueryTextForLogging(requestContext), ex.getMessage());
            queryProps = new TrinoQueryProperties();
        }

        requestContext.setProperty(TRINO_QUERY_PROPERTIES, queryProps);
    }

    private String getQueryTextForLogging(ContainerRequestContext requestContext)
    {
        try {
            ContainerRequest jerseyRequest = (ContainerRequest) requestContext;

            String body = jerseyRequest.readEntity(String.class);
            if (body == null || body.isEmpty()) {
                return "<empty>";
            }
            else if (body.length() > MAX_QUERY_TEXT_LOG_LENGTH) {
                return body.substring(0, MAX_QUERY_TEXT_LOG_LENGTH) + "...";
            }

            return body;
        }

        catch (Exception e) {
            log.error(e, "unable to read query text");
            return "<error reading query>";
        }
    }
}
