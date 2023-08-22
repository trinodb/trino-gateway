package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.dropwizard.views.View;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.security.LbPrincipal;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import lombok.Data;

@RolesAllowed({"USER"})
@Path("/")
public class GatewayViewResource {
  private static final long START_TIME = System.currentTimeMillis();
  @Inject
  private GatewayBackendManager gatewayBackendManager;
  @Inject
  private QueryHistoryManager queryHistoryManager;
  @Inject
  private BackendStateManager backendStateManager;

  private Optional<String> getUserNameForQueryHistory(SecurityContext securityContext) {
    LbPrincipal principal = (LbPrincipal) securityContext.getUserPrincipal();
    Optional<String> userName = Optional.empty();

    if (!securityContext.isUserInRole("ADMIN")) {
      userName = Optional.of(principal.getName());
    }
    return userName;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public GatewayView getQueryDetailsView(@Context SecurityContext securityContext) {
    GatewayView queryHistoryView = new GatewayView("/template/query-history-view.ftl",
        securityContext);

    // Get all active backends
    queryHistoryView.setBackendConfigurations(
        gatewayBackendManager.getAllBackends());

    Optional<String> userName = getUserNameForQueryHistory(securityContext);
    queryHistoryView.setQueryHistory(queryHistoryManager.fetchQueryHistory(userName));
    queryHistoryView.setQueryDistribution(getQueryHistoryDistribution(securityContext));
    return queryHistoryView;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("viewgateway")
  public GatewayView getGatewayView(@Context SecurityContext securityContext) {
    GatewayView gatewayView = new GatewayView("/template/gateway-view.ftl",
        securityContext);
    // Get all Backends
    gatewayView.setBackendConfigurations(
        gatewayBackendManager.getAllBackends());

    Map<String, BackendStateManager.BackendState> backendStates = gatewayBackendManager
        .getAllBackends()
        .stream()
        .map(backendStateManager::getBackendState)
        .collect(Collectors.toMap(s -> s.getName(), s -> s));

    gatewayView.setBackendStates(backendStates);

    Optional<String> userName = getUserNameForQueryHistory(securityContext);
    gatewayView.setQueryHistory(queryHistoryManager.fetchQueryHistory(userName));
    gatewayView.setQueryDistribution(getQueryHistoryDistribution(securityContext));
    return gatewayView;
  }

  @GET
  @Path("api/queryHistory")
  @Produces(MediaType.APPLICATION_JSON)
  public List<QueryHistoryManager.QueryDetail> getQueryHistory(@Context SecurityContext
                                                                   securityContext) {
    Optional<String> userName = getUserNameForQueryHistory(securityContext);
    return queryHistoryManager.fetchQueryHistory(userName);
  }

  @GET
  @Path("api/activeBackends")
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProxyBackendConfiguration> getActiveBackends() {
    return gatewayBackendManager.getAllActiveBackends();
  }

  @GET
  @Path("api/queryHistoryDistribution")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Integer> getQueryHistoryDistribution(@Context SecurityContext
                                                              securityContext) {
    Map<String, String> urlToNameMap = new HashMap<>();
    gatewayBackendManager
        .getAllBackends()
        .forEach(
            backend -> {
              urlToNameMap.put(backend.getProxyTo(), backend.getName());
            });

    Map<String, Integer> clusterToQueryCount = new HashMap<>();
    Optional<String> userName = getUserNameForQueryHistory(securityContext);
    queryHistoryManager
        .fetchQueryHistory(userName)
        .forEach(
            q -> {
              String backend = urlToNameMap.get(q.getBackendUrl());
              if (backend == null) {
                backend = q.getBackendUrl();
              }
              if (!clusterToQueryCount.containsKey(backend)) {
                clusterToQueryCount.put(backend, 0);
              }
              clusterToQueryCount.put(backend, clusterToQueryCount.get(backend) + 1);
            });
    return clusterToQueryCount;
  }

  @Data
  public static class GatewayView extends View {
    private final long gatewayStartTime = START_TIME;
    private String displayName;
    private List<ProxyBackendConfiguration> backendConfigurations;
    private List<QueryHistoryManager.QueryDetail> queryHistory;
    private Map<String, BackendStateManager.BackendState> backendStates;
    private Map<String, Integer> queryDistribution;

    protected GatewayView(String templateName, SecurityContext securityContext) {
      super(templateName, Charset.defaultCharset());
      setDisplayName(securityContext.getUserPrincipal().getName());
    }
  }
}
