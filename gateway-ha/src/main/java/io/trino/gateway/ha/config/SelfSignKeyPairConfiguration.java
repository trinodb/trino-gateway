package io.trino.gateway.ha.config;

public class SelfSignKeyPairConfiguration
{
    private String privateKeyRsa;
    private String publicKeyRsa;

    public SelfSignKeyPairConfiguration() {}

    public String getPrivateKeyRsa()
    {return this.privateKeyRsa;}

    public void setPrivateKeyRsa(String privateKeyRsa)
    {this.privateKeyRsa = privateKeyRsa;}

    public String getPublicKeyRsa()
    {return this.publicKeyRsa;}

    public void setPublicKeyRsa(String publicKeyRsa)
    {this.publicKeyRsa = publicKeyRsa;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SelfSignKeyPairConfiguration)) {
            return false;
        }
        final SelfSignKeyPairConfiguration other = (SelfSignKeyPairConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$privateKeyRsa = this.getPrivateKeyRsa();
        final Object other$privateKeyRsa = other.getPrivateKeyRsa();
        if (this$privateKeyRsa == null ? other$privateKeyRsa != null : !this$privateKeyRsa.equals(other$privateKeyRsa)) {
            return false;
        }
        final Object this$publicKeyRsa = this.getPublicKeyRsa();
        final Object other$publicKeyRsa = other.getPublicKeyRsa();
        if (this$publicKeyRsa == null ? other$publicKeyRsa != null : !this$publicKeyRsa.equals(other$publicKeyRsa)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof SelfSignKeyPairConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $privateKeyRsa = this.getPrivateKeyRsa();
        result = result * PRIME + ($privateKeyRsa == null ? 43 : $privateKeyRsa.hashCode());
        final Object $publicKeyRsa = this.getPublicKeyRsa();
        result = result * PRIME + ($publicKeyRsa == null ? 43 : $publicKeyRsa.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "SelfSignKeyPairConfiguration{" +
                "privateKeyRsa='" + privateKeyRsa + '\'' +
                ", publicKeyRsa='" + publicKeyRsa + '\'' +
                '}';
    }
}
