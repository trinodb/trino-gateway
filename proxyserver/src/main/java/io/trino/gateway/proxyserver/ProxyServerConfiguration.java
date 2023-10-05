package io.trino.gateway.proxyserver;

import lombok.Data;

@Data
public class ProxyServerConfiguration {
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

  protected String getPrefix() {
    return prefix;
  }

  protected String getTrustAll() {
    return trustAll;
  }

  protected String getPreserveHost() {
    return preserveHost;
  }

  protected boolean isSsl() {
    return ssl;
  }

  protected String getKeystorePath() {
    return keystorePath;
  }

  protected String getKeystorePass() {
    return keystorePass;
  }

  protected boolean isForwardKeystore() {
    return forwardKeystore;
  }

  protected int getLocalPort() {
    return localPort;
  }

  protected int getOutputBufferSize() {
    return outputBufferSize;
  }

  protected int getRequestHeaderSize() {
    return requestHeaderSize;
  }

  protected int getResponseHeaderSize() {
    return responseHeaderSize;
  }

  protected int getRequestBufferSize() {
    return requestBufferSize;
  }

  protected int getResponseBufferSize() {
    return responseBufferSize;
  }
}
