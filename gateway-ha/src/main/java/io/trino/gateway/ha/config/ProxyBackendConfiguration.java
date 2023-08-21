package io.trino.gateway.ha.config;

import io.trino.gateway.proxyserver.ProxyServerConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProxyBackendConfiguration extends ProxyServerConfiguration {
  private boolean active = true;
  private String routingGroup = "adhoc";
  private String externalUrl;

  public String getExternalUrl() {
    if (externalUrl == null) {
      return getProxyTo();
    }
    return externalUrl;
  }

}
