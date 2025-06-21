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
package io.trino.gateway.ha.router;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestRoutingManagerExternalUrlCache
{
    private RoutingManager routingManager;
    private GatewayBackendManager backendManager;
    private QueryHistoryManager queryHistoryManager;
    private QueryHistoryManager mockQueryHistoryManager;

    @BeforeAll
    void setUp()
    {
        backendManager = Mockito.mock(GatewayBackendManager.class);
        queryHistoryManager = Mockito.mock(QueryHistoryManager.class);
        mockQueryHistoryManager = Mockito.mock(QueryHistoryManager.class);

        routingManager = new TestRoutingManager(backendManager, queryHistoryManager);
    }

    @Test
    void testSetAndGetExternalUrlFromCache()
    {
        String queryId = "test-query-123";
        String externalUrl = "https://external-gateway.example.com";

        routingManager.setExternalUrlForQueryId(queryId, externalUrl);

        String retrievedUrl = routingManager.findExternalUrlForQueryId(queryId);

        assertThat(retrievedUrl).isEqualTo(externalUrl);
    }

    @Test
    void testCacheMissAndFallbackToQueryHistory()
    {
        String queryId = "cache-miss-query-456";
        String expectedExternalUrl = "https://fallback-gateway.example.com";
        when(queryHistoryManager.getExternalUrlForQueryId(queryId)).thenReturn(expectedExternalUrl);
        String retrievedUrl = routingManager.findExternalUrlForQueryId(queryId);

        assertThat(retrievedUrl).isEqualTo(expectedExternalUrl);
        Mockito.verify(queryHistoryManager).getExternalUrlForQueryId(queryId);
    }

    @Test
    void testCacheOverwrite()
    {
        String queryId = "overwrite-test-query";
        String initialUrl = "https://initial-gateway.example.com";
        String updatedUrl = "https://updated-gateway.example.com";

        routingManager.setExternalUrlForQueryId(queryId, initialUrl);
        assertThat(routingManager.findExternalUrlForQueryId(queryId)).isEqualTo(initialUrl);

        routingManager.setExternalUrlForQueryId(queryId, updatedUrl);
        assertThat(routingManager.findExternalUrlForQueryId(queryId)).isEqualTo(updatedUrl);
    }

    @Test
    void testMultipleQueryIdsWithDifferentExternalUrls()
    {
        String[] queryIds = {"multi-test-1", "multi-test-2", "multi-test-3"};
        String[] externalUrls = {
                "https://gateway1.example.com",
                "https://gateway2.example.com",
                "https://gateway3.example.com"
        };

        for (int i = 0; i < queryIds.length; i++) {
            routingManager.setExternalUrlForQueryId(queryIds[i], externalUrls[i]);
        }

        for (int i = 0; i < queryIds.length; i++) {
            String retrievedUrl = routingManager.findExternalUrlForQueryId(queryIds[i]);
            assertThat(retrievedUrl).isEqualTo(externalUrls[i]);
        }
    }

    @Test
    void testEmptyStringExternalUrl()
    {
        String queryId = "empty-url-test";
        String emptyUrl = "";
        routingManager.setExternalUrlForQueryId(queryId, emptyUrl);
        String retrievedUrl = routingManager.findExternalUrlForQueryId(queryId);

        assertThat(retrievedUrl).isEqualTo(emptyUrl);
    }

    @Test
    void testCacheWithMockQueryHistoryManager()
    {
        RoutingManager mockRoutingManager = new TestRoutingManager(backendManager, mockQueryHistoryManager);

        String queryId = "mock-test-query";
        String expectedUrl = "https://mock-gateway.example.com";

        when(mockQueryHistoryManager.getExternalUrlForQueryId(queryId)).thenReturn(expectedUrl);
        String retrievedUrl = mockRoutingManager.findExternalUrlForQueryId(queryId);

        assertThat(retrievedUrl).isEqualTo(expectedUrl);
        Mockito.verify(mockQueryHistoryManager).getExternalUrlForQueryId(queryId);
    }

    private static class TestRoutingManager
            extends RoutingManager
    {
        public TestRoutingManager(GatewayBackendManager gatewayBackendManager, QueryHistoryManager queryHistoryManager)
        {
            super(gatewayBackendManager, queryHistoryManager);
        }

        @Override
        protected String findBackendForUnknownQueryId(String queryId)
        {
            // Simple implementation for testing - return a default backend
            return "http://default-backend:8080";
        }
    }
}
