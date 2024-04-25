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
package io.trino.gateway.ha.handler;

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.TestingProxyHandlerStats;
import io.trino.gateway.ha.router.TestingQueryManager;
import io.trino.gateway.ha.router.TestingRoutingManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.PROXY_TARGET_HEADER;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.util.Callback.NOOP;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler
{
    QueryIdCachingProxyHandler queryIdCachingProxyHandler;
    String customStatementPath = "/custom/statement/path";
    String backend = "backend";
    List<String> customStatementPaths = ImmutableList.of(customStatementPath);
    QueryHistoryManager queryHistoryManager = new TestingQueryManager();
    RoutingManager routingManager = new TestingRoutingManager(backend);
    RoutingGroupSelector routingGroupSelector = RoutingGroupSelector.byRoutingGroupHeader();
    ProxyHandlerStats proxyHandlerStats = new TestingProxyHandlerStats();

    @BeforeAll
    public void setup()
    {
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(new GatewayCookieConfiguration());
        queryIdCachingProxyHandler = new QueryIdCachingProxyHandler(
                queryHistoryManager,
                routingManager,
                routingGroupSelector,
                80,
                proxyHandlerStats,
                ImmutableList.of(),
                customStatementPaths);
    }

    @Test
    public void testExtractQueryIdFromUrl()
            throws IOException
    {
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/v1/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/v1/statement/queued/20200416_160256_03078_6b4yt/y0d7620a6941e78d3950798a1085383234258a566/1", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/killed", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/preempted", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/v1/query/20200416_160256_03078_6b4yt", "pretty"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/troubleshooting", "queryId=20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/query.html", "20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/login", "redirect=%2Fui%2Fapi%2Fquery%2F20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");

        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/api/query/myOtherThing", null))
                .isNull();
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/api/query/20200416_blah", "bogus_fictional_param"))
                .isNull();
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent("/ui/", "lang=en&p=1&id=0_1_2_a"))
                .isNull();
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent(customStatementPath + "/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(queryIdCachingProxyHandler.extractQueryIdIfPresent(customStatementPath + "/queued/20200416_160256_03078_6b4yt/y0d7620a6941e78d3950798a1085383234258a566/1", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
    }

    @Test
    public void testForwardedHostHeaderOnProxyRequest()
            throws IOException
    {
        String backendServer = "trinocluster";
        String backendPort = "80";
        HttpServletRequest mockServletRequest = Mockito.mock(HttpServletRequest.class);
        when(mockServletRequest.getHeader("proxytarget")).thenReturn(format("http://%s:%s", backendServer, backendPort));
        HttpClient httpClient = new HttpClient();
        Request proxyRequest = httpClient.newRequest("http://localhost:80");
        QueryIdCachingProxyHandler.setForwardedHostHeaderOnProxyRequest(mockServletRequest,
                proxyRequest);
        assertThat(proxyRequest.getHeaders().get("Host"))
                .isEqualTo(format("%s:%s", backendServer, backendPort));
    }

    @Test
    public void testPreconnectionHook()
    {
        String backendServer = "trinocluster";
        String backendPort = "80";

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("proxytarget")).thenReturn(String.format("http://%s:%s", backendServer, backendPort));
        when(request.getRequestURI()).thenReturn(QueryIdCachingProxyHandler.V1_STATEMENT_PATH);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        HttpClient httpClient = new HttpClient();
        Request proxyRequest = httpClient.newRequest("http://localhost:80");
        queryIdCachingProxyHandler.preConnectionHook(request, proxyRequest);
        assertThat(proxyRequest.getHeaders().get("Host")).isEqualTo(String.format("%s:%s", backendServer, backendPort));

        when(request.getRequestURI()).thenReturn(customStatementPath);
        proxyRequest = httpClient.newRequest("http://localhost:80");
        queryIdCachingProxyHandler.preConnectionHook(request, proxyRequest);
        assertThat(proxyRequest.getHeaders().get("Host")).isEqualTo(String.format("%s:%s", backendServer, backendPort));

        when(request.getRequestURI()).thenReturn("/v1/invalid/statement/path");
        proxyRequest = httpClient.newRequest("http://localhost:80");
        queryIdCachingProxyHandler.preConnectionHook(request, proxyRequest);
        assertThat(proxyRequest.getHeaders().get("Host")).isNull();
    }

    @Test
    public void testPostConnectionHook()
            throws IOException
    {
        String backend = "trinocluster:80";
        String user = "usr";
        String source = "jdbc";
        String body = "Select 1";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getRequestURI()).thenReturn(QueryIdCachingProxyHandler.V1_STATEMENT_PATH);
        when(request.getHeader(PROXY_TARGET_HEADER)).thenReturn(backend);
        when(request.getHeader(QueryIdCachingProxyHandler.USER_HEADER)).thenReturn(user);
        when(request.getHeader(QueryIdCachingProxyHandler.SOURCE_HEADER)).thenReturn(source);
        Reader reader = new StringReader(body);
        BufferedReader bufferedReader = new BufferedReader(reader);
        when(request.getReader()).thenReturn(bufferedReader);

        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        String queryId = "v1_statement_id";
        String responseBody = String.format("{\"id\":\"%s\"}", queryId);
        byte[] buffer = responseBody.getBytes(UTF_8);

        when(request.getRequestURI()).thenReturn(QueryIdCachingProxyHandler.V1_STATEMENT_PATH);
        queryIdCachingProxyHandler.postConnectionHook(request, response, buffer, 0, buffer.length, NOOP);
        assertThat(routingManager.findBackendForQueryId(queryId)).isEqualTo(backend);

        queryId = "custom_path_id";
        responseBody = String.format("{\"id\":\"%s\"}", queryId);
        buffer = responseBody.getBytes(UTF_8);
        when(request.getRequestURI()).thenReturn(customStatementPath);
        queryIdCachingProxyHandler.postConnectionHook(request, response, buffer, 0, buffer.length, NOOP);
        assertThat(routingManager.findBackendForQueryId(queryId)).isEqualTo(backend);

        queryId = "invalid_path_id";
        responseBody = String.format("{\"id\":\"%s\"}", queryId);
        buffer = responseBody.getBytes(UTF_8);
        when(request.getRequestURI()).thenReturn("/v1/invalid/statement/path");
        queryIdCachingProxyHandler.postConnectionHook(request, response, buffer, 0, buffer.length, NOOP);
        assertThat(routingManager.findBackendForQueryId(queryId)).isNull();
    }

    @Test
    public void testUserFromRequest()
            throws IOException
    {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

        String authHeader = "Basic dGVzdDoxMjPCow==";
        when(req.getHeader(QueryIdCachingProxyHandler.AUTHORIZATION))
                .thenReturn(authHeader);
        assertThat(QueryIdCachingProxyHandler.getQueryUser(req)).isEqualTo("test");

        String user = "trino_user";
        when(req.getHeader(QueryIdCachingProxyHandler.USER_HEADER))
                .thenReturn(user);
        assertThat(QueryIdCachingProxyHandler.getQueryUser(req)).isEqualTo(user);
    }
}
