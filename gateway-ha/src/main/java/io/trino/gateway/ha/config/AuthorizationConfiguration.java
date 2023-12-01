package io.trino.gateway.ha.config;

public class AuthorizationConfiguration
{
    private String admin;
    private String user;
    private String api;
    private String ldapConfigPath;

    public AuthorizationConfiguration(String admin, String user, String api, String ldapConfigPath)
    {
        this.admin = admin;
        this.user = user;
        this.api = api;
        this.ldapConfigPath = ldapConfigPath;
    }

    public AuthorizationConfiguration() {}

    public String getAdmin()
    {return this.admin;}

    public void setAdmin(String admin)
    {this.admin = admin;}

    public String getUser()
    {return this.user;}

    public void setUser(String user)
    {this.user = user;}

    public String getApi()
    {return this.api;}

    public void setApi(String api)
    {this.api = api;}

    public String getLdapConfigPath()
    {return this.ldapConfigPath;}

    public void setLdapConfigPath(String ldapConfigPath)
    {this.ldapConfigPath = ldapConfigPath;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthorizationConfiguration)) {
            return false;
        }
        final AuthorizationConfiguration other = (AuthorizationConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$admin = this.getAdmin();
        final Object other$admin = other.getAdmin();
        if (this$admin == null ? other$admin != null : !this$admin.equals(other$admin)) {
            return false;
        }
        final Object this$user = this.getUser();
        final Object other$user = other.getUser();
        if (this$user == null ? other$user != null : !this$user.equals(other$user)) {
            return false;
        }
        final Object this$api = this.getApi();
        final Object other$api = other.getApi();
        if (this$api == null ? other$api != null : !this$api.equals(other$api)) {
            return false;
        }
        final Object this$ldapConfigPath = this.getLdapConfigPath();
        final Object other$ldapConfigPath = other.getLdapConfigPath();
        if (this$ldapConfigPath == null ? other$ldapConfigPath != null : !this$ldapConfigPath.equals(other$ldapConfigPath)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof AuthorizationConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $admin = this.getAdmin();
        result = result * PRIME + ($admin == null ? 43 : $admin.hashCode());
        final Object $user = this.getUser();
        result = result * PRIME + ($user == null ? 43 : $user.hashCode());
        final Object $api = this.getApi();
        result = result * PRIME + ($api == null ? 43 : $api.hashCode());
        final Object $ldapConfigPath = this.getLdapConfigPath();
        result = result * PRIME + ($ldapConfigPath == null ? 43 : $ldapConfigPath.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "AuthorizationConfiguration{" +
                "admin='" + admin + '\'' +
                ", user='" + user + '\'' +
                ", api='" + api + '\'' +
                ", ldapConfigPath='" + ldapConfigPath + '\'' +
                '}';
    }
}
