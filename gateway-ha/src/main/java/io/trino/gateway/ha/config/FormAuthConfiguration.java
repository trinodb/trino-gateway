package io.trino.gateway.ha.config;

import java.util.Objects;

public class FormAuthConfiguration
{
    private SelfSignKeyPairConfiguration selfSignKeyPair;
    private String ldapConfigPath;

    public FormAuthConfiguration(SelfSignKeyPairConfiguration selfSignKeyPair, String ldapConfigPath)
    {
        this.selfSignKeyPair = selfSignKeyPair;
        this.ldapConfigPath = ldapConfigPath;
    }

    public FormAuthConfiguration() {}

    public SelfSignKeyPairConfiguration getSelfSignKeyPair()
    {
        return this.selfSignKeyPair;
    }

    public void setSelfSignKeyPair(SelfSignKeyPairConfiguration selfSignKeyPair)
    {
        this.selfSignKeyPair = selfSignKeyPair;
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
        if (!(o instanceof FormAuthConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object selfSignKeyPair = this.getSelfSignKeyPair();
        final Object otherSelfSignKeyPair = other.getSelfSignKeyPair();
        if (!Objects.equals(selfSignKeyPair, otherSelfSignKeyPair)) {
            return false;
        }
        final Object ldapConfigPath = this.getLdapConfigPath();
        final Object otherLdapConfigPath = other.getLdapConfigPath();
        return Objects.equals(ldapConfigPath, otherLdapConfigPath);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof FormAuthConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object selfSignKeyPair = this.getSelfSignKeyPair();
        result = result * prime + (selfSignKeyPair == null ? 43 : selfSignKeyPair.hashCode());
        final Object ldapConfigPath = this.getLdapConfigPath();
        result = result * prime + (ldapConfigPath == null ? 43 : ldapConfigPath.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "FormAuthConfiguration{" +
                "selfSignKeyPair=" + selfSignKeyPair +
                ", ldapConfigPath='" + ldapConfigPath + '\'' +
                '}';
    }
}
