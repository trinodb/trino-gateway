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
import io.trino.gateway.ha.handler.ProxyHandlerStats;
import io.trino.gateway.ha.handler.RoutingTargetHandler;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.security.ProxyRequestAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;

import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static io.trino.gateway.proxyserver.RouterPreMatchContainerRequestFilter.ROUTE_TO_BACKEND;
import static java.util.Objects.requireNonNull;

/**
 * Handles requests that need to be routed to a Trino backend.
 *
 * @see RouterPreMatchContainerRequestFilter
 */
@Path(ROUTE_TO_BACKEND)
public class RouteToBackendResource
{
    private final ProxyHandlerStats proxyHandlerStats;
    private final ProxyRequestHandler proxyRequestHandler;
    private final RoutingTargetHandler routingTargetHandler;
    private final ProxyRequestAuthenticator proxyRequestAuthenticator;

    @Inject
    public RouteToBackendResource(
            ProxyHandlerStats proxyHandlerStats,
            ProxyRequestHandler proxyRequestHandler,
            RoutingTargetHandler routingTargetHandler,
            ProxyRequestAuthenticator proxyRequestAuthenticator)
    {
        this.proxyHandlerStats = requireNonNull(proxyHandlerStats);
        this.proxyRequestHandler = requireNonNull(proxyRequestHandler);
        this.routingTargetHandler = requireNonNull(routingTargetHandler);
        this.proxyRequestAuthenticator = requireNonNull(proxyRequestAuthenticator);
    }

    @POST
    public void postHandler(
            String body,
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        HttpServletRequest multiReadHttpServletRequest = new MultiReadHttpServletRequest(servletRequest, body);
        if (multiReadHttpServletRequest.getRequestURI().startsWith(V1_STATEMENT_PATH)) {
            proxyHandlerStats.recordRequest();
        }
        RoutingTargetResponse result = routingTargetHandler.resolveRouting(multiReadHttpServletRequest);
        HttpServletRequest authenticatedRequest = authenticateProxyRequest(result);
        proxyRequestHandler.postRequest(body, authenticatedRequest, asyncResponse, result.routingDestination());
    }

    @GET
    public void getHandler(
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        RoutingTargetResponse result = routingTargetHandler.resolveRouting(servletRequest);
        HttpServletRequest authenticatedRequest = authenticateProxyRequest(result);
        proxyRequestHandler.getRequest(authenticatedRequest, asyncResponse, result.routingDestination());
    }

    @DELETE
    public void deleteHandler(
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        RoutingTargetResponse result = routingTargetHandler.resolveRouting(servletRequest);
        HttpServletRequest authenticatedRequest = authenticateProxyRequest(result);
        proxyRequestHandler.deleteRequest(authenticatedRequest, asyncResponse, result.routingDestination());
    }

    @PUT
    public void putHandler(
            String body,
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        RoutingTargetResponse result = routingTargetHandler.resolveRouting(new MultiReadHttpServletRequest(servletRequest, body));
        HttpServletRequest authenticatedRequest = authenticateProxyRequest(result);
        proxyRequestHandler.putRequest(body, authenticatedRequest, asyncResponse, result.routingDestination());
    }

    @HEAD
    public void headHandler(
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        RoutingTargetResponse result = routingTargetHandler.resolveRouting(servletRequest);
        HttpServletRequest authenticatedRequest = authenticateProxyRequest(result);
        proxyRequestHandler.headRequest(authenticatedRequest, asyncResponse, result.routingDestination());
    }

    private HttpServletRequest authenticateProxyRequest(RoutingTargetResponse routingTargetResponse)
    {
        return proxyRequestAuthenticator.authenticate(routingTargetResponse.modifiedRequest());
    }
}
