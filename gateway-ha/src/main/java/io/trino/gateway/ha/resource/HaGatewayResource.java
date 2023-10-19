package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RolesAllowed({"API"})
@Path("gateway/backend/modify")
@Produces(MediaType.APPLICATION_JSON)
public class HaGatewayResource {

  @Inject
  private GatewayBackendManager haGatewayManager;

  @Path("/add")
  @POST
  public Response addBackend(ProxyBackendConfiguration backend) {
    ProxyBackendConfiguration updatedBackend = haGatewayManager.addBackend(backend);
    return Response.ok(updatedBackend).build();
  }

  @Path("/update")
  @POST
  public Response updateBackend(ProxyBackendConfiguration backend) {
    ProxyBackendConfiguration updatedBackend = haGatewayManager.updateBackend(backend);
    return Response.ok(updatedBackend).build();
  }

  @Path("/delete")
  @POST
  public Response removeBackend(String name) {
    ((HaGatewayManager) haGatewayManager).deleteBackend(name);
    return Response.ok().build();
  }
}
