package io.trino.gateway.ha;

import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import io.trino.gateway.proxyserver.ProxyServer;

public class GatewayManagedApp implements Managed {
  @Inject private ProxyServer gateway;

  @Override
  public void start() {
    if (gateway != null) {
      gateway.start();
    }
  }

  @Override
  public void stop() {
    if (gateway != null) {
      gateway.close();
    }
  }
}
