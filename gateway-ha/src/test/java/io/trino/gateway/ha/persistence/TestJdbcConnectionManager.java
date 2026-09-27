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
package io.trino.gateway.ha.persistence;

import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestJdbcConnectionManager
{
    @Test
    void testOauth2RoutingSweepScheduledWhenEnabled()
    {
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig("jdbc:postgresql://localhost:5432/mydb"), gatewayConfig(true))) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isTrue();
        }
    }

    @Test
    void testOauth2RoutingSweepNotScheduledWhenDisabled()
    {
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig("jdbc:postgresql://localhost:5432/mydb"), gatewayConfig(false))) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isFalse();
        }
    }

    @Test
    void testUnknownJdbcPrefixAllowedWhenOauth2RoutingDisabled()
    {
        try (JdbcConnectionManager connectionManager = new JdbcConnectionManager(dataStoreConfig("jdbc:h2:mem:test"), gatewayConfig(false))) {
            assertThat(connectionManager.isOauth2RoutingSweepScheduled()).isFalse();
        }
    }

    @Test
    void testUnknownJdbcPrefixRejectedWhenOauth2RoutingEnabled()
    {
        assertThatThrownBy(() -> new JdbcConnectionManager(dataStoreConfig("jdbc:h2:mem:test"), gatewayConfig(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JDBC URL: jdbc:h2:mem:test. Only PostgreSQL, MySQL, and Oracle are supported.");
    }

    private static DataStoreConfiguration dataStoreConfig(String jdbcUrl)
    {
        return new DataStoreConfiguration(jdbcUrl, "sa", "sa", "", true, 4, true, null);
    }

    private static HaGatewayConfiguration gatewayConfig(boolean oauth2RoutingEnabled)
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.getRouting().setOauth2RoutingEnabled(oauth2RoutingEnabled);
        return configuration;
    }
}
