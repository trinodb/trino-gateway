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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestQueryCacheManager
{
    @Mock
    private DistributedCache distributedCache;

    @Mock
    private QueryCacheManager.QueryCacheLoader cacheLoader;

    private QueryCacheManager queryCacheManager;

    @BeforeEach
    void setup()
    {
        MockitoAnnotations.openMocks(this);
        queryCacheManager = new QueryCacheManager(distributedCache, cacheLoader);
    }

    // ========== Single Cache Get Tests ==========

    @Test
    void testGetFromL1Cache()
    {
        // Setup: First call loads from loader, second call should hit L1 cache
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(metadata);

        // First call - loads from database
        Optional<QueryMetadata> result1 = queryCacheManager.get("query1");
        assertThat(result1).hasValue(metadata);

        // Second call - should hit L1 cache (loader not called again)
        Optional<QueryMetadata> result2 = queryCacheManager.get("query1");
        assertThat(result2).hasValue(metadata);

        // Verify loader was only called once (L1 cache hit on second call)
        verify(cacheLoader, times(1)).loadFromDatabase("query1");
    }

    @Test
    void testGetFromL2DistributedCache()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        when(distributedCache.get("query1")).thenReturn(Optional.of(metadata));

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).hasValue(metadata);

        // Verify database loader was never called (found in L2)
        verify(cacheLoader, never()).loadFromDatabase(anyString());
    }

    @Test
    void testGetFromL3Database()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(metadata);

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).hasValue(metadata);

        // Verify L2 cache was backfilled
        verify(distributedCache).set("query1", metadata);
    }

    @Test
    void testGetNotFoundInAnyTier()
    {
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(null);

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).isEmpty();

        // Verify L2 was never backfilled (nothing to cache)
        verify(distributedCache, never()).set(anyString(), any(QueryMetadata.class));
    }

    @Test
    void testGetWithPartialMetadataFromDatabase()
    {
        // Database returns metadata with only backend populated
        QueryMetadata partialMetadata = new QueryMetadata("backend1", null, null);
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(partialMetadata);

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isNull();
                    assertThat(metadata.externalUrl()).isNull();
                });

        // Verify L2 cache was backfilled with partial metadata
        verify(distributedCache).set("query1", partialMetadata);
    }

    @Test
    void testGetWithEmptyMetadataFromDatabase()
    {
        // Database returns empty metadata (all fields null)
        QueryMetadata emptyMetadata = new QueryMetadata(null, null, null);
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(emptyMetadata);

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).isEmpty();

        // Verify L2 cache was not backfilled (metadata is empty)
        verify(distributedCache, never()).set(anyString(), any(QueryMetadata.class));
    }

    // ========== Set Tests ==========

    @Test
    void testSetUpdatesL1AndL2()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");

        queryCacheManager.set("query1", metadata);

        // Verify L2 cache was updated
        verify(distributedCache).set("query1", metadata);

        // Verify L1 cache was updated by checking we can retrieve it
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).hasValue(metadata);
    }

    // ========== Update Tests (Copy-on-Write Pattern) ==========

    @Test
    void testUpdateMergesWithExistingMetadata()
    {
        // Set initial metadata with backend only
        QueryMetadata initial = new QueryMetadata("backend1", null, null);
        queryCacheManager.set("query1", initial);

        // Update with routing group only
        queryCacheManager.update("query1", QueryMetadata.withRoutingGroup("group1"));

        // Verify merged result
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isEqualTo("group1");
                    assertThat(metadata.externalUrl()).isNull();
                });
    }

    @Test
    void testUpdateWithNoExistingMetadata()
    {
        // Update with only backend (no existing metadata)
        queryCacheManager.update("query1", QueryMetadata.withBackend("backend1"));

        // Verify only backend is set
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isNull();
                    assertThat(metadata.externalUrl()).isNull();
                });
    }

    @Test
    void testUpdateOverwritesExistingFields()
    {
        // Set initial metadata
        QueryMetadata initial = new QueryMetadata("backend1", "group1", "http://external1");
        queryCacheManager.set("query1", initial);

        // Update backend only (should overwrite)
        queryCacheManager.update("query1", QueryMetadata.withBackend("backend2"));

        // Verify backend was overwritten, others preserved
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend2");
                    assertThat(metadata.routingGroup()).isEqualTo("group1");
                    assertThat(metadata.externalUrl()).isEqualTo("http://external1");
                });
    }

    // ========== Convenience Method Tests ==========

    @Test
    void testGetBackendConvenienceMethod()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        queryCacheManager.set("query1", metadata);

        String backend = queryCacheManager.getBackend("query1");
        assertThat(backend).isEqualTo("backend1");
    }

    @Test
    void testGetRoutingGroupConvenienceMethod()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        queryCacheManager.set("query1", metadata);

        String routingGroup = queryCacheManager.getRoutingGroup("query1");
        assertThat(routingGroup).isEqualTo("group1");
    }

    @Test
    void testGetExternalUrlConvenienceMethod()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        queryCacheManager.set("query1", metadata);

        String externalUrl = queryCacheManager.getExternalUrl("query1");
        assertThat(externalUrl).isEqualTo("http://external1");
    }

    @Test
    void testSetBackendConvenienceMethod()
    {
        queryCacheManager.setBackend("query1", "backend1");

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isNull();
                    assertThat(metadata.externalUrl()).isNull();
                });
    }

    @Test
    void testSetRoutingGroupConvenienceMethod()
    {
        queryCacheManager.setRoutingGroup("query1", "group1");

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isNull();
                    assertThat(metadata.routingGroup()).isEqualTo("group1");
                    assertThat(metadata.externalUrl()).isNull();
                });
    }

    @Test
    void testSetExternalUrlConvenienceMethod()
    {
        queryCacheManager.setExternalUrl("query1", "http://external1");

        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isNull();
                    assertThat(metadata.routingGroup()).isNull();
                    assertThat(metadata.externalUrl()).isEqualTo("http://external1");
                });
    }

    // ========== Invalidate Tests ==========

    @Test
    void testInvalidateRemovesFromL1AndL2()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");

        // Set metadata
        queryCacheManager.set("query1", metadata);

        // Invalidate
        queryCacheManager.invalidate("query1");

        // Verify L2 invalidation
        verify(distributedCache).invalidate("query1");

        // Verify L1 invalidation - next get should go to L2/L3
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(null);
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).isEmpty();
        verify(cacheLoader).loadFromDatabase("query1"); // Proves L1 cache was cleared
    }

    // ========== Legacy Method Tests ==========

    @Test
    void testUpdateAllCachesLegacyMethod()
    {
        queryCacheManager.updateAllCaches("query1", "backend1", "group1", "http://external1");

        // Verify it delegates to set()
        verify(distributedCache).set(eq("query1"), any(QueryMetadata.class));

        // Verify all fields were set
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isEqualTo("group1");
                    assertThat(metadata.externalUrl()).isEqualTo("http://external1");
                });
    }

    // ========== Edge Case Tests ==========

    @Test
    void testGetWithNullFieldsReturnsNull()
    {
        QueryMetadata metadata = new QueryMetadata("backend1", null, null);
        queryCacheManager.set("query1", metadata);

        assertThat(queryCacheManager.getRoutingGroup("query1")).isNull();
        assertThat(queryCacheManager.getExternalUrl("query1")).isNull();
    }

    @Test
    void testGetNonExistentQueryReturnsNull()
    {
        when(cacheLoader.loadFromDatabase("nonexistent")).thenReturn(null);

        assertThat(queryCacheManager.getBackend("nonexistent")).isNull();
        assertThat(queryCacheManager.getRoutingGroup("nonexistent")).isNull();
        assertThat(queryCacheManager.getExternalUrl("nonexistent")).isNull();
    }

    @Test
    void testLoaderExceptionHandling()
    {
        when(distributedCache.get("query1")).thenReturn(Optional.empty());
        when(cacheLoader.loadFromDatabase("query1")).thenThrow(new RuntimeException("Database error"));

        // Should return null instead of propagating exception
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result).isEmpty();
    }

    // ========== Critical L2 Preservation Tests ==========

    @Test
    void testUpdatePreservesL2DataAfterL1Eviction()
    {
        // Set up L2 (distributed cache) with complete metadata
        QueryMetadata completeMetadata = new QueryMetadata("backend1", "group1", "url1");
        when(distributedCache.get("query1")).thenReturn(Optional.of(completeMetadata));

        // Simulate L1 cache miss (e.g., after eviction) - L2 returns complete data
        // Now update only the backend
        queryCacheManager.setBackend("query1", "backend2");

        // Verify: backend updated, but routing group and external URL preserved from L2
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend2");
                    assertThat(metadata.routingGroup()).isEqualTo("group1"); // Preserved from L2
                    assertThat(metadata.externalUrl()).isEqualTo("url1"); // Preserved from L2
                });
    }

    @Test
    void testPartialUpdateWithL2Lookup()
    {
        // Setup: Complete metadata exists in L2 only (L1 is empty)
        QueryMetadata l2Metadata = new QueryMetadata("backend1", "group1", "url1");
        when(distributedCache.get("query1")).thenReturn(Optional.of(l2Metadata));

        // Update only routing group - should merge with L2 data
        queryCacheManager.setRoutingGroup("query1", "group2");

        // Verify all fields are correct
        verify(distributedCache).set(eq("query1"), any(QueryMetadata.class));

        // Get should now return merged data
        when(distributedCache.get("query1")).thenReturn(Optional.of(new QueryMetadata("backend1", "group2", "url1")));
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isEqualTo("group2");
                    assertThat(metadata.externalUrl()).isEqualTo("url1");
                });
    }

    @Test
    void testPartialUpdateWithL3Lookup()
    {
        // Setup: Complete metadata exists in L3 (database) only
        QueryMetadata l3Metadata = new QueryMetadata("backend1", "group1", "url1");
        when(distributedCache.get("query1")).thenReturn(Optional.empty()); // L2 miss
        when(cacheLoader.loadFromDatabase("query1")).thenReturn(l3Metadata); // L3 hit

        // Update only external URL - should merge with L3 data
        queryCacheManager.setExternalUrl("query1", "url2");

        // Verify L2 was updated with merged data (called twice: once during get() backfill, once during set())
        verify(distributedCache, times(2)).set(eq("query1"), any(QueryMetadata.class));
    }

    @Test
    void testMultiplePartialUpdatesPreserveData()
    {
        // Start with backend only
        queryCacheManager.setBackend("query1", "backend1");

        // Add routing group - backend should be preserved
        queryCacheManager.setRoutingGroup("query1", "group1");

        // Add external URL - both backend and routing group should be preserved
        queryCacheManager.setExternalUrl("query1", "url1");

        // Verify complete metadata
        Optional<QueryMetadata> result = queryCacheManager.get("query1");
        assertThat(result)
                .hasValueSatisfying(metadata -> {
                    assertThat(metadata.backend()).isEqualTo("backend1");
                    assertThat(metadata.routingGroup()).isEqualTo("group1");
                    assertThat(metadata.externalUrl()).isEqualTo("url1");
                });
    }
}
