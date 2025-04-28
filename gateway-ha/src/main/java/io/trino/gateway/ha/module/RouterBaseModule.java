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
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.persistence.BasicJdbcPropertiesProvider;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.JdbcPropertiesProvider;
import io.trino.gateway.ha.persistence.JdbcPropertiesProviderFactory;
import io.trino.gateway.ha.persistence.MySqlJdbcPropertiesProvider;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaResourceGroupsManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.List;
import java.util.Properties;

public class RouterBaseModule
        extends AbstractModule
{
    private final HaGatewayConfiguration configuration;

    public RouterBaseModule(HaGatewayConfiguration configuration)
    {
        this.configuration = configuration;
    }

    @Override
    protected void configure()
    {
        bind(HaGatewayConfiguration.class).toInstance(configuration);
        bind(DataStoreConfiguration.class)
                .toInstance(configuration.getDataStore());

        bind(ResourceGroupsManager.class).to(HaResourceGroupsManager.class);
        bind(GatewayBackendManager.class).to(HaGatewayManager.class);
        bind(QueryHistoryManager.class).to(HaQueryHistoryManager.class);

        bind(new TypeLiteral<List<JdbcPropertiesProvider>>() {}).toInstance(List.of(
                new MySqlJdbcPropertiesProvider(),
                new BasicJdbcPropertiesProvider()));

        bind(JdbcPropertiesProviderFactory.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    public Jdbi provideJdbi(
            DataStoreConfiguration configuration,
            JdbcPropertiesProviderFactory providerFactory)
    {
        Properties props = providerFactory
                .forConfig(configuration)
                .getProperties(configuration);
        Jdbi jdbi = Jdbi.create(configuration.getJdbcUrl(), props);
        jdbi.installPlugin(new SqlObjectPlugin());
        return jdbi;
    }

    @Provides
    @Singleton
    public JdbcConnectionManager provideConnectionManager(
            Jdbi jdbi,
            DataStoreConfiguration configuration,
            JdbcPropertiesProviderFactory providerFactory)
    {
        return new JdbcConnectionManager(jdbi, configuration, providerFactory.forConfig(configuration));
    }
}
