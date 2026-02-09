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
package io.trino.gateway.ha.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestQueryCacheManager
{
    @Mock
    private DistributedCache distributedCache;

    @Mock
    private QueryCacheManager.QueryCacheLoader cacheLoader;

    private QueryCacheManager queryCacheManager;

    @BeforeEach
    public void setup()
    {
        MockitoAnnotations.openMocks(this);
        queryCacheManager = new QueryCacheManager(distributedCache, cacheLoader);
    }

    // ========== Backend Cache Tests ==========

    @Test
    public void testGetBackendFromL1Cache()
    {
        // Setup: First call loads from loader, second call should hit L1 cache
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn("backend1");

        // First call - loads from database
        String backend1 = queryCacheManager.getBackend("query1");
        assertThat(backend1).isEqualTo("backend1");

        // Second call - should hit L1 cache (loader not called again)
        String backend2 = queryCacheManager.getBackend("query1");
        assertThat(backend2).isEqualTo("backend1");

        // Verify loader was only called once (L1 cache hit on second call)
        verify(cacheLoader, times(1)).loadBackendFromDatabase("query1");
    }

    @Test
    public void testGetBackendFromL2DistributedCache()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.of("backend1"));

        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isEqualTo("backend1");

        // Verify database loader was never called (found in L2)
        verify(cacheLoader, never()).loadBackendFromDatabase(anyString());
    }

    @Test
    public void testGetBackendFromL3Database()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn("backend1");

        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isEqualTo("backend1");

        // Verify L2 cache was backfilled
        verify(distributedCache).set("trino:query:backend:query1", "backend1");
    }

    @Test
    public void testGetBackendNotFoundInAnyTier()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn(null);

        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isNull();
    }

    @Test
    public void testSetBackendUpdatesL1AndL2()
    {
        when(distributedCache.isEnabled()).thenReturn(true);

        queryCacheManager.setBackend("query1", "backend1");

        // Verify L2 was updated
        verify(distributedCache).set("trino:query:backend:query1", "backend1");

        // Verify L1 cache was updated by retrieving
        when(distributedCache.isEnabled()).thenReturn(false);
        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isEqualTo("backend1");
    }

    @Test
    public void testSetBackendOnlyUpdatesL1WhenDistributedCacheDisabled()
    {
        when(distributedCache.isEnabled()).thenReturn(false);

        queryCacheManager.setBackend("query1", "backend1");

        // Verify L2 was NOT updated
        verify(distributedCache, never()).set(anyString(), anyString());

        // Verify L1 cache was updated
        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isEqualTo("backend1");
    }

    // ========== Routing Group Cache Tests ==========

    @Test
    public void testGetRoutingGroupFromL1Cache()
    {
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadRoutingGroupFromDatabase("query1")).thenReturn("group1");

        // First call
        String group1 = queryCacheManager.getRoutingGroup("query1");
        assertThat(group1).isEqualTo("group1");

        // Second call - L1 cache hit
        String group2 = queryCacheManager.getRoutingGroup("query1");
        assertThat(group2).isEqualTo("group1");

        verify(cacheLoader, times(1)).loadRoutingGroupFromDatabase("query1");
    }

    @Test
    public void testGetRoutingGroupFromL2DistributedCache()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:routing_group:query1")).thenReturn(Optional.of("group1"));

        String group = queryCacheManager.getRoutingGroup("query1");
        assertThat(group).isEqualTo("group1");

        verify(cacheLoader, never()).loadRoutingGroupFromDatabase(anyString());
    }

    @Test
    public void testGetRoutingGroupFromL3Database()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:routing_group:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadRoutingGroupFromDatabase("query1")).thenReturn("group1");

        String group = queryCacheManager.getRoutingGroup("query1");
        assertThat(group).isEqualTo("group1");

        // Verify L2 backfill
        verify(distributedCache).set("trino:query:routing_group:query1", "group1");
    }

    @Test
    public void testSetRoutingGroupUpdatesL1AndL2()
    {
        when(distributedCache.isEnabled()).thenReturn(true);

        queryCacheManager.setRoutingGroup("query1", "group1");

        verify(distributedCache).set("trino:query:routing_group:query1", "group1");
    }

    // ========== External URL Cache Tests ==========

    @Test
    public void testGetExternalUrlFromL1Cache()
    {
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadExternalUrlFromDatabase("query1")).thenReturn("http://external.com");

        String url1 = queryCacheManager.getExternalUrl("query1");
        assertThat(url1).isEqualTo("http://external.com");

        String url2 = queryCacheManager.getExternalUrl("query1");
        assertThat(url2).isEqualTo("http://external.com");

        verify(cacheLoader, times(1)).loadExternalUrlFromDatabase("query1");
    }

    @Test
    public void testGetExternalUrlFromL2DistributedCache()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:external_url:query1")).thenReturn(Optional.of("http://external.com"));

        String url = queryCacheManager.getExternalUrl("query1");
        assertThat(url).isEqualTo("http://external.com");

        verify(cacheLoader, never()).loadExternalUrlFromDatabase(anyString());
    }

    @Test
    public void testGetExternalUrlFromL3Database()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:external_url:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadExternalUrlFromDatabase("query1")).thenReturn("http://external.com");

        String url = queryCacheManager.getExternalUrl("query1");
        assertThat(url).isEqualTo("http://external.com");

        verify(distributedCache).set("trino:query:external_url:query1", "http://external.com");
    }

    @Test
    public void testSetExternalUrlUpdatesL1AndL2()
    {
        when(distributedCache.isEnabled()).thenReturn(true);

        queryCacheManager.setExternalUrl("query1", "http://external.com");

        verify(distributedCache).set("trino:query:external_url:query1", "http://external.com");
    }

    // ========== Batch Operations Tests ==========

    @Test
    public void testUpdateAllCachesWithDistributedCacheEnabled()
    {
        when(distributedCache.isEnabled()).thenReturn(true);

        queryCacheManager.updateAllCaches("query1", "backend1", "group1", "http://external.com");

        verify(distributedCache).set("trino:query:backend:query1", "backend1");
        verify(distributedCache).set("trino:query:routing_group:query1", "group1");
        verify(distributedCache).set("trino:query:external_url:query1", "http://external.com");

        // Verify L1 cache was also updated
        when(distributedCache.isEnabled()).thenReturn(false);
        assertThat(queryCacheManager.getBackend("query1")).isEqualTo("backend1");
        assertThat(queryCacheManager.getRoutingGroup("query1")).isEqualTo("group1");
        assertThat(queryCacheManager.getExternalUrl("query1")).isEqualTo("http://external.com");
    }

    @Test
    public void testUpdateAllCachesWithDistributedCacheDisabled()
    {
        when(distributedCache.isEnabled()).thenReturn(false);

        queryCacheManager.updateAllCaches("query1", "backend1", "group1", "http://external.com");

        verify(distributedCache, never()).set(anyString(), anyString());

        // Verify L1 cache was updated
        assertThat(queryCacheManager.getBackend("query1")).isEqualTo("backend1");
        assertThat(queryCacheManager.getRoutingGroup("query1")).isEqualTo("group1");
        assertThat(queryCacheManager.getExternalUrl("query1")).isEqualTo("http://external.com");
    }

    // ========== Cache Backfill Tests ==========

    @Test
    public void testL2BackfillWhenFoundInDatabase()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn("backend1");

        queryCacheManager.getBackend("query1");

        // Verify L2 was backfilled with database result
        verify(distributedCache).set("trino:query:backend:query1", "backend1");
    }

    @Test
    public void testNoL2BackfillWhenNotFoundInDatabase()
    {
        when(distributedCache.isEnabled()).thenReturn(true);
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn(null);

        queryCacheManager.getBackend("query1");

        // Verify L2 was NOT backfilled when result is null
        verify(distributedCache, never()).set(eq("trino:query:backend:query1"), anyString());
    }

    // ========== Exception Handling Tests ==========

    @Test
    public void testGetBackendHandlesLoaderException()
    {
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadBackendFromDatabase("query1")).thenThrow(new RuntimeException("Database error"));

        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isNull();
    }

    @Test
    public void testGetRoutingGroupHandlesLoaderException()
    {
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadRoutingGroupFromDatabase("query1")).thenThrow(new RuntimeException("Database error"));

        String group = queryCacheManager.getRoutingGroup("query1");
        assertThat(group).isNull();
    }

    @Test
    public void testGetExternalUrlHandlesLoaderException()
    {
        when(distributedCache.isEnabled()).thenReturn(false);
        when(cacheLoader.loadExternalUrlFromDatabase("query1")).thenThrow(new RuntimeException("Database error"));

        String url = queryCacheManager.getExternalUrl("query1");
        assertThat(url).isNull();
    }

    // ========== Integration Test: Full L1->L2->L3 Flow ==========

    @Test
    public void testFullCachingFlowL1ToL3()
    {
        when(distributedCache.isEnabled()).thenReturn(true);

        // Scenario 1: Query not in any cache - goes to L3
        when(distributedCache.get("trino:query:backend:query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadBackendFromDatabase("query1")).thenReturn("backend1");

        String backend1 = queryCacheManager.getBackend("query1");
        assertThat(backend1).isEqualTo("backend1");
        verify(distributedCache).set("trino:query:backend:query1", "backend1");

        // Scenario 2: Query now in L1 - doesn't reach L2 or L3
        when(distributedCache.isEnabled()).thenReturn(false);
        String backend2 = queryCacheManager.getBackend("query1");
        assertThat(backend2).isEqualTo("backend1");
        verify(cacheLoader, times(1)).loadBackendFromDatabase("query1"); // Still only called once
    }
}
