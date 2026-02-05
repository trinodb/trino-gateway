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
package io.trino.gateway.ha.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestDataStoreConfiguration
{
    @Test
    void testDefaults()
    {
        DataStoreConfiguration dataStoreConfiguration = new DataStoreConfiguration();
        assertThat(dataStoreConfiguration.getJdbcUrl()).isNull();
        assertThat(dataStoreConfiguration.getUser()).isNull();
        assertThat(dataStoreConfiguration.getPassword()).isNull();
        assertThat(dataStoreConfiguration.getDriver()).isNull();
    }

    @Test
    void testExplicitPropertyMappings()
    {
        DataStoreConfiguration dataStoreConfiguration = new DataStoreConfiguration();

        dataStoreConfiguration.setJdbcUrl("jdbc:postgresql://localhost:5432/gateway");
        assertThat(dataStoreConfiguration.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/gateway");

        dataStoreConfiguration.setUser("gateway_user");
        assertThat(dataStoreConfiguration.getUser()).isEqualTo("gateway_user");

        dataStoreConfiguration.setPassword("gateway_password");
        assertThat(dataStoreConfiguration.getPassword()).isEqualTo("gateway_password");

        dataStoreConfiguration.setDriver("org.postgresql.Driver");
        assertThat(dataStoreConfiguration.getDriver()).isEqualTo("org.postgresql.Driver");
    }
}
