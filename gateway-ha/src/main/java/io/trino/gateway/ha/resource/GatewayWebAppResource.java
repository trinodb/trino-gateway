package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.domain.R;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryDistributionRequest;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/webapp")
public class GatewayWebAppResource {
  private static final long START_TIME = System.currentTimeMillis();
  @Inject
  private GatewayBackendManager gatewayBackendManager;
  @Inject
  private QueryHistoryManager queryHistoryManager;
  @Inject
  private BackendStateManager backendStateManager;

  @POST
  @RolesAllowed({"USER"})
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getAllBackends")
  public Response getAllBackends() {
    List<ProxyBackendConfiguration> allBackends = gatewayBackendManager.getAllBackends();
    Map<String, BackendStateManager.BackendState> backendStates = allBackends.stream()
            .map(backendStateManager::getBackendState)
            .collect(Collectors.toMap(BackendStateManager.BackendState::getName, s -> s));
    Map<String, Object> data = Map.of("backendStates", backendStates, "allBackends", allBackends);
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
    distributionResponse.setAverageQueryCountSecond(totalQueryCount / (latestHour * 60 * 60));
    distributionResponse.setAverageQueryCountMinute(totalQueryCount / (latestHour * 60));
    return Response.ok(R.ok(distributionResponse)).build();
  }

}
