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
package io.trino.gateway.ha.module;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.router.QueryCountBasedRouter;
import io.trino.gateway.ha.router.RoutingManager;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;

public class QueryCountBasedRouterProvider
        extends AbstractModule
{
    private static final Logger logger = Logger.get(QueryCountBasedRouterProvider.class);

    // We require all modules to take HaGatewayConfiguration as the only parameter
    public QueryCountBasedRouterProvider(HaGatewayConfiguration configuration)
    {
        // no-op
    }

    @Override
    public void configure()
    {
        logger.info("Using QueryCountBasedRouterProvider instead of default");
        newOptionalBinder(binder(), RoutingManager.class)
                .setBinding()
                .to(QueryCountBasedRouter.class)
                .in(Scopes.SINGLETON);
    }
}
