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

import static com.google.common.base.MoreObjects.toStringHelper;

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

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClusterStats stats = (ClusterStats) o;
        return runningQueryCount == stats.runningQueryCount &&
                queuedQueryCount == stats.queuedQueryCount &&
                blockedQueryCount == stats.blockedQueryCount &&
                numWorkerNodes == stats.numWorkerNodes &&
                healthy == stats.healthy &&
                Objects.equals(clusterId, stats.clusterId) &&
                Objects.equals(proxyTo, stats.proxyTo) &&
                Objects.equals(externalUrl, stats.externalUrl) &&
                Objects.equals(routingGroup, stats.routingGroup) &&
                Objects.equals(userQueuedCount, stats.userQueuedCount);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(runningQueryCount, queuedQueryCount, blockedQueryCount, numWorkerNodes, healthy, clusterId, proxyTo, externalUrl, routingGroup, userQueuedCount);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("runningQueryCount", runningQueryCount)
                .add("queuedQueryCount", queuedQueryCount)
                .add("blockedQueryCount", blockedQueryCount)
                .add("numWorkerNodes", numWorkerNodes)
                .add("healthy", healthy)
                .add("clusterId", clusterId)
                .add("proxyTo", proxyTo)
                .add("externalUrl", externalUrl)
                .add("routingGroup", routingGroup)
                .add("userQueuedCount", userQueuedCount)
                .toString();
    }
}
