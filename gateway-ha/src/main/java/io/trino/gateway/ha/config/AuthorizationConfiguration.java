package io.trino.gateway.ha.config;

import java.util.Objects;

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
    {
        return this.admin;
    }

    public void setAdmin(String admin)
    {
        this.admin = admin;
    }

    public String getUser()
    {
        return this.user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getApi()
    {
        return this.api;
    }

    public void setApi(String api)
    {
        this.api = api;
    }

    public String getLdapConfigPath()
    {
        return this.ldapConfigPath;
    }

    public void setLdapConfigPath(String ldapConfigPath)
    {
        this.ldapConfigPath = ldapConfigPath;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthorizationConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object admin = this.getAdmin();
        final Object otherAdmin = other.getAdmin();
        if (!Objects.equals(admin, otherAdmin)) {
            return false;
        }
        final Object user = this.getUser();
        final Object otherUser = other.getUser();
        if (!Objects.equals(user, otherUser)) {
            return false;
        }
        final Object api = this.getApi();
        final Object otherApi = other.getApi();
        if (!Objects.equals(api, otherApi)) {
            return false;
        }
        final Object ldapConfigPath = this.getLdapConfigPath();
        final Object otherLdapConfigPath = other.getLdapConfigPath();
        return Objects.equals(ldapConfigPath, otherLdapConfigPath);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof AuthorizationConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object admin = this.getAdmin();
        result = result * prime + (admin == null ? 43 : admin.hashCode());
        final Object user = this.getUser();
        result = result * prime + (user == null ? 43 : user.hashCode());
        final Object api = this.getApi();
        result = result * prime + (api == null ? 43 : api.hashCode());
        final Object ldapConfigPath = this.getLdapConfigPath();
        result = result * prime + (ldapConfigPath == null ? 43 : ldapConfigPath.hashCode());
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
