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

@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicResource
{
    @Inject
    private GatewayBackendManager gatewayBackendManager;
    @Inject
    private BackendStateManager backendStateManager;

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
        try {
            ProxyBackendConfiguration backend = gatewayBackendManager
                    .getBackendByName(name)
                    .orElseThrow();
            return Response.ok(backend).build();
        }
        catch (NoSuchElementException e) {
            return Response.status(404).build();
        }
    }

    @GET
    @Path("/backends/{name}/state")
    public Response getBackendState(@PathParam("name") String name)
    {
        try {
            BackendStateManager.BackendState state = gatewayBackendManager
                    .getBackendByName(name)
                    .map(backendStateManager::getBackendState)
                    .orElseThrow();
            return Response.ok(state.getState()).build();
        }
        catch (NoSuchElementException e) {
            return Response.status(404).build();
        }
    }
}
