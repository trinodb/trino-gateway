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

import java.util.Map;
import java.util.Optional;

/**
 * Configuration for the data store used by the gateway.
 * Supports environment-based configuration through environment variables.
 */
public class DataStoreConfiguration
{
    private String jdbcUrl;
    private String user;
    private String password;
    private String driver;
    private Integer queryHistoryHoursRetention = 4;
    private boolean runMigrationsEnabled = true;
    private Map<String, EnvironmentDbConfig> environments;

    public DataStoreConfiguration(String jdbcUrl, String user, String password, String driver, Integer queryHistoryHoursRetention, boolean runMigrationsEnabled)
    {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.driver = driver;
        this.queryHistoryHoursRetention = queryHistoryHoursRetention;
        this.runMigrationsEnabled = runMigrationsEnabled;
    }

    public DataStoreConfiguration() {}

    /**
     * Gets the database configuration for the specified environment.
     * If the environment is not configured, returns the default configuration.
     *
     * @param environment The environment name
     * @return The database configuration for the specified environment
     */
    public DataStoreConfiguration forEnvironment(String environment)
    {
        if (environment == null || environments == null || !environments.containsKey(environment)) {
            return this;
        }

        EnvironmentDbConfig envConfig = environments.get(environment);
        return new DataStoreConfiguration(
                Optional.ofNullable(envConfig.getJdbcUrl()).orElse(this.jdbcUrl),
                Optional.ofNullable(envConfig.getUser()).orElse(this.user),
                Optional.ofNullable(envConfig.getPassword()).orElse(this.password),
                Optional.ofNullable(envConfig.getDriver()).orElse(this.driver),
                Optional.ofNullable(envConfig.getQueryHistoryHoursRetention()).orElse(this.queryHistoryHoursRetention),
                Optional.ofNullable(envConfig.isRunMigrationsEnabled()).orElse(this.runMigrationsEnabled));
    }

    /**
     * Configuration for environment-specific database settings.
     */
    public static class EnvironmentDbConfig
    {
        private String jdbcUrl;
        private String user;
        private String password;
        private String driver;
        private Integer queryHistoryHoursRetention;
        private Boolean runMigrationsEnabled;

        public String getJdbcUrl()
        {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl)
        {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUser()
        {
            return user;
        }

        public void setUser(String user)
        {
            this.user = user;
        }

        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }

        public String getDriver()
        {
            return driver;
        }

        public void setDriver(String driver)
        {
            this.driver = driver;
        }

        public Integer getQueryHistoryHoursRetention()
        {
            return queryHistoryHoursRetention;
        }

        public void setQueryHistoryHoursRetention(Integer queryHistoryHoursRetention)
        {
            this.queryHistoryHoursRetention = queryHistoryHoursRetention;
        }

        public Boolean isRunMigrationsEnabled()
        {
            return runMigrationsEnabled;
        }

        public void setRunMigrationsEnabled(Boolean runMigrationsEnabled)
        {
            this.runMigrationsEnabled = runMigrationsEnabled;
        }
    }

    public String getJdbcUrl()
    {
        return this.jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl)
    {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUser()
    {
        return this.user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getPassword()
    {
        return this.password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getDriver()
    {
        return this.driver;
    }

    public void setDriver(String driver)
    {
        this.driver = driver;
    }

    public Integer getQueryHistoryHoursRetention()
    {
        return this.queryHistoryHoursRetention;
    }

    public void setQueryHistoryHoursRetention(Integer queryHistoryHoursRetention)
    {
        this.queryHistoryHoursRetention = queryHistoryHoursRetention;
    }

    public boolean isRunMigrationsEnabled()
    {
        return this.runMigrationsEnabled;
    }

    public void setRunMigrationsEnabled(boolean runMigrationsEnabled)
    {
        this.runMigrationsEnabled = runMigrationsEnabled;
    }

    public Map<String, EnvironmentDbConfig> getEnvironments()
    {
        return environments;
    }

    public void setEnvironments(Map<String, EnvironmentDbConfig> environments)
    {
        this.environments = environments;
    }
}
