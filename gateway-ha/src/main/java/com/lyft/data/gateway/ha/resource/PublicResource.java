package com.lyft.data.gateway.ha.resource;

import com.google.inject.Inject;
import com.lyft.data.gateway.ha.config.ProxyBackendConfiguration;
import com.lyft.data.gateway.ha.router.BackendStateManager;
import com.lyft.data.gateway.ha.router.GatewayBackendManager;
import java.util.NoSuchElementException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource {
  @Inject
  private GatewayBackendManager gatewayBackendManager;
  @Inject
  private BackendStateManager backendStateManager;

  @GET
  @Path("/backends")
  public Response getAllBackends() {
    return Response.ok(this.gatewayBackendManager.getAllBackends()).build();
  }

  @GET
  @Path("/backends/{name}")
  public Response getBackend(@PathParam("name") String name) {
    try {
      ProxyBackendConfiguration backend = gatewayBackendManager
          .getBackendByName(name)
          .get();
      return Response.ok(backend).build();
    } catch (NoSuchElementException e) {
      return Response.status(404).build();
    }
  }

  @GET
  @Path("/backends/{name}/state")
  public Response getBackendState(@PathParam("name") String name) {
    try {
      BackendStateManager.BackendState state = gatewayBackendManager
          .getBackendByName(name)
          .map(backendStateManager::getBackendState)
          .get().get();
      return Response.ok(state.getState()).build();
    } catch (NoSuchElementException e) {
      return Response.status(404).build();
    }
  }
}
