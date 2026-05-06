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

import io.trino.gateway.ha.clustermonitor.ClusterStats;
import io.trino.gateway.ha.config.ExplainRoutingConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.util.QueryRequestMock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestExplainRoutingGroupSelector
{
    @Test
    void testLargeExplainRoutesToLargeGroup()
            throws Exception
    {
        ExplainRoutingConfiguration config = createConfig();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse("{\"data\":[[\"{\\\"cpuCost\\\":2500000}\"]]}"));

        ExplainRoutingGroupSelector selector = new ExplainRoutingGroupSelector(config, httpClient);
        HttpServletRequest request = request("SELECT * FROM nation");

        assertThat(selector.findRoutingDestination(request).routingGroup()).isEqualTo("large");
    }

    @Test
    void testSmallExplainRoutesToSmallGroup()
            throws Exception
    {
        ExplainRoutingConfiguration config = createConfig();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse("{\"data\":[[\"{\\\"cpuCost\\\":10}\"]]}"));

        ExplainRoutingGroupSelector selector = new ExplainRoutingGroupSelector(config, httpClient);
        HttpServletRequest request = request("SELECT * FROM nation");

        assertThat(selector.findRoutingDestination(request).routingGroup()).isEqualTo("small");
    }

    @Test
    void testSmallReroutesToLargeWhenSmallClusterIsHot()
            throws Exception
    {
        ExplainRoutingConfiguration config = createConfig();
        config.setCpuMetricName("cpu");
        config.setMaxSmallClusterCpuPercent(80);

        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse("{\"data\":[[\"{\\\"cpuCost\\\":10}\"]]}"));

        ExplainRoutingGroupSelector selector = new ExplainRoutingGroupSelector(config, httpClient);
        selector.observe(List.of(
                ClusterStats.builder("small-a")
                        .routingGroup("small")
                        .runningQueryCount(1)
                        .numWorkerNodes(10)
                        .customMetrics(Map.of("cpu", 95))
                        .build(),
                ClusterStats.builder("large-a")
                        .routingGroup("large")
                        .runningQueryCount(1)
                        .numWorkerNodes(20)
                        .customMetrics(Map.of("cpu", 20))
                        .build()));

        HttpServletRequest request = request("SELECT * FROM nation");
        assertThat(selector.findRoutingDestination(request).routingGroup()).isEqualTo("large");
    }

    @Test
    void testNonEligibleQueryTypeUsesDefaultRoutingGroup()
            throws Exception
    {
        ExplainRoutingConfiguration config = createConfig();
        config.getExplain().setQueryTypes(java.util.Set.of("SELECT"));
        HttpClient httpClient = mock(HttpClient.class);

        ExplainRoutingGroupSelector selector = new ExplainRoutingGroupSelector(config, httpClient);
        HttpServletRequest request = request("INSERT INTO nation SELECT * FROM nation");

        assertThat(selector.findRoutingDestination(request).routingGroup()).isEqualTo("small");
    }

    private static ExplainRoutingConfiguration createConfig()
    {
        ExplainRoutingConfiguration config = new ExplainRoutingConfiguration();
        config.setDefaultRoutingGroup("small");
        config.setFallbackRoutingGroup("small");
        config.setSmallRoutingGroup("small");
        config.setLargeRoutingGroup("large");
        config.getExplain().setEndpoint("http://localhost:8080/v1/statement");
        config.getExplain().setQueryTypes(java.util.Set.of("SELECT", "INSERT", "CREATE_TABLE_AS_SELECT", "MERGE"));
        config.getMetricsThresholds().setMinCpuCost(1000);
        return config;
    }

    private static HttpServletRequest request(String sql)
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        return new QueryRequestMock()
                .query(sql)
                .requestAnalyzerConfig(requestAnalyzerConfig)
                .getHttpServletRequest();
    }

    private static HttpResponse<String> mockResponse(String body)
    {
        return new StubHttpResponse(body);
    }

    private record StubHttpResponse(String body)
            implements HttpResponse<String>
    {
        @Override
        public int statusCode()
        {
            return 200;
        }

        @Override
        public HttpRequest request()
        {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse()
        {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers()
        {
            return HttpHeaders.of(Collections.emptyMap(), (k, v) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession()
        {
            return Optional.empty();
        }

        @Override
        public URI uri()
        {
            return URI.create("http://localhost:8080/v1/statement");
        }

        @Override
        public Version version()
        {
            return Version.HTTP_1_1;
        }
    }
}
