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
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.config.RulesType;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.RoutingRule;
import io.trino.gateway.ha.router.RoutingRulesManager;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class RoutingRulesResourceHandler
{
    private final RoutingRulesManager routingRulesManager;
    private final boolean isRulesEngineEnabled;
    private final RulesType rulesType;

    @Inject
    public RoutingRulesResourceHandler(RoutingRulesManager routingRulesManager, HaGatewayConfiguration configuration)
    {
        this.routingRulesManager = requireNonNull(routingRulesManager, "routingRulesManager is null");
        RoutingRulesConfiguration routingRulesConfiguration = requireNonNull(configuration, "configuration is null").getRoutingRules();
        this.isRulesEngineEnabled = routingRulesConfiguration.isRulesEngineEnabled();
        this.rulesType = routingRulesConfiguration.getRulesType();
    }

    public Response getRoutingRules()
            throws IOException
    {
        if (isRulesEngineEnabled && rulesType == RulesType.EXTERNAL) {
            return Response.status(Response.Status.NO_CONTENT)
                    .entity(Result.fail("Routing rules are managed by an external service")).build();
        }
        List<RoutingRule> routingRulesList = routingRulesManager.getRoutingRules();
        return Response.ok(Result.ok(routingRulesList)).build();
    }

    public Response updateRoutingRules(RoutingRule routingRule)
            throws IOException
    {
        List<RoutingRule> routingRulesList = routingRulesManager.updateRoutingRule(routingRule);
        return Response.ok(Result.ok(routingRulesList)).build();
    }
}
