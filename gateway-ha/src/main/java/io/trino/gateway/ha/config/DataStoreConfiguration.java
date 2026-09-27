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

import io.airlift.units.Duration;

public class DataStoreConfiguration
{
    private String jdbcUrl;
    private String user;
    private String password;
    private String driver;
    private boolean queryHistoryEnabled = true;
    private Integer queryHistoryHoursRetention = 4;
    private boolean runMigrationsEnabled = true;
    // How long an OAuth2 pin is kept before the periodic sweep may prune it. Must exceed Trino's own
    // token-exchange challenge timeout (OAuth2Config.challengeTimeout, 15m by default) by more than
    // the poll loop's worst-case wait (OAuth2TokenExchange.MAX_POLL_TIME, 10s, times up to 10 polls =
    // 1m40s) plus a safety margin, or a pin could be swept while its handshake is still legitimately
    // in flight. 20m clears Trino's 15m challenge timeout + 1m40s poll wait (16m40s) with about 3m20s
    // to spare. Note this is a lower bound: the sweep runs on its own fixed cadence (every 5 minutes,
    // see JdbcConnectionManager), so a pin can linger until the next sweep after this period elapses.
    private Duration oauth2RoutingRetention = Duration.valueOf("20m");

    public DataStoreConfiguration(String jdbcUrl, String user, String password, String driver, boolean queryHistoryEnabled, Integer queryHistoryHoursRetention, boolean runMigrationsEnabled)
    {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.driver = driver;
        this.queryHistoryEnabled = queryHistoryEnabled;
        this.queryHistoryHoursRetention = queryHistoryHoursRetention;
        this.runMigrationsEnabled = runMigrationsEnabled;
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

    public boolean isQueryHistoryEnabled()
    {
        return queryHistoryEnabled;
    }

    public void setQueryHistoryEnabled(boolean queryHistoryEnabled)
    {
        this.queryHistoryEnabled = queryHistoryEnabled;
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

    public Duration getOauth2RoutingRetention()
    {
        return this.oauth2RoutingRetention;
    }

    public void setOauth2RoutingRetention(String oauth2RoutingRetention)
    {
        this.oauth2RoutingRetention = Duration.valueOf(oauth2RoutingRetention);
    }
}
