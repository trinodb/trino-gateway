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

public class DataStoreConfiguration
{
    private String jdbcUrl;
    private String user;
    private String password;
    private String driver;
    private Integer queryHistoryHoursRetention = 4;
    private boolean runMigrationsEnabled = true;
    private Integer maxPoolSize;

    public DataStoreConfiguration(String jdbcUrl, String user, String password, String driver, Integer queryHistoryHoursRetention, boolean runMigrationsEnabled, Integer maxPoolSize)
    {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.driver = driver;
        this.queryHistoryHoursRetention = queryHistoryHoursRetention;
        this.runMigrationsEnabled = runMigrationsEnabled;
        this.maxPoolSize = maxPoolSize;
    }

    public DataStoreConfiguration(String jdbcUrl, String user, String password, String driver, Integer queryHistoryHoursRetention, boolean runMigrationsEnabled)
    {
        this(jdbcUrl, user, password, driver, queryHistoryHoursRetention, runMigrationsEnabled, null);
    }

    public Integer getMaxPoolSize()
    {
        return this.maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize)
    {
        this.maxPoolSize = maxPoolSize;
    }

    public DataStoreConfiguration() {}

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
}
