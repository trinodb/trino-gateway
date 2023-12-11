package io.trino.gateway.ha.domain.response;

import java.util.List;
import java.util.Map;

/**
 * QueryHistory Request Body
 *
 * @author Wei Peng
 */
public class DistributionResponse {
  private Integer totalBackendCount;
  private Integer offlineBackendCount;
  private Integer onlineBackendCount;
  private Long totalQueryCount;
  private Long averageQueryCountMinute;
  private Long averageQueryCountSecond;
  private List<DistributionChart> distributionChart;
  private Map<String, List<LineChart>> lineChart;

  public Integer getTotalBackendCount() {
    return totalBackendCount;
  }

  public void setTotalBackendCount(Integer totalBackendCount) {
    this.totalBackendCount = totalBackendCount;
  }

  public Integer getOfflineBackendCount() {
    return offlineBackendCount;
  }

  public void setOfflineBackendCount(Integer offlineBackendCount) {
    this.offlineBackendCount = offlineBackendCount;
  }

  public Integer getOnlineBackendCount() {
    return onlineBackendCount;
  }

  public void setOnlineBackendCount(Integer onlineBackendCount) {
    this.onlineBackendCount = onlineBackendCount;
  }

  public Long getTotalQueryCount() {
    return totalQueryCount;
  }

  public void setTotalQueryCount(Long totalQueryCount) {
    this.totalQueryCount = totalQueryCount;
  }

  public Long getAverageQueryCountMinute() {
    return averageQueryCountMinute;
  }

  public void setAverageQueryCountMinute(Long averageQueryCountMinute) {
    this.averageQueryCountMinute = averageQueryCountMinute;
  }

  public Long getAverageQueryCountSecond() {
    return averageQueryCountSecond;
  }

  public void setAverageQueryCountSecond(Long averageQueryCountSecond) {
    this.averageQueryCountSecond = averageQueryCountSecond;
  }

  public List<DistributionChart> getDistributionChart() {
    return distributionChart;
  }

  public void setDistributionChart(List<DistributionChart> distributionChart) {
    this.distributionChart = distributionChart;
  }

  public Map<String, List<LineChart>> getLineChart() {
    return lineChart;
  }

  public void setLineChart(Map<String, List<LineChart>> lineChart) {
    this.lineChart = lineChart;
  }

  public static class DistributionChart {
    private String backendUrl;
    private Long queryCount;
    private String name;

    public String getBackendUrl() {
      return backendUrl;
    }

    public void setBackendUrl(String backendUrl) {
      this.backendUrl = backendUrl;
    }

    public Long getQueryCount() {
      return queryCount;
    }

    public void setQueryCount(Long queryCount) {
      this.queryCount = queryCount;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  public static class LineChart {
    private String minute;
    private String backendUrl;
    private Long queryCount;
    private String name;

    public String getMinute() {
      return minute;
    }

    public void setMinute(String minute) {
      this.minute = minute;
    }

    public String getBackendUrl() {
      return backendUrl;
    }

    public void setBackendUrl(String backendUrl) {
      this.backendUrl = backendUrl;
    }

    public Long getQueryCount() {
      return queryCount;
    }

    public void setQueryCount(Long queryCount) {
      this.queryCount = queryCount;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
