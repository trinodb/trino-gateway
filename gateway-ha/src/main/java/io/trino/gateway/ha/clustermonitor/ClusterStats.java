/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.clustermonitor;

import java.util.Map;

public class ClusterStats {
  private int runningQueryCount;
  private int queuedQueryCount;
  private int blockedQueryCount;
  private int numWorkerNodes;
  private boolean healthy;
  private String clusterId;
  private String proxyTo;
  private String externalUrl;
  private String routingGroup;
  private Map<String, Integer> userQueuedCount;

  public ClusterStats() {}

  public int getRunningQueryCount()
  {return this.runningQueryCount;}

  public int getQueuedQueryCount()
  {return this.queuedQueryCount;}

  public int getBlockedQueryCount()
  {return this.blockedQueryCount;}

  public int getNumWorkerNodes()
  {return this.numWorkerNodes;}

  public boolean isHealthy()
  {return this.healthy;}

  public String getClusterId()
  {return this.clusterId;}

  public String getProxyTo()
  {return this.proxyTo;}

  public String getExternalUrl()
  {return this.externalUrl;}

  public String getRoutingGroup()
  {return this.routingGroup;}

  public Map<String, Integer> getUserQueuedCount()
  {return this.userQueuedCount;}

  public void setRunningQueryCount(int runningQueryCount)
  {this.runningQueryCount = runningQueryCount;}

  public void setQueuedQueryCount(int queuedQueryCount)
  {this.queuedQueryCount = queuedQueryCount;}

  public void setBlockedQueryCount(int blockedQueryCount)
  {this.blockedQueryCount = blockedQueryCount;}

  public void setNumWorkerNodes(int numWorkerNodes)
  {this.numWorkerNodes = numWorkerNodes;}

  public void setHealthy(boolean healthy)
  {this.healthy = healthy;}

  public void setClusterId(String clusterId)
  {this.clusterId = clusterId;}

  public void setProxyTo(String proxyTo)
  {this.proxyTo = proxyTo;}

  public void setExternalUrl(String externalUrl)
  {this.externalUrl = externalUrl;}

  public void setRoutingGroup(String routingGroup)
  {this.routingGroup = routingGroup;}

  public void setUserQueuedCount(Map<String, Integer> userQueuedCount)
  {this.userQueuedCount = userQueuedCount;}

  public boolean equals(final Object o)
  {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ClusterStats)) {
      return false;
    }
    final ClusterStats other = (ClusterStats) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (this.getRunningQueryCount() != other.getRunningQueryCount()) {
      return false;
    }
    if (this.getQueuedQueryCount() != other.getQueuedQueryCount()) {
      return false;
    }
    if (this.getBlockedQueryCount() != other.getBlockedQueryCount()) {
      return false;
    }
    if (this.getNumWorkerNodes() != other.getNumWorkerNodes()) {
      return false;
    }
    if (this.isHealthy() != other.isHealthy()) {
      return false;
    }
    final Object this$clusterId = this.getClusterId();
    final Object other$clusterId = other.getClusterId();
    if (this$clusterId == null ? other$clusterId != null : !this$clusterId.equals(other$clusterId)) {
      return false;
    }
    final Object this$proxyTo = this.getProxyTo();
    final Object other$proxyTo = other.getProxyTo();
    if (this$proxyTo == null ? other$proxyTo != null : !this$proxyTo.equals(other$proxyTo)) {
      return false;
    }
    final Object this$externalUrl = this.getExternalUrl();
    final Object other$externalUrl = other.getExternalUrl();
    if (this$externalUrl == null ? other$externalUrl != null : !this$externalUrl.equals(other$externalUrl)) {
      return false;
    }
    final Object this$routingGroup = this.getRoutingGroup();
    final Object other$routingGroup = other.getRoutingGroup();
    if (this$routingGroup == null ? other$routingGroup != null : !this$routingGroup.equals(other$routingGroup)) {
      return false;
    }
    final Object this$userQueuedCount = this.getUserQueuedCount();
    final Object other$userQueuedCount = other.getUserQueuedCount();
    if (this$userQueuedCount == null ? other$userQueuedCount != null : !this$userQueuedCount.equals(other$userQueuedCount)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other)
  {return other instanceof ClusterStats;}

  public int hashCode()
  {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + this.getRunningQueryCount();
    result = result * PRIME + this.getQueuedQueryCount();
    result = result * PRIME + this.getBlockedQueryCount();
    result = result * PRIME + this.getNumWorkerNodes();
    result = result * PRIME + (this.isHealthy() ? 79 : 97);
    final Object $clusterId = this.getClusterId();
    result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
    final Object $proxyTo = this.getProxyTo();
    result = result * PRIME + ($proxyTo == null ? 43 : $proxyTo.hashCode());
    final Object $externalUrl = this.getExternalUrl();
    result = result * PRIME + ($externalUrl == null ? 43 : $externalUrl.hashCode());
    final Object $routingGroup = this.getRoutingGroup();
    result = result * PRIME + ($routingGroup == null ? 43 : $routingGroup.hashCode());
    final Object $userQueuedCount = this.getUserQueuedCount();
    result = result * PRIME + ($userQueuedCount == null ? 43 : $userQueuedCount.hashCode());
    return result;
  }

  @Override
  public String toString()
  {
    return "ClusterStats{" +
            "runningQueryCount=" + runningQueryCount +
            ", queuedQueryCount=" + queuedQueryCount +
            ", blockedQueryCount=" + blockedQueryCount +
            ", numWorkerNodes=" + numWorkerNodes +
            ", healthy=" + healthy +
            ", clusterId='" + clusterId + '\'' +
            ", proxyTo='" + proxyTo + '\'' +
            ", externalUrl='" + externalUrl + '\'' +
            ", routingGroup='" + routingGroup + '\'' +
            ", userQueuedCount=" + userQueuedCount +
            '}';
  }

}
