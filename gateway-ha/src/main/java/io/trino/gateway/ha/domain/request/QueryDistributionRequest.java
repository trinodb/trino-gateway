package io.trino.gateway.ha.domain.request;

/**
 * Distribution Request Body
 *
 * @author Wei Peng
 */
public class QueryDistributionRequest {
  private Integer latestHour = 1;

  public Integer getLatestHour() {
    return latestHour;
  }

  public void setLatestHour(Integer latestHour) {
    this.latestHour = latestHour;
  }
}
