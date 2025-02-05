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
import io.trino.gateway.ha.clustermonitor.ActiveClusterMonitor;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import static java.util.Objects.requireNonNull;

@Path("/trino-gateway")
public class GatewayHealthCheckResource
{
    private final ActiveClusterMonitor activeClusterMonitor;

    @Inject
    public GatewayHealthCheckResource(ActiveClusterMonitor activeClusterMonitor)
    {
        this.activeClusterMonitor = requireNonNull(activeClusterMonitor, "activeClusterMonitor is null");
    }

    @GET
    @Path("/livez")
    public Response liveness()
    {
        return Response.ok("ok").build();
    }

    @GET
    @Path("/readyz")
    public Response readiness()
    {
        if (!activeClusterMonitor.isInitialized()) {
            return Response
                    .status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Trino Gateway is still initializing")
                    .build();
        }
        return Response.ok("ok").build();
    }
}
