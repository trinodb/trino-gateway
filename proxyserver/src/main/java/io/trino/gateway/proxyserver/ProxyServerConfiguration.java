package io.trino.gateway.proxyserver;

public class ProxyServerConfiguration
{
    private String name;
    private int localPort;
    private String proxyTo;
    private String prefix = "/";
    private String trustAll = "true";
    private String preserveHost = "true";
    private boolean ssl;
    private String keystorePath;
    private String keystorePass;
    private boolean forwardKeystore;
    private int outputBufferSize = 2 * 1024 * 1024;
    private int requestHeaderSize = 2 * 1024 * 1024;
    private int responseHeaderSize = 8 * 1024;
    private int requestBufferSize = 4 * 1024;
    private int responseBufferSize = 16 * 1024;

    public ProxyServerConfiguration()
    {
    }

    protected String getPrefix()
    {
        return prefix;
    }

    protected String getTrustAll()
    {
        return trustAll;
    }

    protected String getPreserveHost()
    {
        return preserveHost;
    }

    protected boolean isSsl()
    {
        return ssl;
    }

    protected String getKeystorePath()
    {
        return keystorePath;
    }

    protected String getKeystorePass()
    {
        return keystorePass;
    }

    protected boolean isForwardKeystore()
    {
        return forwardKeystore;
    }

    protected int getLocalPort()
    {
        return localPort;
    }

    protected int getOutputBufferSize()
    {
        return outputBufferSize;
    }

    protected int getRequestHeaderSize()
    {
        return requestHeaderSize;
    }

    protected int getResponseHeaderSize()
    {
        return responseHeaderSize;
    }

    protected int getRequestBufferSize()
    {
        return requestBufferSize;
    }

    protected int getResponseBufferSize()
    {
        return responseBufferSize;
    }

    public String getName()
    {
        return this.name;
    }

    public String getProxyTo()
    {
        return this.proxyTo;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setLocalPort(int localPort)
    {
        this.localPort = localPort;
    }

    public void setProxyTo(String proxyTo)
    {
        this.proxyTo = proxyTo;
    }

    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    public void setTrustAll(String trustAll)
    {
        this.trustAll = trustAll;
    }

    public void setPreserveHost(String preserveHost)
    {
        this.preserveHost = preserveHost;
    }

    public void setSsl(boolean ssl)
    {
        this.ssl = ssl;
    }

    public void setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
    }

    public void setKeystorePass(String keystorePass)
    {
        this.keystorePass = keystorePass;
    }

    public void setForwardKeystore(boolean forwardKeystore)
    {
        this.forwardKeystore = forwardKeystore;
    }

    public void setOutputBufferSize(int outputBufferSize)
    {
        this.outputBufferSize = outputBufferSize;
    }

    public void setRequestHeaderSize(int requestHeaderSize)
    {
        this.requestHeaderSize = requestHeaderSize;
    }

    public void setResponseHeaderSize(int responseHeaderSize)
    {
        this.responseHeaderSize = responseHeaderSize;
    }

    public void setRequestBufferSize(int requestBufferSize)
    {
        this.requestBufferSize = requestBufferSize;
    }

