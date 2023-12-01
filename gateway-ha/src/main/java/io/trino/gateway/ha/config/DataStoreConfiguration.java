package io.trino.gateway.ha.config;

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
    {return this.jdbcUrl;}

    public void setJdbcUrl(String jdbcUrl)
    {this.jdbcUrl = jdbcUrl;}

    public String getUser()
    {return this.user;}

    public void setUser(String user)
    {this.user = user;}

    public String getPassword()
    {return this.password;}

    public void setPassword(String password)
    {this.password = password;}

    public String getDriver()
    {return this.driver;}

    public void setDriver(String driver)
    {this.driver = driver;}

    public Integer getQueryHistoryHoursRetention()
    {return this.queryHistoryHoursRetention;}

    public void setQueryHistoryHoursRetention(Integer queryHistoryHoursRetention)
    {this.queryHistoryHoursRetention = queryHistoryHoursRetention;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DataStoreConfiguration)) {
            return false;
        }
        final DataStoreConfiguration other = (DataStoreConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$jdbcUrl = this.getJdbcUrl();
        final Object other$jdbcUrl = other.getJdbcUrl();
        if (this$jdbcUrl == null ? other$jdbcUrl != null : !this$jdbcUrl.equals(other$jdbcUrl)) {
            return false;
        }
        final Object this$user = this.getUser();
        final Object other$user = other.getUser();
        if (this$user == null ? other$user != null : !this$user.equals(other$user)) {
            return false;
        }
        final Object this$password = this.getPassword();
        final Object other$password = other.getPassword();
        if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
            return false;
        }
        final Object this$driver = this.getDriver();
        final Object other$driver = other.getDriver();
        if (this$driver == null ? other$driver != null : !this$driver.equals(other$driver)) {
            return false;
        }
        final Object this$queryHistoryHoursRetention = this.getQueryHistoryHoursRetention();
        final Object other$queryHistoryHoursRetention = other.getQueryHistoryHoursRetention();
        if (this$queryHistoryHoursRetention == null ? other$queryHistoryHoursRetention != null : !this$queryHistoryHoursRetention.equals(other$queryHistoryHoursRetention)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof DataStoreConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $jdbcUrl = this.getJdbcUrl();
        result = result * PRIME + ($jdbcUrl == null ? 43 : $jdbcUrl.hashCode());
        final Object $user = this.getUser();
        result = result * PRIME + ($user == null ? 43 : $user.hashCode());
        final Object $password = this.getPassword();
        result = result * PRIME + ($password == null ? 43 : $password.hashCode());
        final Object $driver = this.getDriver();
        result = result * PRIME + ($driver == null ? 43 : $driver.hashCode());
        final Object $queryHistoryHoursRetention = this.getQueryHistoryHoursRetention();
        result = result * PRIME + ($queryHistoryHoursRetention == null ? 43 : $queryHistoryHoursRetention.hashCode());
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
