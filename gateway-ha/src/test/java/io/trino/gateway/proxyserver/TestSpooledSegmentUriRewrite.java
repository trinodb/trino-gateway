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
package io.trino.gateway.proxyserver;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.handler.ProxyUtils;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.OAuth2RoutingStore;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestSpooledSegmentUriRewrite
{
    private static final String QUERY_ID = "20260828_100000_00001_abcde";
    private static final String BACKEND_NAME = "trino b";
    private static final String ENCODED_BACKEND_NAME = "trino+b";
    private static final String CLUSTER_HOST = "https://trino-b.example.com";
    private static final String SPOOLED_RESPONSE_BODY =
            """
            {
              "id": "%s",
              "nextUri": "http://gateway:8080/v1/statement/executing/%s/xxx/1",
              "data": {
                "encoding": "json+zstd",
                "segments": [
                  {
                    "type": "spooled",
                    "uri": "http://gateway:8080/v1/spooled/download/token1",
                    "ackUri": "http://gateway:8080/v1/spooled/ack/token1",
                    "metadata": {"rowOffset": 0, "rowsCount": 100, "segmentSize": 1000}
                  },
                  {
                    "type": "inline",
                    "data": "abc",
                    "metadata": {"rowOffset": 100, "rowsCount": 1, "segmentSize": 10}
                  }
                ]
              }
            }
            """.formatted(QUERY_ID, QUERY_ID);

    private ProxyRequestHandler handler;

    @BeforeAll
    void setUp()
    {
        // ProxyRequestHandler reads the (disabled-by-default) cookie singleton in its constructor.
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(new GatewayCookieConfiguration());
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName(BACKEND_NAME);
        backend.setProxyTo(CLUSTER_HOST);
        GatewayBackendManager gatewayBackendManager = mock(GatewayBackendManager.class);
        when(gatewayBackendManager.getAllBackends()).thenReturn(List.of(backend));
        handler = new ProxyRequestHandler(
                mock(HttpClient.class),
                mock(RoutingManager.class),
                gatewayBackendManager,
                mock(QueryHistoryManager.class),
                mock(OAuth2RoutingStore.class),
                new HaGatewayConfiguration());
    }

    @AfterAll
    void cleanup()
    {
        handler.shutdown();
    }

    @Test
    void testSpooledUrisRewritten()
    {
        ProxyResponse response = new ProxyResponse(
                200,
                ImmutableListMultimap.of(HeaderName.of("Content-Length"), String.valueOf(SPOOLED_RESPONSE_BODY.length())),
                SPOOLED_RESPONSE_BODY);
        ProxyResponse rewritten = handler.rewriteSpooledSegmentUris(response, CLUSTER_HOST);

        assertThat(rewritten.body()).contains("http://gateway:8080/v1/spooled/download/token1?queryId=" + QUERY_ID + "&spooledBackend=" + ENCODED_BACKEND_NAME);
        assertThat(rewritten.body()).contains("http://gateway:8080/v1/spooled/ack/token1?queryId=" + QUERY_ID + "&spooledBackend=" + ENCODED_BACKEND_NAME);
        // the backend URL must not be exposed to clients
        assertThat(rewritten.body()).doesNotContain(CLUSTER_HOST);
        // nextUri must not be touched
        assertThat(rewritten.body()).contains("http://gateway:8080/v1/statement/executing/%s/xxx/1\"".formatted(QUERY_ID));
        // stale Content-Length must be dropped
        assertThat(rewritten.headers().keySet()).noneMatch(name -> name.toString().equalsIgnoreCase("Content-Length"));
    }

    @Test
    void testUnknownBackendOnlyAddsQueryId()
    {
        ProxyResponse rewritten = handler.rewriteSpooledSegmentUris(
                new ProxyResponse(200, ImmutableListMultimap.of(), SPOOLED_RESPONSE_BODY), "https://unknown.example.com");

        assertThat(rewritten.body()).contains("http://gateway:8080/v1/spooled/download/token1?queryId=" + QUERY_ID + "\"");
        assertThat(rewritten.body()).doesNotContain("spooledBackend");
    }

    @Test
    void testRewrittenUriIsRoutableByQueryId()
    {
        ProxyResponse rewritten = handler.rewriteSpooledSegmentUris(
                new ProxyResponse(200, ImmutableListMultimap.of(), SPOOLED_RESPONSE_BODY), CLUSTER_HOST);
        assertThat(rewritten.body()).contains("?queryId=" + QUERY_ID);

        // the appended parameter is picked up by the existing query id extraction used for routing
        Optional<String> extracted = ProxyUtils.extractQueryIdIfPresent(
                "/v1/spooled/download/token1",
                "queryId=" + QUERY_ID + "&spooledBackend=" + ENCODED_BACKEND_NAME,
                List.of("/v1/statement"));
        assertThat(extracted).contains(QUERY_ID);
    }

    @Test
    void testNonSpooledResponseUnchanged()
    {
        String body = "{\"id\": \"%s\", \"data\": [[1]]}".formatted(QUERY_ID);
        ProxyResponse response = new ProxyResponse(200, ImmutableListMultimap.of(), body);
        assertThat(handler.rewriteSpooledSegmentUris(response, CLUSTER_HOST)).isSameAs(response);
    }

    @Test
    void testNonOkResponseUnchanged()
    {
        ProxyResponse response = new ProxyResponse(500, ImmutableListMultimap.of(), SPOOLED_RESPONSE_BODY);
        assertThat(handler.rewriteSpooledSegmentUris(response, CLUSTER_HOST)).isSameAs(response);
    }

    @Test
    void testMalformedBodyUnchanged()
    {
        ProxyResponse response = new ProxyResponse(200, ImmutableListMultimap.of(), "not json /v1/spooled/");
        assertThat(handler.rewriteSpooledSegmentUris(response, CLUSTER_HOST)).isSameAs(response);
    }
}
