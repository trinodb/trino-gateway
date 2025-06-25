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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataStoreConfiguration
{
    @Test
    void testDefaultConfiguration()
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                "jdbc:postgresql://localhost:5432/default",
                "default_user",
                "default_password",
                "org.postgresql.Driver",
                4,
                true);

        // When no environment is specified, the default configuration should be returned
        DataStoreConfiguration result = config.forEnvironment(null);
        assertThat(result.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/default");
        assertThat(result.getUser()).isEqualTo("default_user");
        assertThat(result.getPassword()).isEqualTo("default_password");
        assertThat(result.getDriver()).isEqualTo("org.postgresql.Driver");
        assertThat(result.getQueryHistoryHoursRetention()).isEqualTo(4);
        assertThat(result.isRunMigrationsEnabled()).isTrue();

        // When an unknown environment is specified, the default configuration should be returned
        result = config.forEnvironment("unknown");
        assertThat(result.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/default");
    }

    @Test
    void testEnvironmentSpecificConfiguration()
    {
        DataStoreConfiguration config = new DataStoreConfiguration(
                "jdbc:postgresql://localhost:5432/default",
                "default_user",
                "default_password",
                "org.postgresql.Driver",
                4,
                true);

        // Set up environment-specific configurations
        Map<String, DataStoreConfiguration.EnvironmentDbConfig> environments = new HashMap<>();

        // Development environment
        DataStoreConfiguration.EnvironmentDbConfig devConfig = new DataStoreConfiguration.EnvironmentDbConfig();
        devConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/dev");
        devConfig.setUser("dev_user");
        devConfig.setPassword("dev_password");
        environments.put("development", devConfig);

        // Production environment with some overrides
        DataStoreConfiguration.EnvironmentDbConfig prodConfig = new DataStoreConfiguration.EnvironmentDbConfig();
        prodConfig.setJdbcUrl("jdbc:postgresql://prod-db:5432/prod");
        prodConfig.setUser("prod_user");
        prodConfig.setPassword("prod_password");
        prodConfig.setQueryHistoryHoursRetention(168); // 7 days
        environments.put("production", prodConfig);

        // Testing environment with minimal overrides
        DataStoreConfiguration.EnvironmentDbConfig testConfig = new DataStoreConfiguration.EnvironmentDbConfig();
        testConfig.setJdbcUrl("jdbc:postgresql://localhost:5432/test");
        environments.put("testing", testConfig);

        config.setEnvironments(environments);

        // Test development environment
        DataStoreConfiguration devResult = config.forEnvironment("development");
        assertThat(devResult.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/dev");
        assertThat(devResult.getUser()).isEqualTo("dev_user");
        assertThat(devResult.getPassword()).isEqualTo("dev_password");
        assertThat(devResult.getDriver()).isEqualTo("org.postgresql.Driver"); // Inherited from default
        assertThat(devResult.getQueryHistoryHoursRetention()).isEqualTo(4); // Inherited from default
        assertThat(devResult.isRunMigrationsEnabled()).isTrue(); // Inherited from default

        // Test production environment
        DataStoreConfiguration prodResult = config.forEnvironment("production");
        assertThat(prodResult.getJdbcUrl()).isEqualTo("jdbc:postgresql://prod-db:5432/prod");
        assertThat(prodResult.getUser()).isEqualTo("prod_user");
        assertThat(prodResult.getPassword()).isEqualTo("prod_password");
        assertThat(prodResult.getDriver()).isEqualTo("org.postgresql.Driver"); // Inherited from default
        assertThat(prodResult.getQueryHistoryHoursRetention()).isEqualTo(168); // Overridden
        assertThat(prodResult.isRunMigrationsEnabled()).isTrue(); // Inherited from default

        // Test testing environment with minimal overrides
        DataStoreConfiguration testResult = config.forEnvironment("testing");
        assertThat(testResult.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/test");
        assertThat(testResult.getUser()).isEqualTo("default_user"); // Inherited from default
        assertThat(testResult.getPassword()).isEqualTo("default_password"); // Inherited from default
    }
}
