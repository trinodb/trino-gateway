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

class TestDataStoreConfiguration
{
    @Test
    void testDefaultValues()
    {
        DataStoreConfiguration dataStoreConfiguration = new DataStoreConfiguration();
        assertThat(dataStoreConfiguration.getQueryHistoryHoursRetention()).isEqualTo(4);
        assertThat(dataStoreConfiguration.isQueryHistoryEnabled()).isTrue();
        assertThat(dataStoreConfiguration.isRunMigrationsEnabled()).isTrue();
    }

    @Test
    void testQueryHistoryEnabledSetter()
    {
        DataStoreConfiguration dataStoreConfiguration = new DataStoreConfiguration();
        assertThat(dataStoreConfiguration.isQueryHistoryEnabled()).isTrue();

        dataStoreConfiguration.setQueryHistoryEnabled(false);
        assertThat(dataStoreConfiguration.isQueryHistoryEnabled()).isFalse();

        dataStoreConfiguration.setQueryHistoryEnabled(true);
        assertThat(dataStoreConfiguration.isQueryHistoryEnabled()).isTrue();
    }

    @Test
    void testAllSetters()
    {
        DataStoreConfiguration dataStoreConfiguration = new DataStoreConfiguration();

        dataStoreConfiguration.setJdbcUrl("jdbc:postgresql://localhost:5432/test");
        assertThat(dataStoreConfiguration.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/test");

        dataStoreConfiguration.setUser("test_user");
        assertThat(dataStoreConfiguration.getUser()).isEqualTo("test_user");

        dataStoreConfiguration.setPassword("test_password");
        assertThat(dataStoreConfiguration.getPassword()).isEqualTo("test_password");

        dataStoreConfiguration.setDriver("org.postgresql.Driver");
        assertThat(dataStoreConfiguration.getDriver()).isEqualTo("org.postgresql.Driver");

        dataStoreConfiguration.setQueryHistoryHoursRetention(24);
        assertThat(dataStoreConfiguration.getQueryHistoryHoursRetention()).isEqualTo(24);

        dataStoreConfiguration.setQueryHistoryEnabled(false);
        assertThat(dataStoreConfiguration.isQueryHistoryEnabled()).isFalse();

        dataStoreConfiguration.setRunMigrationsEnabled(false);
        assertThat(dataStoreConfiguration.isRunMigrationsEnabled()).isFalse();
    }
}
