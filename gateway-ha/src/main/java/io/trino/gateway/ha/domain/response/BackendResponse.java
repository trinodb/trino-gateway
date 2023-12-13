package io.trino.gateway.ha.domain.response;

import io.trino.gateway.ha.config.ProxyBackendConfiguration;

/**
 * Backend Request Body
 *
 * @author Wei Peng
 */
public class BackendResponse extends ProxyBackendConfiguration {
  private Integer queued;
  private Integer running;

  public Integer getQueued() {
    return queued;
  }

  public void setQueued(Integer queued) {
    this.queued = queued;
  }

  public Integer getRunning() {
    return running;
  }

  public void setRunning(Integer running) {
    this.running = running;
  }
}
