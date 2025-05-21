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
import io.trino.gateway.ha.handler.ProxyHandlerStats;
import io.trino.gateway.ha.handler.RoutingTargetHandler;
import io.trino.gateway.ha.router.ExternalRoutingError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.net.URI;

import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static io.trino.gateway.proxyserver.RouterPreMatchContainerRequestFilter.ROUTE_TO_BACKEND;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.util.Objects.requireNonNull;

/**
 * Handles requests that need to be routed to a Trino backend.
 *
 * @see RouterPreMatchContainerRequestFilter
 */
@Path(ROUTE_TO_BACKEND)
public class RouteToBackendResource
{
    private static final Logger log = Logger.get(RouteToBackendResource.class);
    private final ProxyHandlerStats proxyHandlerStats;
    private final ProxyRequestHandler proxyRequestHandler;
    private final RoutingTargetHandler routingTargetHandler;

    @Inject
    public RouteToBackendResource(
            ProxyHandlerStats proxyHandlerStats,
            ProxyRequestHandler proxyRequestHandler,
            RoutingTargetHandler routingTargetHandler)
    {
        this.proxyHandlerStats = requireNonNull(proxyHandlerStats);
        this.proxyRequestHandler = requireNonNull(proxyRequestHandler);
        this.routingTargetHandler = requireNonNull(routingTargetHandler);
    }

    @POST
    public void postHandler(
            String body,
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        try {
            MultiReadHttpServletRequest multiReadHttpServletRequest = new MultiReadHttpServletRequest(servletRequest, body);
            if (multiReadHttpServletRequest.getRequestURI().startsWith(V1_STATEMENT_PATH)) {
                proxyHandlerStats.recordRequest();
            }
            String remoteUri = routingTargetHandler.getRoutingDestination(multiReadHttpServletRequest);
            proxyRequestHandler.postRequest(body, multiReadHttpServletRequest, asyncResponse, URI.create(remoteUri));
        }
        catch (ExternalRoutingError e) {
            // Handle external routing API errors with 500 status
            log.warn("External routing API error: %s", e.getMessage());
            asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build());
        }
        catch (IllegalArgumentException e) {
            // Check if this is a query validation error
            if (e.getMessage() != null && e.getMessage().contains("Query validation failed")) {
                log.warn("Rejecting query due to validation failure: %s", e.getMessage());
                // Make sure the response has the correct content type for JSON
                asyncResponse.resume(Response.status(Response.Status.BAD_REQUEST)
                        .type("application/json")
                        .entity("{\"error\": {\"message\": \"" + e.getMessage() + "\", \"errorCode\": 1, \"errorName\": \"VALIDATION_ERROR\", \"errorType\": \"USER_ERROR\"}}")
                        .build());
            }
            else {
                // Regular routing error - return 404
                log.warn("Routing error: %s", e.getMessage());
                asyncResponse.resume(Response.status(Response.Status.NOT_FOUND)
                        .entity(e.getMessage())
                        .build());
            }
        }
        catch (Exception e) {
            log.error(e, "Error handling request");
            
            // Check if this is an external API error
            if (e.getMessage() != null && e.getMessage().startsWith("EXTERNAL_API_ERROR:")) {
                String errorMessage = e.getMessage().substring("EXTERNAL_API_ERROR:".length());
                log.warn("External API error: %s", errorMessage);
                asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(errorMessage)
                        .build());
                return;
            }
            
            asyncResponse.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Internal server error: " + e.getMessage())
                    .build());
        }
    }

    @GET
    public void getHandler(
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        try {
            String remoteUri = routingTargetHandler.getRoutingDestination(servletRequest);
            proxyRequestHandler.getRequest(servletRequest, asyncResponse, URI.create(remoteUri));
        }
        catch (IllegalArgumentException e) {
            asyncResponse.resume(Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build());
        }
    }

    @PUT
    public void putHandler(
            String body,
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        try {
            MultiReadHttpServletRequest multiReadHttpServletRequest = new MultiReadHttpServletRequest(servletRequest, body);
            String remoteUri = routingTargetHandler.getRoutingDestination(multiReadHttpServletRequest);
            proxyRequestHandler.putRequest(body, multiReadHttpServletRequest, asyncResponse, URI.create(remoteUri));
        }
        catch (IllegalArgumentException e) {
            asyncResponse.resume(Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build());
        }
    }

    @DELETE
    public void deleteHandler(
            @Context HttpServletRequest servletRequest,
            @Suspended AsyncResponse asyncResponse)
    {
        try {
            String remoteUri = routingTargetHandler.getRoutingDestination(servletRequest);
            proxyRequestHandler.deleteRequest(servletRequest, asyncResponse, URI.create(remoteUri));
        }
        catch (IllegalArgumentException e) {
            asyncResponse.resume(Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build());
        }
    }
}
