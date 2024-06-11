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
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.security.LbPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Path("/trino-gateway")
public class GatewayViewResource
{
    private final GatewayBackendManager gatewayBackendManager;
    private final QueryHistoryManager queryHistoryManager;

    @Inject
    public GatewayViewResource(GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager)
    {
        this.gatewayBackendManager = requireNonNull(gatewayBackendManager, "gatewayBackendManager is null");
        this.queryHistoryManager = requireNonNull(queryHistoryManager, "queryHistoryManager is null");
    }

    private Optional<String> getUserNameForQueryHistory(SecurityContext securityContext)
    {
        LbPrincipal principal = (LbPrincipal) securityContext.getUserPrincipal();
        Optional<String> userName = Optional.empty();

        if (!securityContext.isUserInRole("ADMIN")) {
            userName = Optional.of(principal.getName());
        }
        return userName;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getIndex()
    {
        return Response.ok(getClass().getResourceAsStream("/static/index.html")).build();
    }

    @GET
    @RolesAllowed("USER")
    @Path("api/queryHistory")
    @Produces(MediaType.APPLICATION_JSON)
    public List<QueryHistoryManager.QueryDetail> getQueryHistory(@Context SecurityContext
            securityContext)
    {
        Optional<String> userName = getUserNameForQueryHistory(securityContext);
        return queryHistoryManager.fetchQueryHistory(userName);
    }

    @GET
    @RolesAllowed("USER")
    @Path("api/activeBackends")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProxyBackendConfiguration> getActiveBackends()
    {
        return gatewayBackendManager.getAllActiveBackends();
    }

    @GET
    @RolesAllowed("USER")
    @Path("api/queryHistoryDistribution")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Integer> getQueryHistoryDistribution(@Context SecurityContext
            securityContext)
    {
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
}
