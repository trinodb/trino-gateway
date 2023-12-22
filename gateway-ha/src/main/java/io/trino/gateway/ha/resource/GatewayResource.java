package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RolesAllowed({"API"})
@Path("/gateway")
@Produces(MediaType.APPLICATION_JSON)
public class GatewayResource
{
    private static final Logger log = LoggerFactory.getLogger(GatewayResource.class);
    @Inject
    private GatewayBackendManager gatewayBackendManager;

    @GET
    public Response ok(@Context Request request)
    {
        return Response.ok("ok").build();
    }

    @GET
    @Path("/backend/all")
    public Response getAllBackends()
    {
        return Response.ok(this.gatewayBackendManager.getAllBackends()).build();
    }

    @GET
    @Path("/backend/active")
    public Response getActiveBackends()
    {
        return Response.ok(gatewayBackendManager.getAllActiveBackends()).build();
    }

    @POST
    @Path("/backend/deactivate/{name}")
    public Response deactivateBackend(@PathParam("name") String name)
    {
        try {
            this.gatewayBackendManager.deactivateBackend(name);
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            return throwError(e);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/backend/activate/{name}")
    public Response activateBackend(@PathParam("name") String name)
    {
        try {
            this.gatewayBackendManager.activateBackend(name);
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            return throwError(e);
        }
        return Response.ok().build();
    }

    private Response throwError(Exception e)
    {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(e.getMessage())
                .type("text/plain")
                .build();
    }
}
