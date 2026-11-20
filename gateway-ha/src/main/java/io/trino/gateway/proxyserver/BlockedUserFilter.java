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

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.handler.BlockedUserService;
import io.trino.gateway.ha.router.TrinoRequestUser;
import io.trino.gateway.ha.security.util.GatewayFilterPriorities;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Set;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.util.Objects.requireNonNull;

@PreMatching
@Priority(GatewayFilterPriorities.USER_BLOCKING)
public class BlockedUserFilter
        implements ContainerRequestFilter
{
    private static final Logger log = Logger.get(BlockedUserFilter.class);
    private static final String BLOCKED_USER_RESPONSE = "{\"error\":\"User is not permitted to submit queries. Contact your administrator.\"}";

    private final BlockedUserService blockedUserService;
    private final Set<String> statementPaths;

    @Context
    private HttpServletRequest servletRequest;

    @Inject
    public BlockedUserFilter(BlockedUserService blockedUserService, HaGatewayConfiguration configuration)
    {
        this.blockedUserService = requireNonNull(blockedUserService, "blockedUserService is null");
        this.statementPaths = Set.copyOf(requireNonNull(configuration.getStatementPaths(), "statementPaths is null"));
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        if (!POST.equals(requestContext.getMethod())) {
            return;
        }

        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (statementPaths.stream().noneMatch(path::startsWith)) {
            return;
        }

        Object userProperty = requestContext.getProperty(TRINO_REQUEST_USER);
        if (!(userProperty instanceof TrinoRequestUser trinoRequestUser) || trinoRequestUser.getUser().isEmpty()) {
            return;
        }

        String username = trinoRequestUser.getUser().orElseThrow();
        if (username.isBlank()) {
            return;
        }

        if (!blockedUserService.isBlocked(username)) {
            return;
        }

        log.warn("Blocked query submission for user %s from %s", username, remoteAddress());
        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(BLOCKED_USER_RESPONSE)
                .build());
    }

    private String remoteAddress()
    {
        if (servletRequest == null) {
            return "unknown";
        }
        return servletRequest.getRemoteAddr();
    }
}
