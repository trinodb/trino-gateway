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
package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static java.util.Objects.requireNonNull;

@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource
{
    private final GatewayBackendManager gatewayBackendManager;
    private final BackendStateManager backendStateManager;

    @Inject
    public PublicResource(GatewayBackendManager gatewayBackendManager, BackendStateManager backendStateManager)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
    }

    @GET
    @Path("/backends")
    public Response getAllBackends()
    {
        return Response.ok(this.gatewayBackendManager.getAllBackends()).build();
    }

    @GET
    @Path("/backends/{name}")
    public Response getBackend(@PathParam("name") String name)
    {
        return gatewayBackendManager.getBackendByName(name).map(Response::ok)
                .orElseGet(() -> Response.status(404)).build();
    }

    @GET
    @Path("/backends/{name}/state")
    public Response getBackendState(@PathParam("name") String name)
    {
        return gatewayBackendManager.getBackendByName(name).map(backendStateManager::getBackendState)
                .map(state -> Response.ok(state).build())
                .orElseGet(() -> Response.status(404).build());
    }
}
