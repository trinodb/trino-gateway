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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.clustermonitor.TrinoStatus;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.RulesType;
import io.trino.gateway.ha.config.UIConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.GlobalPropertyRequest;
import io.trino.gateway.ha.domain.request.QueryDistributionRequest;
import io.trino.gateway.ha.domain.request.QueryGlobalPropertyRequest;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.request.QueryResourceGroupsRequest;
import io.trino.gateway.ha.domain.request.QuerySelectorsRequest;
import io.trino.gateway.ha.domain.request.ResourceGroupsRequest;
import io.trino.gateway.ha.domain.request.SelectorsRequest;
import io.trino.gateway.ha.domain.response.BackendResponse;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import io.trino.gateway.ha.router.RoutingRulesManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

@Path("/webapp")
public class GatewayWebAppResource
{
    private static final LocalDateTime START_TIME = LocalDateTime.now(ZoneId.systemDefault());
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private final GatewayBackendManager gatewayBackendManager;
    private final QueryHistoryManager queryHistoryManager;
    private final BackendStateManager backendStateManager;
    private final ResourceGroupsManager resourceGroupsManager;
    private final boolean isRulesEngineEnabled;
    private final RulesType ruleType;
    // TODO Avoid putting mutable objects in fields
    private final UIConfiguration uiConfiguration;
    private final RoutingRulesManager routingRulesManager;

