package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.NoSuchElementException;
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
          .get();
      return Response.ok(state.getState()).build();
    } catch (NoSuchElementException e) {
      return Response.status(404).build();
    }
  }
}
