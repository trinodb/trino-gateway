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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoHealthStateType;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static java.util.Objects.requireNonNull;

@RolesAllowed("ADMIN")
@Path("entity")
public class EntityEditorResource
{
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.get(EntityEditorResource.class);

    private final GatewayBackendManager gatewayBackendManager;
    private final ResourceGroupsManager resourceGroupsManager;
    private final RoutingManager routingManager;
    private final BackendStateManager backendStateManager;

    @Inject
    public EntityEditorResource(GatewayBackendManager gatewayBackendManager, ResourceGroupsManager resourceGroupsManager, RoutingManager routingManager, BackendStateManager backendStateManager)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.resourceGroupsManager = requireNonNull(resourceGroupsManager, "resourceGroupsManager is null");
        this.routingManager = requireNonNull(routingManager, "routingManager is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public List<EntityType> getAllEntityTypes()
    {
        return Arrays.asList(EntityType.values());
    }

    @POST
    public Response updateEntity(
            @QueryParam("entityType") String entityTypeStr,
            @QueryParam("useSchema") String database,
            String jsonPayload)
    {
        if (Strings.isNullOrEmpty(entityTypeStr)) {
            throw new WebApplicationException("EntryType can not be null");
        }
        EntityType entityType = EntityType.valueOf(entityTypeStr);
        try {
            switch (entityType) {
                case GATEWAY_BACKEND:
                    //TODO: make the gateway backend database sensitive
                    ProxyBackendConfiguration backend =
                            OBJECT_MAPPER.readValue(jsonPayload, ProxyBackendConfiguration.class);
                    gatewayBackendManager.updateBackend(backend);
                    log.info("Setting up the backend %s with healthy state", backend.getName());
                    routingManager.updateBackEndHealth(backend.getName(), backend.isActive());
                    backendStateManager.updateStates(
                            backend.getName(),
                            ClusterStats.builder(backend.getName())
                                    .healthy(backend.isActive() ? TrinoHealthStateType.PENDING : TrinoHealthStateType.UNHEALTHY)
                                    .build());
                    break;
                case RESOURCE_GROUP:
                    ResourceGroupsDetail resourceGroupDetails = OBJECT_MAPPER.readValue(jsonPayload,
                            ResourceGroupsDetail.class);
                    resourceGroupsManager.updateResourceGroup(resourceGroupDetails, database);
                    break;
                case SELECTOR:
                    SelectorsDetail selectorDetails = OBJECT_MAPPER.readValue(jsonPayload,
                            SelectorsDetail.class);
                    List<SelectorsDetail> oldSelectorDetails =
                            resourceGroupsManager.readSelector(selectorDetails.getResourceGroupId(), database);
                    if (oldSelectorDetails.size() >= 1) {
                        resourceGroupsManager.updateSelector(oldSelectorDetails.get(0),
                                selectorDetails, database);
                    }
                    else {
                        resourceGroupsManager.createSelector(selectorDetails, database);
                    }
                    break;
                default:
            }
        }
        catch (IOException e) {
            log.error(e, e.getMessage());
            throw new WebApplicationException(e);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/{entityType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEntitiesForType(
            @PathParam("entityType") String entityTypeStr,
            @QueryParam("useSchema") String database)
    {
        EntityType entityType = EntityType.valueOf(entityTypeStr);

        switch (entityType) {
            case GATEWAY_BACKEND:
                return Response.ok(gatewayBackendManager.getAllBackends()).build();
            case RESOURCE_GROUP:
                return Response.ok(resourceGroupsManager.readAllResourceGroups(database)).build();
            case SELECTOR:
                return Response.ok(resourceGroupsManager.readAllSelectors(database)).build();
            default:
        }
        return Response.ok(ImmutableList.of()).build();
    }
}
