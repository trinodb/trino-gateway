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
import io.trino.gateway.ha.router.TrinoQueryProperties;
import io.trino.gateway.ha.security.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;

/**
 *
 * This filter parses the query statement and stores the TrinoQueryProperties object as a property to be accessed
 * in the later processing
 */
@PreMatching
@Priority(Priorities.PRE_AUTHORIZATION)
public class QueryMetadataParser
        implements ContainerRequestFilter
{
    private static final Logger log = Logger.get(QueryMetadataParser.class);
    private final HaGatewayConfiguration haGatewayConfiguration;

    @Inject
    public QueryMetadataParser(HaGatewayConfiguration config)
    {
        haGatewayConfiguration = config;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        RequestAnalyzerConfig requestAnalyzerConfig = haGatewayConfiguration.getRequestAnalyzerConfig();
        if (!requestAnalyzerConfig.isAnalyzeRequest() ||
                !haGatewayConfiguration.isPathWhiteListed(path)) {
            return;
        }

        log.debug("Processing query metadata");
        //Buffer the entity (aka body of the request) for future reads during request processing
        ContainerRequest jerseyRequest = (ContainerRequest) requestContext;
        jerseyRequest.bufferEntity();

        TrinoQueryProperties queryProps = new TrinoQueryProperties(requestContext,
                requestAnalyzerConfig.isClientsUseV2Format(),
                requestAnalyzerConfig.getMaxBodySize());

        requestContext.setProperty(TRINO_QUERY_PROPERTIES, queryProps);
    }
}
