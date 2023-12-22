package io.trino.gateway.ha.config;

import java.util.Objects;

public class UserConfiguration
{
    private String privileges;
    private String password;

    public UserConfiguration(String privileges, String password)
    {
        this.privileges = privileges;
        this.password = password;
    }

    public UserConfiguration() {}

    public String getPrivileges()
    {
        return this.privileges;
    }

    public void setPrivileges(String privileges)
    {
        this.privileges = privileges;
    }

    public String getPassword()
    {
        return this.password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object privileges = this.getPrivileges();
        final Object otherPrivileges = other.getPrivileges();
        if (!Objects.equals(privileges, otherPrivileges)) {
            return false;
        }
        final Object password = this.getPassword();
        final Object otherPassword = other.getPassword();
        return Objects.equals(password, otherPassword);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof UserConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object privileges = this.getPrivileges();
        result = result * prime + (privileges == null ? 43 : privileges.hashCode());
        final Object password = this.getPassword();
        result = result * prime + (password == null ? 43 : password.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "UserConfiguration{" +
                "privileges='" + privileges + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