    @Inject
    public GatewayWebAppResource(
            GatewayBackendManager gatewayBackendManager,
            QueryHistoryManager queryHistoryManager,
            BackendStateManager backendStateManager,
            ResourceGroupsManager resourceGroupsManager,
            RoutingRulesManager routingRulesManager,
            HaGatewayConfiguration configuration)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.queryHistoryManager = requireNonNull(queryHistoryManager, "queryHistoryManager is null");
        this.backendStateManager = requireNonNull(backendStateManager, "backendStateManager is null");
        this.resourceGroupsManager = requireNonNull(resourceGroupsManager, "resourceGroupsManager is null");
        this.uiConfiguration = configuration.getUiConfiguration();
        this.routingRulesManager = requireNonNull(routingRulesManager, "routingRulesManager is null");
        RoutingRulesConfiguration routingRules = configuration.getRoutingRules();
        isRulesEngineEnabled = routingRules.isRulesEngineEnabled();
        ruleType = routingRules.getRulesType();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getAllBackends")
    public Response getAllBackends()
    {
        List<ProxyBackendConfiguration> allBackends = gatewayBackendManager.getAllBackends();
        List<BackendResponse> data = allBackends.stream().map(b -> {
            ClusterStats backendState = backendStateManager.getBackendState(b);
            BackendResponse backendResponse = new BackendResponse();
            backendResponse.setQueued(backendState.queuedQueryCount());
            backendResponse.setRunning(backendState.runningQueryCount());
            backendResponse.setName(b.getName());
            backendResponse.setProxyTo(b.getProxyTo());
            backendResponse.setActive(b.isActive());
            backendResponse.setStatus(backendState.trinoStatus().toString());
            backendResponse.setRoutingGroup(b.getRoutingGroup());
            backendResponse.setExternalUrl(b.getExternalUrl());
            return backendResponse;
        }).toList();
        return Response.ok(Result.ok(data)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/findQueryHistory")
    public Response findQueryHistory(QueryHistoryRequest query, @Context SecurityContext securityContext)
    {
        TableData<?> queryHistory;
        if (!securityContext.isUserInRole("ADMIN")) {
            queryHistory = queryHistoryManager.findQueryHistory(new QueryHistoryRequest(
                    query.page(),
                    query.size(),
                    securityContext.getUserPrincipal().getName(),
                    query.backendUrl(),
                    query.queryId(),
                    query.source()));
        }
        else {
            queryHistory = queryHistoryManager.findQueryHistory(query);
        }
        return Response.ok(Result.ok(queryHistory)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getDistribution")
    public Response getDistribution(QueryDistributionRequest query)
    {
        List<ProxyBackendConfiguration> allBackends = gatewayBackendManager
                .getAllBackends();
        Map<String, String> urlToNameMap = allBackends
                .stream().collect(Collectors.toMap(ProxyBackendConfiguration::getProxyTo, ProxyBackendConfiguration::getName, (o, n) -> n));
        Map<Boolean, List<ProxyBackendConfiguration>> activeMap = allBackends.stream().collect(Collectors.groupingBy(ProxyBackendConfiguration::isActive));
        Map<Boolean, Integer> statusCounts = allBackends.stream()
                .map(backendStateManager::getBackendState)
                .collect(Collectors.partitioningBy(
                        state -> state.trinoStatus() == TrinoStatus.HEALTHY,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        Integer latestHour = query.latestHour();
        Long ts = System.currentTimeMillis() - (latestHour * 60 * 60 * 1000);
        List<DistributionResponse.LineChart> lineChart = queryHistoryManager.findDistribution(ts);
        lineChart.forEach(qh -> qh.setName(urlToNameMap.get(qh.getBackendUrl())));
        Map<String, List<DistributionResponse.LineChart>> lineChartMap = lineChart.stream().collect(Collectors.groupingBy(DistributionResponse.LineChart::getName));
        List<DistributionResponse.DistributionChart> distributionChart = lineChartMap.values().stream().map(d -> {
            DistributionResponse.DistributionChart dc = new DistributionResponse.DistributionChart();
            DistributionResponse.LineChart lc = d.get(0);
            long sum = d.stream().collect(Collectors.summarizingLong(DistributionResponse.LineChart::getQueryCount)).getSum();
            dc.setQueryCount(sum);
            dc.setBackendUrl(lc.getBackendUrl());
            dc.setName(lc.getName());
            return dc;
        }).collect(Collectors.toList());
        long totalQueryCount = distributionChart.stream().collect(Collectors.summarizingLong(DistributionResponse.DistributionChart::getQueryCount)).getSum();
        DistributionResponse distributionResponse = new DistributionResponse();
        distributionResponse.setTotalBackendCount(allBackends.size());
        distributionResponse.setOfflineBackendCount(requireNonNullElse(activeMap.get(false), Collections.emptyList()).size());
        distributionResponse.setOnlineBackendCount(requireNonNullElse(activeMap.get(true), Collections.emptyList()).size());
        distributionResponse.setHealthyBackendCount(statusCounts.getOrDefault(true, 0));
        distributionResponse.setUnhealthyBackendCount(statusCounts.getOrDefault(false, 0));
        distributionResponse.setLineChart(lineChartMap);
        distributionResponse.setDistributionChart(distributionChart);
        distributionResponse.setTotalQueryCount(totalQueryCount);
        distributionResponse.setAverageQueryCountSecond(totalQueryCount / (latestHour * 60d * 60d));
        distributionResponse.setAverageQueryCountMinute(totalQueryCount / (latestHour * 60d));
        ZonedDateTime zonedLocalTime = START_TIME.atZone(ZoneId.systemDefault());
        ZonedDateTime utcTime = zonedLocalTime.withZoneSameInstant(ZoneOffset.UTC);
        distributionResponse.setStartTime(utcTime.format(formatter));
        return Response.ok(Result.ok(distributionResponse)).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/saveBackend")
    public Response saveBackend(ProxyBackendConfiguration backend)
    {
        ProxyBackendConfiguration proxyBackendConfiguration = gatewayBackendManager.addBackend(backend);
        return Response.ok(Result.ok(proxyBackendConfiguration)).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateBackend")
    public Response updateBackend(ProxyBackendConfiguration backend)
    {
        ProxyBackendConfiguration proxyBackendConfiguration = gatewayBackendManager.updateBackend(backend);
        return Response.ok(Result.ok(proxyBackendConfiguration)).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/deleteBackend")
    public Response deleteBackend(ProxyBackendConfiguration backend)
    {
        ((HaGatewayManager) gatewayBackendManager).deleteBackend(backend.getName());
        return Response.ok(Result.ok(true)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/saveResourceGroup")
    public Response saveResourceGroup(ResourceGroupsRequest request)
    {
        ResourceGroupsManager.ResourceGroupsDetail newResourceGroup =
                resourceGroupsManager.createResourceGroup(request.data(), request.useSchema());
        return Response.ok(Result.ok(newResourceGroup)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateResourceGroup")
    public Response updateResourceGroup(ResourceGroupsRequest request)
    {
        ResourceGroupsManager.ResourceGroupsDetail newResourceGroup =
                resourceGroupsManager.updateResourceGroup(request.data(), request.useSchema());
        return Response.ok(Result.ok(newResourceGroup)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/deleteResourceGroup")
    public Response deleteResourceGroup(ResourceGroupsRequest request)
    {
        ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail = request.data();
        resourceGroupsManager.deleteResourceGroup(resourceGroupsDetail.getResourceGroupId(), request.useSchema());
        return Response.ok(Result.ok(true)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/findResourceGroup")
    public Response findResourceGroup(QueryResourceGroupsRequest request)
    {
        List<ResourceGroupsManager.ResourceGroupsDetail> resourceGroupsDetailList = resourceGroupsManager.readAllResourceGroups(request.useSchema());
        return Response.ok(Result.ok(resourceGroupsDetailList)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getResourceGroup")
    public Response readResourceGroup(QueryResourceGroupsRequest request)
    {
        if (Objects.isNull(request.resourceGroupId())) {
            return Response.ok(Result.fail("ResourceGroupId cannot be null!")).build();
        }
        List<ResourceGroupsManager.ResourceGroupsDetail> resourceGroup = resourceGroupsManager.readResourceGroup(
                request.resourceGroupId(),
                request.useSchema());
        return Response.ok(Result.ok(resourceGroup.stream().findFirst())).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/saveSelector")
    public Response saveSelector(SelectorsRequest selectorsRequest)
    {
        ResourceGroupsManager.SelectorsDetail updatedSelector = this.resourceGroupsManager.createSelector(
                selectorsRequest.data(),
                selectorsRequest.useSchema());
        return Response.ok(Result.ok(updatedSelector)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateSelector")
    public Response updateSelector(SelectorsRequest selectorsRequest)
    {
        ResourceGroupsManager.SelectorsDetail updatedSelector = resourceGroupsManager.updateSelector(
                selectorsRequest.oldData(),
                selectorsRequest.data(),
                selectorsRequest.useSchema());
        return Response.ok(Result.ok(updatedSelector)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/deleteSelector")
    public Response deleteSelector(SelectorsRequest request)
    {
        resourceGroupsManager.deleteSelector(request.data(), request.useSchema());
        return Response.ok(Result.ok(true)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/findSelector")
    public Response findSelector(QuerySelectorsRequest request)
    {
        List<ResourceGroupsManager.SelectorsDetail> selectorsDetailList = resourceGroupsManager.readAllSelectors(
                request.useSchema());
        return Response.ok(Result.ok(selectorsDetailList)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getSelector")
    public Response getSelector(QuerySelectorsRequest request)
    {
        if (Objects.isNull(request.resourceGroupId())) {
            return Response.ok(Result.fail("ResourceGroupId cannot be null!")).build();
        }
        List<ResourceGroupsManager.SelectorsDetail> selectors = resourceGroupsManager.readSelector(
                request.resourceGroupId(),
                request.useSchema());
        return Response.ok(Result.ok(selectors.stream().findFirst())).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/saveGlobalProperty")
    public Response saveGlobalProperty(GlobalPropertyRequest request)
    {
        ResourceGroupsManager.GlobalPropertiesDetail globalProperty = resourceGroupsManager.createGlobalProperty(
                request.data(),
                request.useSchema());
        return Response.ok(Result.ok(globalProperty)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateGlobalProperty")
    public Response updateGlobalProperty(GlobalPropertyRequest request)
    {
        ResourceGroupsManager.GlobalPropertiesDetail globalProperty = resourceGroupsManager.updateGlobalProperty(
                request.data(),
                request.useSchema());
        return Response.ok(Result.ok(globalProperty)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/deleteGlobalProperty")
    public Response deleteGlobalProperty(GlobalPropertyRequest request)
    {
        ResourceGroupsManager.GlobalPropertiesDetail globalPropertiesDetail = request.data();
        resourceGroupsManager.deleteGlobalProperty(
                globalPropertiesDetail.getName(),
                request.useSchema());
        return Response.ok(Result.ok(true)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/findGlobalProperty")
    public Response readAllGlobalProperties(QueryGlobalPropertyRequest request)
    {
        List<ResourceGroupsManager.GlobalPropertiesDetail> globalPropertiesDetailList;
        if (Strings.isNullOrEmpty(request.name())) {
            globalPropertiesDetailList = resourceGroupsManager.readAllGlobalProperties(request.useSchema());
        }
        else {
            globalPropertiesDetailList = resourceGroupsManager.readGlobalProperty(request.name(), request.useSchema());
        }
        return Response.ok(Result.ok(globalPropertiesDetailList)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getGlobalProperty")
    public Response readGlobalProperty(QueryGlobalPropertyRequest request)
    {
        if (Strings.isNullOrEmpty(request.name())) {
            return Response.ok(Result.fail("Name cannot be null!")).build();
        }
        List<ResourceGroupsManager.GlobalPropertiesDetail> globalProperty = resourceGroupsManager.readGlobalProperty(request.name(), request.useSchema());
        return Response.ok(Result.ok(globalProperty.stream().findFirst())).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/saveExactMatchSourceSelector")
    public Response saveExactMatchSourceSelector(
            ResourceGroupsManager.ExactSelectorsDetail exactMatchSourceSelector)
    {
        ResourceGroupsManager.ExactSelectorsDetail newExactMatchSourceSelector = resourceGroupsManager.createExactMatchSourceSelector(exactMatchSourceSelector);
        return Response.ok(Result.ok(newExactMatchSourceSelector)).build();
    }

    @POST
    @RolesAllowed("USER")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/findExactMatchSourceSelector")
    public Response readExactMatchSourceSelector()
    {
        List<ResourceGroupsManager.ExactSelectorsDetail> selectorsDetailList = resourceGroupsManager.readExactMatchSourceSelector();
        return Response.ok(Result.ok(selectorsDetailList)).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getRoutingRules")
    public Response getRoutingRules()
            throws IOException
    {
        if (isRulesEngineEnabled && ruleType == RulesType.EXTERNAL) {
            return Response.status(Response.Status.NO_CONTENT)
                    .entity(Result.fail("Routing rules are managed by an external service")).build();
        }
        List<RoutingRule> routingRulesList = routingRulesManager.getRoutingRules();
        return Response.ok(Result.ok(routingRulesList)).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/updateRoutingRules")
    public Response updateRoutingRules(RoutingRule routingRule)
            throws IOException
    {
        List<RoutingRule> routingRulesList = routingRulesManager.updateRoutingRule(routingRule);
        return Response.ok(Result.ok(routingRulesList)).build();
    }

    @GET
    @RolesAllowed("USER")
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/getUIConfiguration")
    public Response getUIConfiguration()
    {
        return Response.ok(Result.ok(uiConfiguration)).build();
    }
}
