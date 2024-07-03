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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import jakarta.annotation.security.RolesAllowed;
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
import java.util.List;

import static io.trino.gateway.ha.router.ResourceGroupsManager.GlobalPropertiesDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static java.util.Objects.requireNonNull;

@RolesAllowed("USER")
@Path("/trino")
@Produces(MediaType.APPLICATION_JSON)
public class TrinoResource
{
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = Logger.get(TrinoResource.class);

    private final ResourceGroupsManager resourceGroupsManager;

    @Inject
    public TrinoResource(ResourceGroupsManager resourceGroupsManager)
    {
        this.resourceGroupsManager = requireNonNull(resourceGroupsManager, "resourceGroupsManager is null");
    }

    @POST
    @Path("/resourcegroup/create")
    public Response createResourceGroup(
            @QueryParam("useSchema") String useSchema,
            String jsonPayload)
    {
        try {
            ResourceGroupsDetail resourceGroup =
                    OBJECT_MAPPER.readValue(jsonPayload, ResourceGroupsDetail.class);
            ResourceGroupsDetail newResourceGroup =
                    this.resourceGroupsManager.createResourceGroup(resourceGroup, useSchema);
            return Response.ok(newResourceGroup).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("/resourcegroup/read")
    public Response readAllResourceGroups(@QueryParam("useSchema") String useSchema)
    {
        return Response.ok(this.resourceGroupsManager.readAllResourceGroups(
                useSchema)).build();
    }

    @GET
    @Path("/resourcegroup/read/{resourceGroupId}")
    public Response readResourceGroup(
            @PathParam("resourceGroupId") String resourceGroupIdStr,
            @QueryParam("useSchema") String useSchema)
    {
        long resourceGroupId = Long.parseLong(resourceGroupIdStr);
        List<ResourceGroupsDetail> resourceGroup =
                this.resourceGroupsManager.readResourceGroup(resourceGroupId, useSchema);
        return Response.ok(resourceGroup).build();
    }

    @POST
    @Path("/resourcegroup/update")
    public Response updateResourceGroup(
            String jsonPayload,
            @QueryParam("useSchema") String useSchema)
    {
        try {
            ResourceGroupsDetail resourceGroup =
                    OBJECT_MAPPER.readValue(jsonPayload, ResourceGroupsDetail.class);
            ResourceGroupsDetail updatedResourceGroup =
                    this.resourceGroupsManager.updateResourceGroup(resourceGroup, useSchema);
            return Response.ok(updatedResourceGroup).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @POST
    @Path("/resourcegroup/delete/{resourceGroupId}")
    public Response deleteResourceGroup(
            @PathParam("resourceGroupId") String resourceGroupIdStr,
            @QueryParam("useSchema") String useSchema)
    {
        long resourceGroupId = Long.parseLong(resourceGroupIdStr);
        resourceGroupsManager.deleteResourceGroup(resourceGroupId, useSchema);
        return Response.ok().build();
    }

    @POST
    @Path("/selector/create")
    public Response createSelector(
            String jsonPayload,
            @QueryParam("useSchema") String useSchema)
    {
        try {
            SelectorsDetail selector = OBJECT_MAPPER.readValue(jsonPayload, SelectorsDetail.class);
            SelectorsDetail updatedSelector = this.resourceGroupsManager.createSelector(selector,
                    useSchema);
            return Response.ok(updatedSelector).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("/selector/read")
    public Response readAllSelectors(@QueryParam("useSchema") String useSchema)
    {
        return Response.ok(this.resourceGroupsManager.readAllSelectors(useSchema)).build();
    }

    @GET
    @Path("/selector/read/{resourceGroupId}")
    public Response readSelector(
            @PathParam("resourceGroupId") String resourceGroupIdStr,
            @QueryParam("useSchema") String useSchema)
    {
        long resourceGroupId = Long.parseLong(resourceGroupIdStr);
        List<SelectorsDetail> selectors = this.resourceGroupsManager.readSelector(resourceGroupId,
                useSchema);
        return Response.ok(selectors).build();
    }

    @POST
    @Path("/selector/update")
    public Response updateSelector(
            String jsonPayload,
            @QueryParam("useSchema") String useSchema)
    {
        try {
            JsonNode selectors = OBJECT_MAPPER.readValue(jsonPayload, JsonNode.class);
            SelectorsDetail selector =
                    OBJECT_MAPPER.readValue(selectors.get("current").toString(), SelectorsDetail.class);
            SelectorsDetail newSelector =
                    OBJECT_MAPPER.readValue(selectors.get("update").toString(), SelectorsDetail.class);

            SelectorsDetail updatedSelector =
                    this.resourceGroupsManager.updateSelector(selector, newSelector, useSchema);
            return Response.ok(updatedSelector).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @POST
    @Path("/selector/delete/")
    public Response deleteSelector(String jsonPayload,
            @QueryParam("useSchema")
            String useSchema)
    {
        if (Strings.isNullOrEmpty(jsonPayload)) {
            throw new WebApplicationException("EntryType can not be null");
        }
        try {
            SelectorsDetail selector = OBJECT_MAPPER.readValue(jsonPayload, SelectorsDetail.class);
            resourceGroupsManager.deleteSelector(selector, useSchema);
        }
        catch (IOException e) {
            log.error(e);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/globalproperty/create")
    public Response createGlobalProperty(
            String jsonPayload,
            @QueryParam("useSchema")
            String useSchema)
    {
        try {
            GlobalPropertiesDetail globalProperty =
                    OBJECT_MAPPER.readValue(jsonPayload, GlobalPropertiesDetail.class);
            GlobalPropertiesDetail newGlobalProperty =
                    this.resourceGroupsManager.createGlobalProperty(globalProperty, useSchema);
            return Response.ok(newGlobalProperty).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("/globalproperty/read")
    public Response readAllGlobalProperties(
            @QueryParam("useSchema") String useSchema)
    {
        return Response.ok(this.resourceGroupsManager.readAllGlobalProperties(useSchema))
                .build();
    }

    @GET
    @Path("/globalproperty/read/{name}")
    public Response readGlobalProperty(
            @PathParam("name") String name,
            @QueryParam("useSchema") String useSchema)
    {
        List<GlobalPropertiesDetail> globalProperty =
                this.resourceGroupsManager.readGlobalProperty(name, useSchema);
        return Response.ok(globalProperty).build();
    }

    @POST
    @Path("/globalproperty/update")
    public Response updateGlobalProperty(
            String jsonPayload,
            @QueryParam("useSchema") String useSchema)
    {
        try {
            GlobalPropertiesDetail globalProperty =
                    OBJECT_MAPPER.readValue(jsonPayload, GlobalPropertiesDetail.class);
            GlobalPropertiesDetail updatedGlobalProperty =
                    this.resourceGroupsManager.updateGlobalProperty(globalProperty, useSchema);
            return Response.ok(updatedGlobalProperty).build();
        }
        catch (IOException e) {
            log.error(e);
            throw new WebApplicationException(e);
        }
    }

    @POST
    @Path("/globalproperty/delete/{name}")
    public Response deleteGlobalProperty(
            @PathParam("name") String name,
            @QueryParam("useSchema") String useSchema)
    {
        resourceGroupsManager.deleteGlobalProperty(name, useSchema);
        return Response.ok().build();
    }
}