    public void setResponseBufferSize(int responseBufferSize)
    {
        this.responseBufferSize = responseBufferSize;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProxyServerConfiguration)) {
            return false;
        }
        final ProxyServerConfiguration other = (ProxyServerConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object thisName = this.getName();
        final Object otherName = other.getName();
        if (thisName == null ? otherName != null : !thisName.equals(otherName)) {
            return false;
        }
        if (this.getLocalPort() != other.getLocalPort()) {
            return false;
        }
        final Object thisProxyTo = this.getProxyTo();
        final Object otherProxyTo = other.getProxyTo();
        if (thisProxyTo == null ? otherProxyTo != null : !thisProxyTo.equals(otherProxyTo)) {
            return false;
        }
        final Object thisPrefix = this.getPrefix();
        final Object otherPrefix = other.getPrefix();
        if (thisPrefix == null ? otherPrefix != null : !thisPrefix.equals(otherPrefix)) {
            return false;
        }
        final Object thisTrustAll = this.getTrustAll();
        final Object otherTrustAll = other.getTrustAll();
        if (thisTrustAll == null ? otherTrustAll != null : !thisTrustAll.equals(otherTrustAll)) {
            return false;
        }
        final Object thisPreserveHost = this.getPreserveHost();
        final Object otherPreserveHost = other.getPreserveHost();
        if (thisPreserveHost == null ? otherPreserveHost != null : !thisPreserveHost.equals(otherPreserveHost)) {
            return false;
        }
        if (this.isSsl() != other.isSsl()) {
            return false;
        }
        final Object thisKeystorePath = this.getKeystorePath();
        final Object otherKeystorePath = other.getKeystorePath();
        if (thisKeystorePath == null ? otherKeystorePath != null : !thisKeystorePath.equals(otherKeystorePath)) {
            return false;
        }
        final Object thisKeystorePass = this.getKeystorePass();
        final Object otherKeystorePass = other.getKeystorePass();
        if (thisKeystorePass == null ? otherKeystorePass != null : !thisKeystorePass.equals(otherKeystorePass)) {
            return false;
        }
        if (this.isForwardKeystore() != other.isForwardKeystore()) {
            return false;
        }
        if (this.getOutputBufferSize() != other.getOutputBufferSize()) {
            return false;
        }
        if (this.getRequestHeaderSize() != other.getRequestHeaderSize()) {
            return false;
        }
        if (this.getResponseHeaderSize() != other.getResponseHeaderSize()) {
            return false;
        }
        if (this.getRequestBufferSize() != other.getRequestBufferSize()) {
            return false;
        }
        if (this.getResponseBufferSize() != other.getResponseBufferSize()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof ProxyServerConfiguration;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        final Object name = this.getName();
        result = result * prime + (name == null ? 43 : name.hashCode());
        result = result * prime + this.getLocalPort();
        final Object proxyTo = this.getProxyTo();
        result = result * prime + (proxyTo == null ? 43 : proxyTo.hashCode());
        final Object prefix = this.getPrefix();
        result = result * prime + (prefix == null ? 43 : prefix.hashCode());
        final Object trustAll = this.getTrustAll();
        result = result * prime + (trustAll == null ? 43 : trustAll.hashCode());
        final Object preserveHost = this.getPreserveHost();
        result = result * prime + (preserveHost == null ? 43 : preserveHost.hashCode());
        result = result * prime + (this.isSsl() ? 79 : 97);
        final Object keystorePath = this.getKeystorePath();
        result = result * prime + (keystorePath == null ? 43 : keystorePath.hashCode());
        final Object keystorePass = this.getKeystorePass();
        result = result * prime + (keystorePass == null ? 43 : keystorePass.hashCode());
        result = result * prime + (this.isForwardKeystore() ? 79 : 97);
        result = result * prime + this.getOutputBufferSize();
        result = result * prime + this.getRequestHeaderSize();
        result = result * prime + this.getResponseHeaderSize();
        result = result * prime + this.getRequestBufferSize();
        result = result * prime + this.getResponseBufferSize();
        return result;
    }

    @Override
    public String toString()
    {
        return "ProxyServerConfiguration{" +
                "name='" + name + '\'' +
                ", localPort=" + localPort +
                ", proxyTo='" + proxyTo + '\'' +
                ", prefix='" + prefix + '\'' +
                ", trustAll='" + trustAll + '\'' +
                ", preserveHost='" + preserveHost + '\'' +
                ", ssl=" + ssl +
                ", keystorePath='" + keystorePath + '\'' +
                ", keystorePass='" + keystorePass + '\'' +
                ", forwardKeystore=" + forwardKeystore +
                ", outputBufferSize=" + outputBufferSize +
                ", requestHeaderSize=" + requestHeaderSize +
                ", responseHeaderSize=" + responseHeaderSize +
                ", requestBufferSize=" + requestBufferSize +
                ", responseBufferSize=" + responseBufferSize +
                '}';
    }
}
