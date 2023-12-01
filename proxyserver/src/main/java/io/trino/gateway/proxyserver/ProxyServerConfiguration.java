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
        final Object this$name = this.getName();
        final Object other$name = other.getName();
      if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
        return false;
      }
      if (this.getLocalPort() != other.getLocalPort()) {
        return false;
      }
        final Object this$proxyTo = this.getProxyTo();
        final Object other$proxyTo = other.getProxyTo();
      if (this$proxyTo == null ? other$proxyTo != null : !this$proxyTo.equals(other$proxyTo)) {
        return false;
      }
        final Object this$prefix = this.getPrefix();
        final Object other$prefix = other.getPrefix();
      if (this$prefix == null ? other$prefix != null : !this$prefix.equals(other$prefix)) {
        return false;
      }
        final Object this$trustAll = this.getTrustAll();
        final Object other$trustAll = other.getTrustAll();
      if (this$trustAll == null ? other$trustAll != null : !this$trustAll.equals(other$trustAll)) {
        return false;
      }
        final Object this$preserveHost = this.getPreserveHost();
        final Object other$preserveHost = other.getPreserveHost();
      if (this$preserveHost == null ? other$preserveHost != null : !this$preserveHost.equals(other$preserveHost)) {
        return false;
      }
      if (this.isSsl() != other.isSsl()) {
        return false;
      }
        final Object this$keystorePath = this.getKeystorePath();
        final Object other$keystorePath = other.getKeystorePath();
      if (this$keystorePath == null ? other$keystorePath != null : !this$keystorePath.equals(other$keystorePath)) {
        return false;
      }
        final Object this$keystorePass = this.getKeystorePass();
        final Object other$keystorePass = other.getKeystorePass();
      if (this$keystorePass == null ? other$keystorePass != null : !this$keystorePass.equals(other$keystorePass)) {
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
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        result = result * PRIME + this.getLocalPort();
        final Object $proxyTo = this.getProxyTo();
        result = result * PRIME + ($proxyTo == null ? 43 : $proxyTo.hashCode());
        final Object $prefix = this.getPrefix();
        result = result * PRIME + ($prefix == null ? 43 : $prefix.hashCode());
        final Object $trustAll = this.getTrustAll();
        result = result * PRIME + ($trustAll == null ? 43 : $trustAll.hashCode());
        final Object $preserveHost = this.getPreserveHost();
        result = result * PRIME + ($preserveHost == null ? 43 : $preserveHost.hashCode());
        result = result * PRIME + (this.isSsl() ? 79 : 97);
        final Object $keystorePath = this.getKeystorePath();
        result = result * PRIME + ($keystorePath == null ? 43 : $keystorePath.hashCode());
        final Object $keystorePass = this.getKeystorePass();
        result = result * PRIME + ($keystorePass == null ? 43 : $keystorePass.hashCode());
        result = result * PRIME + (this.isForwardKeystore() ? 79 : 97);
        result = result * PRIME + this.getOutputBufferSize();
        result = result * PRIME + this.getRequestHeaderSize();
        result = result * PRIME + this.getResponseHeaderSize();
        result = result * PRIME + this.getRequestBufferSize();
        result = result * PRIME + this.getResponseBufferSize();
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
