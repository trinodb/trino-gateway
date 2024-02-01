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
import org.eclipse.jetty.http.HttpStatus;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;

import static io.trino.gateway.ha.router.ResourceGroupsManager.GlobalPropertiesDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class TestTrinoResource
{
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    int routerPort = 22000 + (int) (Math.random() * 1000);
    JdbcConnectionManager connectionManager;
    HaResourceGroupsManager resourceGroupManager;

    @BeforeAll
    public void setup()
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
        String[] args = {"server", testConfig.configFilePath()};
        HaGatewayLauncher.main(args);
    }

    public void prepareData()
    {
        // First resource group and selector(s)
        ResourceGroupsDetail firstResourceGroup = new ResourceGroupsDetail();
        firstResourceGroup.setResourceGroupId(1234);
        firstResourceGroup.setName("admins");
        firstResourceGroup.setHardConcurrencyLimit(1);
        firstResourceGroup.setSoftMemoryLimit("2%");
        firstResourceGroup.setMaxQueued(34);

        resourceGroupManager.createResourceGroup(firstResourceGroup, null);

        SelectorsDetail firstGroupSelector = new SelectorsDetail();
        firstGroupSelector.setResourceGroupId(1234);
        firstGroupSelector.setPriority(5L);

        resourceGroupManager.createSelector(firstGroupSelector, null);

        // Second resource group and selector(s)
        ResourceGroupsDetail secondResourceGroup = new ResourceGroupsDetail();
        secondResourceGroup.setResourceGroupId(3456);
        secondResourceGroup.setName("services");
        secondResourceGroup.setHardConcurrencyLimit(34);
        secondResourceGroup.setSoftMemoryLimit("5GB");
        secondResourceGroup.setMaxQueued(6);

        resourceGroupManager.createResourceGroup(secondResourceGroup, null);

        SelectorsDetail secondGroupSelector = new SelectorsDetail();
        secondGroupSelector.setResourceGroupId(3456);
        secondGroupSelector.setPriority(9L);

        resourceGroupManager.createSelector(secondGroupSelector, null);

        // Third resource group (no selectors)
        ResourceGroupsDetail thirdResourceGroup = new ResourceGroupsDetail();
        thirdResourceGroup.setResourceGroupId(5678);
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
    public void testReadResourceGroupsAll()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/read/")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);

        ResourceGroupsDetail[] groups =
                objectMapper.readValue(response.body().string(), ResourceGroupsDetail[].class);
        assertThat(groups.length).isEqualTo(3);

        Arrays.sort(groups, (x, y) -> Long.compare(x.getResourceGroupId(), y.getResourceGroupId()));
        assertThat(groups[0].getResourceGroupId()).isEqualTo(1234);
        assertThat(groups[1].getResourceGroupId()).isEqualTo(3456);
        assertThat(groups[2].getResourceGroupId()).isEqualTo(5678);
    }

    @Test
    @Order(2)
    public void testReadResourceGroupsByGroupId()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/read/1234")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);

        ResourceGroupsDetail[] resourceGroups =
                objectMapper.readValue(response.body().string(), ResourceGroupsDetail[].class);
        assertThat(resourceGroups.length).isEqualTo(1);

        assertThat(resourceGroups[0].getResourceGroupId()).isEqualTo(1234);
    }

    @Test
    @Order(3)
    public void testReadSelectorsAll()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/selector/read/")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);

        SelectorsDetail[] selectors =
                objectMapper.readValue(response.body().string(), SelectorsDetail[].class);
        assertThat(selectors.length).isEqualTo(2);

        Arrays.sort(selectors, (x, y) -> Long.compare(x.getResourceGroupId(), y.getResourceGroupId()));
        assertThat(selectors[0].getResourceGroupId()).isEqualTo(1234);
        assertThat(selectors[1].getResourceGroupId()).isEqualTo(3456);
    }

    @Test
    @Order(4)
    public void testReadSelectorsByGroupId()
            throws Exception
    {
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/selector/read/3456")
                        .get()
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);

        SelectorsDetail[] selectors =
                objectMapper.readValue(response.body().string(), SelectorsDetail[].class);
        assertThat(selectors.length).isEqualTo(1);

        assertThat(selectors[0].getResourceGroupId()).isEqualTo(3456);
    }

    @Test
    @Order(5)
    public void testDeleteResourceGroupOk()
            throws Exception
    {
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "");
        Request request =
                new Request.Builder()
                        .url("http://localhost:" + routerPort + "/trino/resourcegroup/delete/5678")
                        .post(requestBody)
                        .build();
        Response response = httpClient.newCall(request).execute();
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);
    }

    @Test
    @Order(6)
    public void testDeleteResourceGroupNoId()
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
        assertThat(response.code()).isEqualTo(HttpStatus.NOT_FOUND_404);
    }

    @Test
    @Order(7)
    public void testDeleteGlobalPropertyOk()
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
        assertThat(response.code()).isEqualTo(HttpStatus.OK_200);
    }

    @Test
    @Order(8)
    public void testDeleteGlobalPropertyNoName()
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
        assertThat(response.code()).isEqualTo(HttpStatus.NOT_FOUND_404);
    }
}
