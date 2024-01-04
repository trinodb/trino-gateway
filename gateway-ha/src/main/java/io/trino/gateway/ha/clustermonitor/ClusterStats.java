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
import java.util.Objects;

public class ClusterStats
{
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
    {
        return this.runningQueryCount;
    }

    public void setRunningQueryCount(int runningQueryCount)
    {
        this.runningQueryCount = runningQueryCount;
    }

    public int getQueuedQueryCount()
    {
        return this.queuedQueryCount;
    }

    public void setQueuedQueryCount(int queuedQueryCount)
    {
        this.queuedQueryCount = queuedQueryCount;
    }

    public int getBlockedQueryCount()
    {
        return this.blockedQueryCount;
    }

    public void setBlockedQueryCount(int blockedQueryCount)
    {
        this.blockedQueryCount = blockedQueryCount;
    }

    public int getNumWorkerNodes()
    {
        return this.numWorkerNodes;
    }

    public void setNumWorkerNodes(int numWorkerNodes)
    {
        this.numWorkerNodes = numWorkerNodes;
    }

    public boolean isHealthy()
    {
        return this.healthy;
    }

    public void setHealthy(boolean healthy)
    {
        this.healthy = healthy;
    }

    public String getClusterId()
    {
        return this.clusterId;
    }

    public void setClusterId(String clusterId)
    {
        this.clusterId = clusterId;
    }

    public String getProxyTo()
    {
        return this.proxyTo;
    }

    public void setProxyTo(String proxyTo)
    {
        this.proxyTo = proxyTo;
    }

    public String getExternalUrl()
    {
        return this.externalUrl;
    }

    public void setExternalUrl(String externalUrl)
    {
        this.externalUrl = externalUrl;
    }

    public String getRoutingGroup()
    {
        return this.routingGroup;
    }

    public void setRoutingGroup(String routingGroup)
    {
        this.routingGroup = routingGroup;
    }

    public Map<String, Integer> getUserQueuedCount()
    {
        return this.userQueuedCount;
    }

    public void setUserQueuedCount(Map<String, Integer> userQueuedCount)
    {
        this.userQueuedCount = userQueuedCount;
    }

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ClusterStats other)) {
            return false;
        }
        if (!other.canEqual(this)) {
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
        final Object clusterId = this.getClusterId();
        final Object otherClusterId = other.getClusterId();
        if (!Objects.equals(clusterId, otherClusterId)) {
            return false;
        }
        final Object proxyTo = this.getProxyTo();
        final Object otherProxyTo = other.getProxyTo();
        if (!Objects.equals(proxyTo, otherProxyTo)) {
            return false;
        }
        final Object externalUrl = this.getExternalUrl();
        final Object otherExternalUrl = other.getExternalUrl();
        if (!Objects.equals(externalUrl, otherExternalUrl)) {
            return false;
        }
        final Object routingGroup = this.getRoutingGroup();
        final Object otherRoutingGroup = other.getRoutingGroup();
        if (!Objects.equals(routingGroup, otherRoutingGroup)) {
            return false;
        }
        final Object userQueuedCount = this.getUserQueuedCount();
        final Object otherUserQueuedCount = other.getUserQueuedCount();
        return Objects.equals(userQueuedCount, otherUserQueuedCount);
    }

    protected boolean canEqual(final Object other)
    {
        return other instanceof ClusterStats;
    }

    public int hashCode()
    {
        final int prime = 59;
        int result = 1;
        result = result * prime + this.getRunningQueryCount();
        result = result * prime + this.getQueuedQueryCount();
        result = result * prime + this.getBlockedQueryCount();
        result = result * prime + this.getNumWorkerNodes();
        result = result * prime + (this.isHealthy() ? 79 : 97);
        final Object clusterId = this.getClusterId();
        result = result * prime + (clusterId == null ? 43 : clusterId.hashCode());
        final Object proxyTo = this.getProxyTo();
        result = result * prime + (proxyTo == null ? 43 : proxyTo.hashCode());
        final Object externalUrl = this.getExternalUrl();
        result = result * prime + (externalUrl == null ? 43 : externalUrl.hashCode());
        final Object routingGroup = this.getRoutingGroup();
        result = result * prime + (routingGroup == null ? 43 : routingGroup.hashCode());
        final Object userQueuedCount = this.getUserQueuedCount();
        result = result * prime + (userQueuedCount == null ? 43 : userQueuedCount.hashCode());
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
