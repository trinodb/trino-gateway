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

import java.util.Objects;

public class DataStoreConfiguration
{
    private String jdbcUrl;
    private String user;
    private String password;
    private String driver;
    private Integer queryHistoryHoursRetention = 4;

    public DataStoreConfiguration(String jdbcUrl, String user, String password, String driver, Integer queryHistoryHoursRetention)
    {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.driver = driver;
        this.queryHistoryHoursRetention = queryHistoryHoursRetention;
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

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DataStoreConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object jdbcUrl = this.getJdbcUrl();
        final Object otherJdbcUrl = other.getJdbcUrl();
        if (!Objects.equals(jdbcUrl, otherJdbcUrl)) {
            return false;
        }
        final Object user = this.getUser();
        final Object otherUser = other.getUser();
        if (!Objects.equals(user, otherUser)) {
            return false;
        }
        final Object password = this.getPassword();
        final Object otherPassword = other.getPassword();
        if (!Objects.equals(password, otherPassword)) {
            return false;
        }
        final Object driver = this.getDriver();
        final Object otherDriver = other.getDriver();
        if (!Objects.equals(driver, otherDriver)) {
            return false;
        }
        final Object queryHistoryHoursRetention = this.getQueryHistoryHoursRetention();
        final Object otherQueryHistoryHoursRetention = other.getQueryHistoryHoursRetention();
        return Objects.equals(queryHistoryHoursRetention, otherQueryHistoryHoursRetention);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof DataStoreConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object jdbcUrl = this.getJdbcUrl();
        result = result * prime + (jdbcUrl == null ? 43 : jdbcUrl.hashCode());
        final Object user = this.getUser();
        result = result * prime + (user == null ? 43 : user.hashCode());
        final Object password = this.getPassword();
        result = result * prime + (password == null ? 43 : password.hashCode());
        final Object driver = this.getDriver();
        result = result * prime + (driver == null ? 43 : driver.hashCode());
        final Object queryHistoryHoursRetention = this.getQueryHistoryHoursRetention();
        result = result * prime + (queryHistoryHoursRetention == null ? 43 : queryHistoryHoursRetention.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "DataStoreConfiguration{" +
                "jdbcUrl='" + jdbcUrl + '\'' +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
                ", driver='" + driver + '\'' +
                ", queryHistoryHoursRetention=" + queryHistoryHoursRetention +
                '}';
    }
}
