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
import io.dropwizard.views.common.View;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.security.LbPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

  public static class GatewayView extends View {
        private final long gatewayStartTime = START_TIME;
        private String displayName;
        private List<ProxyBackendConfiguration> backendConfigurations;
        private List<QueryHistoryManager.QueryDetail> queryHistory;
        private Map<String, BackendStateManager.BackendState> backendStates;
        private Map<String, Integer> queryDistribution;

        protected GatewayView(String templateName, SecurityContext securityContext)
        {
            super(templateName, Charset.defaultCharset());
            setDisplayName(securityContext.getUserPrincipal().getName());
        }

        public long getGatewayStartTime()
        {return this.gatewayStartTime;}

        public String getDisplayName()
        {return this.displayName;}

        public List<ProxyBackendConfiguration> getBackendConfigurations()
        {return this.backendConfigurations;}

        public List<QueryHistoryManager.QueryDetail> getQueryHistory()
        {return this.queryHistory;}

        public Map<String, BackendStateManager.BackendState> getBackendStates()
        {return this.backendStates;}

        public Map<String, Integer> getQueryDistribution()
        {return this.queryDistribution;}

        public void setDisplayName(String displayName)
        {this.displayName = displayName;}

        public void setBackendConfigurations(List<ProxyBackendConfiguration> backendConfigurations)
        {this.backendConfigurations = backendConfigurations;}

        public void setQueryHistory(List<QueryHistoryManager.QueryDetail> queryHistory)
        {this.queryHistory = queryHistory;}

        public void setBackendStates(Map<String, BackendStateManager.BackendState> backendStates)
        {this.backendStates = backendStates;}

        public void setQueryDistribution(Map<String, Integer> queryDistribution)
        {this.queryDistribution = queryDistribution;}

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof GatewayView)) {
                return false;
            }
            final GatewayView other = (GatewayView) o;
            if (!other.canEqual((Object) this)) {
                return false;
            }
            if (this.getGatewayStartTime() != other.getGatewayStartTime()) {
                return false;
            }
            final Object this$displayName = this.getDisplayName();
            final Object other$displayName = other.getDisplayName();
            if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName)) {
                return false;
            }
            final Object this$backendConfigurations = this.getBackendConfigurations();
            final Object other$backendConfigurations = other.getBackendConfigurations();
            if (this$backendConfigurations == null ? other$backendConfigurations != null : !this$backendConfigurations.equals(other$backendConfigurations)) {
                return false;
            }
            final Object this$queryHistory = this.getQueryHistory();
            final Object other$queryHistory = other.getQueryHistory();
            if (this$queryHistory == null ? other$queryHistory != null : !this$queryHistory.equals(other$queryHistory)) {
                return false;
            }
            final Object this$backendStates = this.getBackendStates();
            final Object other$backendStates = other.getBackendStates();
            if (this$backendStates == null ? other$backendStates != null : !this$backendStates.equals(other$backendStates)) {
                return false;
            }
            final Object this$queryDistribution = this.getQueryDistribution();
            final Object other$queryDistribution = other.getQueryDistribution();
            if (this$queryDistribution == null ? other$queryDistribution != null : !this$queryDistribution.equals(other$queryDistribution)) {
                return false;
            }
            return true;
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof GatewayView;
        }


        public int hashCode()
        {
            final int PRIME = 59;
            int result = 1;
            final long $gatewayStartTime = this.getGatewayStartTime();
            result = result * PRIME + (int) ($gatewayStartTime >>> 32 ^ $gatewayStartTime);
            final Object $displayName = this.getDisplayName();
            result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
            final Object $backendConfigurations = this.getBackendConfigurations();
            result = result * PRIME + ($backendConfigurations == null ? 43 : $backendConfigurations.hashCode());
            final Object $queryHistory = this.getQueryHistory();
            result = result * PRIME + ($queryHistory == null ? 43 : $queryHistory.hashCode());
            final Object $backendStates = this.getBackendStates();
            result = result * PRIME + ($backendStates == null ? 43 : $backendStates.hashCode());
            final Object $queryDistribution = this.getQueryDistribution();
            result = result * PRIME + ($queryDistribution == null ? 43 : $queryDistribution.hashCode());
            return result;
        }

        public String toString()
        {
            return "GatewayViewResource.GatewayView(gatewayStartTime="
                    + this.getGatewayStartTime() + ", displayName="
                    + this.getDisplayName() + ", backendConfigurations="
                    + this.getBackendConfigurations() + ", queryHistory="
                    + this.getQueryHistory() + ", backendStates="
                    + this.getBackendStates() + ", queryDistribution="
                    + this.getQueryDistribution() + ")";
        }
    }
}
