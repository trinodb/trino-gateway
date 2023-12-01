package io.trino.gateway.ha.config;

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
    {return this.privileges;}

    public void setPrivileges(String privileges)
    {this.privileges = privileges;}

    public String getPassword()
    {return this.password;}

    public void setPassword(String password)
    {this.password = password;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserConfiguration)) {
            return false;
        }
        final UserConfiguration other = (UserConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$privileges = this.getPrivileges();
        final Object other$privileges = other.getPrivileges();
        if (this$privileges == null ? other$privileges != null : !this$privileges.equals(other$privileges)) {
            return false;
        }
        final Object this$password = this.getPassword();
        final Object other$password = other.getPassword();
        if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof UserConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $privileges = this.getPrivileges();
        result = result * PRIME + ($privileges == null ? 43 : $privileges.hashCode());
        final Object $password = this.getPassword();
        result = result * PRIME + ($password == null ? 43 : $password.hashCode());
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
