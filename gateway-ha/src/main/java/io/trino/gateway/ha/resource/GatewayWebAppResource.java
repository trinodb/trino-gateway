package io.trino.gateway.ha.resource;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.domain.R;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.*;
import io.trino.gateway.ha.domain.response.BackendResponse;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.router.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Path("/webapp")
public class GatewayWebAppResource {
  private static final LocalDateTime START_TIME = LocalDateTime.now();
  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  @Inject
  private GatewayBackendManager gatewayBackendManager;
  @Inject
  private QueryHistoryManager queryHistoryManager;
  @Inject
  private BackendStateManager backendStateManager;
  @Inject
  private ResourceGroupsManager resourceGroupsManager;

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getAllBackends")
  public Response getAllBackends() {
    List<ProxyBackendConfiguration> allBackends = gatewayBackendManager.getAllBackends();
    List<BackendResponse> data = allBackends.stream().map(b -> {
      BackendStateManager.BackendState backendState = backendStateManager.getBackendState(b);
      Map<String, Integer> state = backendState.getState();
      BackendResponse backendResponse = new BackendResponse();
      backendResponse.setQueued(state.get("QUEUED"));
      backendResponse.setRunning(state.get("RUNNING"));
      backendResponse.setName(b.getName());
      backendResponse.setProxyTo(b.getProxyTo());
      backendResponse.setActive(b.getActive());
      backendResponse.setRoutingGroup(b.getRoutingGroup());
      backendResponse.setExternalUrl(b.getExternalUrl());
      return backendResponse;
    }).toList();
    return Response.ok(R.ok(data)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getQueryHistory")
  public Response getQueryHistory(QueryHistoryRequest query) {
    TableData<?> queryHistory = queryHistoryManager.findQueryHistory(query);
    return Response.ok(R.ok(queryHistory)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getDistribution")
  public Response getDistribution(QueryDistributionRequest query) {
    List<ProxyBackendConfiguration> allBackends = gatewayBackendManager
            .getAllBackends();
    Map<String, String> urlToNameMap = allBackends
            .stream().collect(Collectors.toMap(ProxyBackendConfiguration::getProxyTo, ProxyBackendConfiguration::getName, (o, n) -> n));
    Map<Boolean, List<ProxyBackendConfiguration>> activeMap = allBackends.stream().collect(Collectors.groupingBy(ProxyBackendConfiguration::getActive));
    Integer latestHour = query.getLatestHour();
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
    distributionResponse.setOfflineBackendCount(activeMap.get(false).size());
    distributionResponse.setOnlineBackendCount(activeMap.get(true).size());
    distributionResponse.setLineChart(lineChartMap);
    distributionResponse.setDistributionChart(distributionChart);
    distributionResponse.setTotalQueryCount(totalQueryCount);
    distributionResponse.setAverageQueryCountSecond(totalQueryCount / (latestHour * 60d * 60d));
    distributionResponse.setAverageQueryCountMinute(totalQueryCount / (latestHour * 60d));
    distributionResponse.setStartTime(START_TIME.format(formatter));
    return Response.ok(R.ok(distributionResponse)).build();
  }


  @POST
  @RolesAllowed({"ADMIN"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/saveBackend")
  public Response saveBackend(ProxyBackendConfiguration backend) {
    ProxyBackendConfiguration proxyBackendConfiguration = gatewayBackendManager.addBackend(backend);
    return Response.ok(R.ok(proxyBackendConfiguration)).build();
  }

  @POST
  @RolesAllowed({"ADMIN"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/updateBackend")
  public Response updateBackend(ProxyBackendConfiguration backend) {
    ProxyBackendConfiguration proxyBackendConfiguration = gatewayBackendManager.updateBackend(backend);
    return Response.ok(R.ok(proxyBackendConfiguration)).build();
  }

  @POST
  @RolesAllowed({"ADMIN"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/deleteBackend")
  public Response deleteBackend(ProxyBackendConfiguration backend) {
    ((HaGatewayManager) gatewayBackendManager).deleteBackend(backend.getName());
    return Response.ok(R.ok(true)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/saveResourceGroup")
  public Response saveResourceGroup(ResourceGroupsRequest request) {
    ResourceGroupsManager.ResourceGroupsDetail newResourceGroup =
            resourceGroupsManager.createResourceGroup(request.getData(), request.getUseSchema());
    return Response.ok(R.ok(newResourceGroup)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/updateResourceGroup")
  public Response updateResourceGroup(ResourceGroupsRequest request) {
    ResourceGroupsManager.ResourceGroupsDetail newResourceGroup =
            resourceGroupsManager.updateResourceGroup(request.getData(), request.getUseSchema());
    return Response.ok(R.ok(newResourceGroup)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/deleteResourceGroup")
  public Response deleteResourceGroup(ResourceGroupsRequest request) {
    ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail = request.getData();
    resourceGroupsManager.deleteResourceGroup(resourceGroupsDetail.getResourceGroupId(), request.getUseSchema());
    return Response.ok(R.ok(true)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/findResourceGroup")
  public Response findResourceGroup(QueryResourceGroupsRequest request) {
    List<ResourceGroupsManager.ResourceGroupsDetail> resourceGroupsDetailList = resourceGroupsManager.readAllResourceGroups(request.getUseSchema());
    return Response.ok(R.ok(resourceGroupsDetailList)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getResourceGroup")
  public Response readResourceGroup(QueryResourceGroupsRequest request) {
    if (Objects.isNull(request.getResourceGroupId())) {
      return Response.ok(R.fail("ResourceGroupId cannot be null!")).build();
    }
    List<ResourceGroupsManager.ResourceGroupsDetail> resourceGroup = resourceGroupsManager.readResourceGroup(
            request.getResourceGroupId(),
            request.getUseSchema());
    return Response.ok(R.ok(resourceGroup.stream().findFirst())).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/saveSelector")
  public Response saveSelector(SelectorsRequest selectorsRequest) {
    ResourceGroupsManager.SelectorsDetail updatedSelector = this.resourceGroupsManager.createSelector(
            selectorsRequest.getData(),
            selectorsRequest.getUseSchema());
    return Response.ok(R.ok(updatedSelector)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/updateSelector")
  public Response updateSelector(SelectorsRequest selectorsRequest) {
    ResourceGroupsManager.SelectorsDetail updatedSelector = resourceGroupsManager.updateSelector(
            selectorsRequest.getOldData(),
            selectorsRequest.getData(),
            selectorsRequest.getUseSchema());
    return Response.ok(R.ok(updatedSelector)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/deleteSelector")
  public Response deleteSelector(SelectorsRequest request) {
    resourceGroupsManager.deleteSelector(request.getData(), request.getUseSchema());
    return Response.ok(R.ok(true)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/findSelector")
  public Response findSelector(QuerySelectorsRequest request) {
    List<ResourceGroupsManager.SelectorsDetail> selectorsDetailList = resourceGroupsManager.readAllSelectors(
            request.getUseSchema());
    return Response.ok(R.ok(selectorsDetailList)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getSelector")
  public Response getSelector(QuerySelectorsRequest request) {
    if (Objects.isNull(request.getResourceGroupId())) {
      return Response.ok(R.fail("ResourceGroupId cannot be null!")).build();
    }
    List<ResourceGroupsManager.SelectorsDetail> selectors = resourceGroupsManager.readSelector(
            request.getResourceGroupId(),
            request.getUseSchema());
    return Response.ok(R.ok(selectors.stream().findFirst())).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/saveGlobalProperty")
  public Response saveGlobalProperty(GlobalPropertyRequest request) {
    ResourceGroupsManager.GlobalPropertiesDetail globalProperty = resourceGroupsManager.createGlobalProperty(
            request.getData(),
            request.getUseSchema());
    return Response.ok(R.ok(globalProperty)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/updateGlobalProperty")
  public Response updateGlobalProperty(GlobalPropertyRequest request) {
    ResourceGroupsManager.GlobalPropertiesDetail globalProperty = resourceGroupsManager.updateGlobalProperty(
            request.getData(),
            request.getUseSchema());
    return Response.ok(R.ok(globalProperty)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/deleteGlobalProperty")
  public Response deleteGlobalProperty(GlobalPropertyRequest request) {
    ResourceGroupsManager.GlobalPropertiesDetail globalPropertiesDetail = request.getData();
    resourceGroupsManager.deleteGlobalProperty(
            globalPropertiesDetail.getName(),
            request.getUseSchema());
    return Response.ok(R.ok(true)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/findGlobalProperty")
  public Response readAllGlobalProperties(QueryGlobalPropertyRequest request) {
    List<ResourceGroupsManager.GlobalPropertiesDetail> globalPropertiesDetailList;
    if (Strings.isNullOrEmpty(request.getName())) {
      globalPropertiesDetailList = resourceGroupsManager.readAllGlobalProperties(request.getUseSchema());
    } else {
      globalPropertiesDetailList = resourceGroupsManager.readGlobalProperty(request.getName(), request.getUseSchema());
    }
    return Response.ok(R.ok(globalPropertiesDetailList)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getGlobalProperty")
  public Response readGlobalProperty(QueryGlobalPropertyRequest request) {
    if (Strings.isNullOrEmpty(request.getName())) {
      return Response.ok(R.fail("Name cannot be null!")).build();
    }
    List<ResourceGroupsManager.GlobalPropertiesDetail> globalProperty = resourceGroupsManager.readGlobalProperty(request.getName(), request.getUseSchema());
    return Response.ok(R.ok(globalProperty.stream().findFirst())).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/saveExactMatchSourceSelector")
  public Response saveExactMatchSourceSelector(ResourceGroupsManager.ExactSelectorsDetail exactMatchSourceSelector) {
    ResourceGroupsManager.ExactSelectorsDetail newExactMatchSourceSelector = resourceGroupsManager.createExactMatchSourceSelector(exactMatchSourceSelector);
    return Response.ok(R.ok(newExactMatchSourceSelector)).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/findExactMatchSourceSelector")
  public Response readExactMatchSourceSelector() {
    List<ResourceGroupsManager.ExactSelectorsDetail> selectorsDetailList = resourceGroupsManager.readExactMatchSourceSelector();
    return Response.ok(R.ok(selectorsDetailList)).build();
  }
}
