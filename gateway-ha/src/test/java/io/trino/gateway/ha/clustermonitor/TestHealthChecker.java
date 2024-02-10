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

import io.trino.gateway.ha.notifier.Notifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.util.Collections;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestHealthChecker
{
    @Test
    public void testNotifyUnhealthyCluster()
    {
        Notifier notifier = Mockito.mock(Notifier.class);
        HealthChecker healthChecker = new HealthChecker(notifier);
        ClusterStats clusterStats = mock(ClusterStats.class);
        when(clusterStats.healthy()).thenReturn(false);
        when(clusterStats.clusterId()).thenReturn("testCluster");

        healthChecker.observe(Collections.singletonList(clusterStats));

        verify(notifier, times(1)).sendNotification(eq("Cluster name 'testCluster' is unhealthy"), anyString());
    }

    @Test
    public void testNotifyForTooManyQueuedQueries()
    {
        Notifier notifier = Mockito.mock(Notifier.class);
        HealthChecker healthChecker = new HealthChecker(notifier);
        ClusterStats clusterStats = mock(ClusterStats.class);
        when(clusterStats.healthy()).thenReturn(true);
        when(clusterStats.clusterId()).thenReturn("testCluster");
        when(clusterStats.queuedQueryCount()).thenReturn(101);

        healthChecker.observe(Collections.singletonList(clusterStats));

        verify(notifier, times(1)).sendNotification(eq("Cluster name 'testCluster' has too many queued queries"), anyString());
    }

    @Test
    public void testNotifyForNoWorkers()
    {
        Notifier notifier = Mockito.mock(Notifier.class);
        HealthChecker healthChecker = new HealthChecker(notifier);
        ClusterStats clusterStats = mock(ClusterStats.class);
        when(clusterStats.healthy()).thenReturn(true);
        when(clusterStats.clusterId()).thenReturn("testCluster");
        when(clusterStats.numWorkerNodes()).thenReturn(0);

        healthChecker.observe(Collections.singletonList(clusterStats));

        verify(notifier, times(1)).sendNotification(eq("Cluster name 'testCluster' has no workers running"), anyString());
    }
}
