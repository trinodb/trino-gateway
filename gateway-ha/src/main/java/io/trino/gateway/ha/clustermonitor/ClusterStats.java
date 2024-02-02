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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record ClusterStats(
        String clusterId,
        int runningQueryCount,
        int queuedQueryCount,
        int blockedQueryCount,
        int numWorkerNodes,
        boolean healthy,
        String proxyTo,
        String externalUrl,
        String routingGroup,
        Map<String, Integer> userQueuedCount)
{
    public static Builder builder(String clusterId)
    {
        return new Builder(clusterId);
    }

    public static final class Builder
    {
        private final String clusterId;
        private int runningQueryCount;
        private int queuedQueryCount;
        private int blockedQueryCount;
        private int numWorkerNodes;
        private boolean healthy;
        private String proxyTo;
        private String externalUrl;
        private String routingGroup;
        private Map<String, Integer> userQueuedCount;

        private Builder(String clusterId)
        {
            this.clusterId = requireNonNull(clusterId, "clusterId is null");
        }

        public Builder runningQueryCount(int runningQueryCount)
        {
            this.runningQueryCount = runningQueryCount;
            return this;
        }

        public Builder queuedQueryCount(int queuedQueryCount)
        {
            this.queuedQueryCount = queuedQueryCount;
            return this;
        }

        public Builder blockedQueryCount(int blockedQueryCount)
        {
            this.blockedQueryCount = blockedQueryCount;
            return this;
        }

        public Builder numWorkerNodes(int numWorkerNodes)
        {
            this.numWorkerNodes = numWorkerNodes;
            return this;
        }

        public Builder healthy(boolean healthy)
        {
            this.healthy = healthy;
            return this;
        }

        public Builder proxyTo(String proxyTo)
        {
            this.proxyTo = proxyTo;
            return this;
        }

        public Builder externalUrl(String externalUrl)
        {
            this.externalUrl = externalUrl;
            return this;
        }

        public Builder routingGroup(String routingGroup)
        {
            this.routingGroup = routingGroup;
            return this;
        }

        public Builder userQueuedCount(Map<String, Integer> userQueuedCount)
        {
            this.userQueuedCount = ImmutableMap.copyOf(userQueuedCount);
            return this;
        }

        public ClusterStats build()
        {
            return new ClusterStats(
                    clusterId,
                    runningQueryCount,
                    queuedQueryCount,
                    blockedQueryCount,
                    numWorkerNodes,
                    healthy,
                    proxyTo,
                    externalUrl,
                    routingGroup,
                    userQueuedCount);
        }
    }
}
