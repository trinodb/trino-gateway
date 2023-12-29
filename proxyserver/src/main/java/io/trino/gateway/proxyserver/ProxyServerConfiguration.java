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
}
