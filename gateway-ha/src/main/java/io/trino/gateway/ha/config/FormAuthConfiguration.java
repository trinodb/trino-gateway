package io.trino.gateway.ha.config;

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
    {return this.selfSignKeyPair;}

    public void setSelfSignKeyPair(SelfSignKeyPairConfiguration selfSignKeyPair)
    {this.selfSignKeyPair = selfSignKeyPair;}

    public String getLdapConfigPath()
    {return this.ldapConfigPath;}

    public void setLdapConfigPath(String ldapConfigPath)
    {this.ldapConfigPath = ldapConfigPath;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof FormAuthConfiguration)) {
            return false;
        }
        final FormAuthConfiguration other = (FormAuthConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$selfSignKeyPair = this.getSelfSignKeyPair();
        final Object other$selfSignKeyPair = other.getSelfSignKeyPair();
        if (this$selfSignKeyPair == null ? other$selfSignKeyPair != null : !this$selfSignKeyPair.equals(other$selfSignKeyPair)) {
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
    {return other instanceof FormAuthConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $selfSignKeyPair = this.getSelfSignKeyPair();
        result = result * PRIME + ($selfSignKeyPair == null ? 43 : $selfSignKeyPair.hashCode());
        final Object $ldapConfigPath = this.getLdapConfigPath();
        result = result * PRIME + ($ldapConfigPath == null ? 43 : $ldapConfigPath.hashCode());
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
