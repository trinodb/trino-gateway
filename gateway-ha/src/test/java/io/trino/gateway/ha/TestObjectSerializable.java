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
package io.trino.gateway.ha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.ObjectMapperProvider;
import io.trino.gateway.ha.clustermonitor.ServerInfo;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.GlobalPropertyRequest;
import io.trino.gateway.ha.domain.request.QueryDistributionRequest;
import io.trino.gateway.ha.domain.request.QueryGlobalPropertyRequest;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.request.QueryResourceGroupsRequest;
import io.trino.gateway.ha.domain.request.QuerySelectorsRequest;
import io.trino.gateway.ha.domain.request.ResourceGroupsRequest;
import io.trino.gateway.ha.domain.request.RestLoginRequest;
import io.trino.gateway.ha.domain.request.SelectorsRequest;
import io.trino.gateway.ha.domain.response.BackendResponse;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class TestObjectSerializable
{
    private final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    @Test
    void testServerInfo()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new ServerInfo(true)))
                .contains("starting");
    }

    @Test
    void testProxyBackendConfiguration()
            throws JsonProcessingException
    {
        ProxyBackendConfiguration proxyBackendConfiguration = new ProxyBackendConfiguration();
        proxyBackendConfiguration.setExternalUrl("http://localhost:8080");
        proxyBackendConfiguration.setActive(false);
        proxyBackendConfiguration.setRoutingGroup("batch-1");
        assertThat(objectMapper.writeValueAsString(proxyBackendConfiguration))
                .contains(ImmutableList.of("externalUrl", "active", "routingGroup"));
    }

    @Test
    void testResult()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(Result.ok()))
                .contains(ImmutableList.of("code", "msg"));
        assertThat(objectMapper.writeValueAsString(Result.ok("some_msg")))
                .contains(ImmutableList.of("code", "msg"));
        assertThat(objectMapper.writeValueAsString(Result.ok(123)))
                .contains(ImmutableList.of("code", "msg", "data"));
    }

    @Test
    void testTableData()
            throws JsonProcessingException
    {
        TableData<String> tableData = new TableData<>(ImmutableList.of("t1", "t2"), 2);
        assertThat(objectMapper.writeValueAsString(tableData))
                .contains(ImmutableList.of("total", "rows"));
    }

    @Test
    void testGlobalPropertyRequest()
            throws JsonProcessingException
    {
        ResourceGroupsManager.GlobalPropertiesDetail data = new ResourceGroupsManager.GlobalPropertiesDetail("cpu_quota_period");
        assertThat(objectMapper.writeValueAsString(new GlobalPropertyRequest(null, data)))
                .contains("data");
        assertThat(objectMapper.writeValueAsString(new GlobalPropertyRequest("some_schema", data)))
                .contains(ImmutableList.of("useSchema", "data"));
    }

    @Test
    void testQueryDistributionRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new QueryDistributionRequest(null)))
                .contains("\"latestHour\":1");
        assertThat(objectMapper.writeValueAsString(new QueryDistributionRequest(3)))
                .contains("\"latestHour\":3");
    }

    @Test
    void testQueryGlobalPropertyRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new QueryGlobalPropertyRequest(null, null)))
                .isEqualTo("{}");
        assertThat(objectMapper.writeValueAsString(new QueryGlobalPropertyRequest("some_schema", null)))
                .contains("\"useSchema\":\"some_schema\"");
        assertThat(objectMapper.writeValueAsString(new QueryGlobalPropertyRequest("some_schema", "some_name")))
                .contains(ImmutableList.of("\"useSchema\":\"some_schema\"", "\"name\":\"some_name\""));
    }

    @Test
    void testQueryHistoryRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new QueryHistoryRequest(null, null, "user1", "url1", "query_id", "source")))
                .contains(ImmutableList.of("\"page\":1", "\"size\":10", "user", "externalUrl", "queryId", "source"));
        assertThat(objectMapper.writeValueAsString(new QueryHistoryRequest(5, 6, "user1", "url1", "query_id", "source")))
                .contains(ImmutableList.of("\"page\":5", "\"size\":6", "user", "externalUrl", "queryId", "source"));
    }

    @Test
    void testQueryResourceGroupsRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new QueryResourceGroupsRequest(null, 123L)))
                .contains("resourceGroupId");
        assertThat(objectMapper.writeValueAsString(new QueryResourceGroupsRequest("some_schema", 123L)))
                .contains(ImmutableList.of("useSchema", "resourceGroupId"));
    }

    @Test
    void testQuerySelectorsRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new QuerySelectorsRequest(null, 123L)))
                .contains("resourceGroupId");
        assertThat(objectMapper.writeValueAsString(new QuerySelectorsRequest("some_schema", 123L)))
                .contains(ImmutableList.of("useSchema", "resourceGroupId"));
    }

    @Test
    void testResourceGroupsRequest()
            throws JsonProcessingException
    {
        ResourceGroupsManager.ResourceGroupsDetail data = new ResourceGroupsManager.ResourceGroupsDetail();
        assertThat(objectMapper.writeValueAsString(new ResourceGroupsRequest(null, data)))
                .contains(ImmutableList.of("data", "resourceGroupId"));
        assertThat(objectMapper.writeValueAsString(new ResourceGroupsRequest("some_schema", data)))
                .contains(ImmutableList.of("useSchema", "data", "resourceGroupId"));
    }

    @Test
    void testRestLoginRequest()
            throws JsonProcessingException
    {
        assertThat(objectMapper.writeValueAsString(new RestLoginRequest("user", "pass")))
                .contains(ImmutableList.of("username", "password"));
    }

    @Test
    void testSelectorsRequest()
            throws JsonProcessingException
    {
        ResourceGroupsManager.SelectorsDetail data = new ResourceGroupsManager.SelectorsDetail();
        ResourceGroupsManager.SelectorsDetail oldData = new ResourceGroupsManager.SelectorsDetail();
        assertThat(objectMapper.writeValueAsString(new SelectorsRequest(null, data, oldData)))
                .contains(ImmutableList.of("data", "oldData"));
        assertThat(objectMapper.writeValueAsString(new SelectorsRequest("some_schema", data, oldData)))
                .contains(ImmutableList.of("useSchema", "data", "oldData"));
    }

    @Test
    void testBackendResponse()
            throws JsonProcessingException
    {
        BackendResponse backendResponse = new BackendResponse();
        backendResponse.setQueued(4);
        backendResponse.setRunning(5);
        backendResponse.setName("foo");
        backendResponse.setProxyTo("example.com");
        backendResponse.setActive(false);
        backendResponse.setRoutingGroup("batch-1");
        backendResponse.setExternalUrl("example.com");
        backendResponse.setStatus("HEALTHY");
        assertThat(objectMapper.writeValueAsString(backendResponse))
                .contains(ImmutableList.of("queued", "running", "active", "routingGroup", "externalUrl", "name", "proxyTo", "status"));
    }

    @Test
    void testDistributionResponse()
            throws JsonProcessingException
    {
        DistributionResponse.LineChart lineChart = new DistributionResponse.LineChart();
        lineChart.setMinute("11:22");
        lineChart.setBackendUrl("example.com");
        lineChart.setName("name1");
        lineChart.setQueryCount(6L);
        DistributionResponse.DistributionChart distributionChart = new DistributionResponse.DistributionChart();
        distributionChart.setName("name2");
        distributionChart.setBackendUrl("example.com");
        distributionChart.setQueryCount(1L);
        DistributionResponse distributionResponse = new DistributionResponse();
        distributionResponse.setTotalBackendCount(5);
        distributionResponse.setOfflineBackendCount(3);
        distributionResponse.setOnlineBackendCount(2);
        distributionResponse.setLineChart(ImmutableMap.of("k1", ImmutableList.of(lineChart)));
        distributionResponse.setDistributionChart(List.of(distributionChart));
        distributionResponse.setTotalQueryCount(123L);
        distributionResponse.setAverageQueryCountSecond(5.0);
        distributionResponse.setAverageQueryCountMinute(10.0);
        distributionResponse.setStartTime("2024-04-01 12:34:56");
        assertThat(objectMapper.writeValueAsString(distributionResponse))
                .contains(ImmutableList.of(
                        "totalBackendCount",    // DistributionResponse
                        "offlineBackendCount",
                        "onlineBackendCount",
                        "totalQueryCount",
                        "averageQueryCountMinute",
                        "averageQueryCountSecond",
                        "distributionChart",
                        "lineChart",
                        "startTime",
                        "minute",       // LineChart
                        "backendUrl",
                        "queryCount",
                        "\"name\":\"name1\"",
                        "\"name\":\"name2\"",   // DistributionChart
                        "backendUrl",
                        "queryCount"));
    }
}
