package io.trino.gateway.ha.config;

import java.util.Objects;

public class SelfSignKeyPairConfiguration
{
    private String privateKeyRsa;
    private String publicKeyRsa;

    public SelfSignKeyPairConfiguration() {}

    public String getPrivateKeyRsa()
    {
        return this.privateKeyRsa;
    }

    public void setPrivateKeyRsa(String privateKeyRsa)
    {
        this.privateKeyRsa = privateKeyRsa;
    }

    public String getPublicKeyRsa()
    {
        return this.publicKeyRsa;
    }

    public void setPublicKeyRsa(String publicKeyRsa)
    {
        this.publicKeyRsa = publicKeyRsa;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SelfSignKeyPairConfiguration other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        final Object privateKeyRsa = this.getPrivateKeyRsa();
        final Object otherPrivateKeyRsa = other.getPrivateKeyRsa();
        if (!Objects.equals(privateKeyRsa, otherPrivateKeyRsa)) {
            return false;
        }
        final Object publicKeyRsa = this.getPublicKeyRsa();
        final Object otherPublicKeyRsa = other.getPublicKeyRsa();
        return Objects.equals(publicKeyRsa, otherPublicKeyRsa);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof SelfSignKeyPairConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object privateKeyRsa = this.getPrivateKeyRsa();
        result = result * prime + (privateKeyRsa == null ? 43 : privateKeyRsa.hashCode());
        final Object publicKeyRsa = this.getPublicKeyRsa();
        result = result * prime + (publicKeyRsa == null ? 43 : publicKeyRsa.hashCode());
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
