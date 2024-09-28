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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.router.HaResourceGroupsManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;

import static io.airlift.http.client.HttpStatus.NOT_FOUND;
import static io.airlift.http.client.HttpStatus.OK;
import static io.trino.gateway.ha.router.ResourceGroupsManager.GlobalPropertiesDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
final class TestTrinoResource
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    int routerPort = 22000 + (int) (Math.random() * 1000);
    JdbcConnectionManager connectionManager;
    HaResourceGroupsManager resourceGroupManager;

    @BeforeAll
    void setup()
            throws Exception
    {
        // Prepare config and database tables
        HaGatewayTestUtils.TestConfig testConfig =
                HaGatewayTestUtils.buildGatewayConfigAndSeedDb(routerPort, "test-config-template.yml");

        // Setup resource group manager
        String jdbcUrl = "jdbc:h2:" + testConfig.h2DbFilePath();
        DataStoreConfiguration db = new DataStoreConfiguration(jdbcUrl, "sa", "sa", "org.h2.Driver", 4);
        Jdbi jdbi = Jdbi.create(jdbcUrl, "sa", "sa");
        connectionManager = new JdbcConnectionManager(jdbi, db);
        resourceGroupManager = new HaResourceGroupsManager(connectionManager);

        // Generate test data
        prepareData();

        // Start Gateway
        String[] args = {testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
    }

    public void prepareData()
    {
        // First resource group and selector(s)
        ResourceGroupsDetail firstResourceGroup = new ResourceGroupsDetail();
        firstResourceGroup.setName("admins");
        firstResourceGroup.setHardConcurrencyLimit(1);
        firstResourceGroup.setSoftMemoryLimit("2%");
        firstResourceGroup.setMaxQueued(34);

        resourceGroupManager.createResourceGroup(firstResourceGroup, null);

        SelectorsDetail firstGroupSelector = new SelectorsDetail();
        firstGroupSelector.setResourceGroupId(1);
        firstGroupSelector.setPriority(5L);

        resourceGroupManager.createSelector(firstGroupSelector, null);

        // Second resource group and selector(s)
        ResourceGroupsDetail secondResourceGroup = new ResourceGroupsDetail();
        secondResourceGroup.setName("services");
        secondResourceGroup.setHardConcurrencyLimit(34);
        secondResourceGroup.setSoftMemoryLimit("5GB");
        secondResourceGroup.setMaxQueued(6);

        resourceGroupManager.createResourceGroup(secondResourceGroup, null);

        SelectorsDetail secondGroupSelector = new SelectorsDetail();
        secondGroupSelector.setResourceGroupId(2);
        secondGroupSelector.setPriority(9L);

        resourceGroupManager.createSelector(secondGroupSelector, null);

        // Third resource group (no selectors)
        ResourceGroupsDetail thirdResourceGroup = new ResourceGroupsDetail();
        thirdResourceGroup.setName("users");
        thirdResourceGroup.setHardConcurrencyLimit(5);
        thirdResourceGroup.setSoftMemoryLimit("67%");
        thirdResourceGroup.setMaxQueued(8);

        resourceGroupManager.createResourceGroup(thirdResourceGroup, null);

        // Global property
        GlobalPropertiesDetail firstProperty = new GlobalPropertiesDetail();
        firstProperty.setName("cpu_quota_period");
        firstProperty.setValue("cpu_quota_period_value");

        resourceGroupManager.createGlobalProperty(firstProperty, null);
    }

    @Test
    @Order(1)
    void testReadResourceGroupsAll()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/read/")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());

        ResourceGroupsDetail[] groups =
                objectMapper.readValue(response.body().string(), ResourceGroupsDetail[].class);
        assertThat(groups.length).isEqualTo(3);

        Arrays.sort(groups, (x, y) -> Long.compare(x.getResourceGroupId(), y.getResourceGroupId()));
        assertThat(groups[0].getResourceGroupId()).isEqualTo(1);
        assertThat(groups[1].getResourceGroupId()).isEqualTo(2);
        assertThat(groups[2].getResourceGroupId()).isEqualTo(3);
    }

    @Test
    @Order(2)
    void testReadResourceGroupsByGroupId()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/read/1")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());

        ResourceGroupsDetail[] resourceGroups =
                objectMapper.readValue(response.body().string(), ResourceGroupsDetail[].class);
        assertThat(resourceGroups.length).isEqualTo(1);

        assertThat(resourceGroups[0].getResourceGroupId()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void testReadSelectorsAll()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/selector/read/")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());

        SelectorsDetail[] selectors =
                objectMapper.readValue(response.body().string(), SelectorsDetail[].class);
        assertThat(selectors.length).isEqualTo(2);

        Arrays.sort(selectors, (x, y) -> Long.compare(x.getResourceGroupId(), y.getResourceGroupId()));
        assertThat(selectors[0].getResourceGroupId()).isEqualTo(1);
        assertThat(selectors[1].getResourceGroupId()).isEqualTo(2);
    }

    @Test
    @Order(4)
    void testReadSelectorsByGroupId()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/selector/read/2")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());

        SelectorsDetail[] selectors =
                objectMapper.readValue(response.body().string(), SelectorsDetail[].class);
        assertThat(selectors.length).isEqualTo(1);

        assertThat(selectors[0].getResourceGroupId()).isEqualTo(2);
    }

    @Test
    @Order(5)
    void testDeleteResourceGroupOk()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/delete/3")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());
    }

    @Test
    @Order(6)
    void testDeleteResourceGroupNoId()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/delete/")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(NOT_FOUND.code());
    }

    @Test
    @Order(7)
    void testDeleteGlobalPropertyOk()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/globalproperty/delete/cpu_quota_period")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(OK.code());
    }

    @Test
    @Order(8)
    void testDeleteGlobalPropertyNoName()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/globalproperty/delete/")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(NOT_FOUND.code());
    }
}
