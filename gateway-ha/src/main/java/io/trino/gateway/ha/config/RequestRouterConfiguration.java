package io.trino.gateway.ha.config;

import lombok.Data;

@Data
public class RequestRouterConfiguration {
  // Local gateway port
  private int port;

  // Name of the routing gateway name (for metrics purposes)
  private String name;

  // Use SSL?
  private boolean ssl;
  private String keystorePath;
  private String keystorePass;

  private int historySize = 2000;

  // Use the certificate between gateway and trino?
  private boolean forwardKeystore;

  // Set size for HttpConfiguration
  private int outputBufferSize = 32 * 1024;
  private int requestHeaderSize = 8 * 1024;
  private int responseHeaderSize = 8 * 1024;

  // Set size for HttpClient
  private int requestBufferSize = 4 * 1024;
  private int responseBufferSize = 16 * 1024;
}
