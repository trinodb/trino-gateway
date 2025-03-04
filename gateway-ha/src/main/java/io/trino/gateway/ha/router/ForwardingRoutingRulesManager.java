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
package io.trino.gateway.ha.router;

import com.google.inject.Inject;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RoutingRulesConfiguration;
import io.trino.gateway.ha.persistence.RecordAndAnnotatedConstructorMapper;
import io.trino.gateway.ha.persistence.dao.RoutingRule;
import io.trino.gateway.ha.persistence.dao.RoutingRulesDao;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.List;

public class ForwardingRoutingRulesManager
        implements IRoutingRulesManager
{
    IRoutingRulesManager delegate;

    @Inject
    ForwardingRoutingRulesManager(HaGatewayConfiguration haGatewayConfiguration)
    {
        RoutingRulesConfiguration routingRulesConfig = haGatewayConfiguration.getRoutingRules();
        delegate = switch (routingRulesConfig.getRulesType()) {
            case FILE -> new FileBasedRoutingRulesManager(haGatewayConfiguration);
            case DB -> {
                String jdbcUrl = haGatewayConfiguration.getDataStore().getJdbcUrl();
                Jdbi jdbi = Jdbi.create(
                                jdbcUrl,
                                haGatewayConfiguration.getDataStore().getUser(),
                                haGatewayConfiguration.getDataStore().getPassword())
                        .installPlugin(new SqlObjectPlugin())
                        .registerRowMapper(new RecordAndAnnotatedConstructorMapper());

                yield new DbRoutingRulesManager(jdbi.onDemand(RoutingRulesDao.class), jdbcUrl.startsWith("jdbc:postgresql"));
            }
            default -> throw new RuntimeException("No routing manager for " + routingRulesConfig.getRulesType());
        };
    }

    @Override
    public List<RoutingRule> getRoutingRules()
    {
        return delegate.getRoutingRules();
    }

    @Override
    public List<RoutingRule> updateRoutingRule(RoutingRule routingRule)
    {
        return delegate.updateRoutingRule(routingRule);
    }

    @Override
    public void deleteRoutingRule(String name)
    {
        delegate.deleteRoutingRule(name);
    }

    @Override
    public void createRoutingRule(RoutingRule routingRule)
    {
        delegate.createRoutingRule(routingRule);
    }
}
