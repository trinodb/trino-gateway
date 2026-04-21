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
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static java.util.Objects.requireNonNull;

@RolesAllowed("API")
@Path("gateway/backend/modify")
@Produces(MediaType.APPLICATION_JSON)
public class HaGatewayResource
{
    private final GatewayBackendManager haGatewayManager;
    private final RoutingManager routingManager;
    private final BackendStateManager backendStateManager;

    @Inject
    public HaGatewayResource(
            GatewayBackendManager haGatewayManager,
            RoutingManager routingManager,
            BackendStateManager backendStateManager)
    {
        this.haGatewayManager = requireNonNull(haGatewayManager, "haGatewayManager is null");
        this.routingManager = requireNonNull(routingManager, "routingManager is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
    }

    @Path("/add")
    @POST
    public Response addBackend(ProxyBackendConfiguration backend)
    {
        ProxyBackendConfiguration updatedBackend = haGatewayManager.addBackend(backend);
        syncBackendHealth(updatedBackend.getName(), updatedBackend.isActive());
        return Response.ok(updatedBackend).build();
    }

    @Path("/update")
    @POST
    public Response updateBackend(ProxyBackendConfiguration backend)
    {
        ProxyBackendConfiguration updatedBackend = haGatewayManager.updateBackend(backend);
        syncBackendHealth(updatedBackend.getName(), updatedBackend.isActive());
        return Response.ok(updatedBackend).build();
    }

    @Path("/delete")
    @POST
    public Response removeBackend(String name)
    {
        ((HaGatewayManager) haGatewayManager).deleteBackend(name);
        syncBackendHealth(name, false);
        return Response.ok().build();
    }

    private void syncBackendHealth(String name, boolean active)
    {
        TrinoStatus trinoStatus = active ? TrinoStatus.PENDING : TrinoStatus.UNHEALTHY;
        routingManager.updateBackEndHealth(name, trinoStatus);
        backendStateManager.updateStates(name, ClusterStats.builder(name).trinoStatus(trinoStatus).build());
    }
}
