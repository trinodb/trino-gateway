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
package io.trino.gateway.ha.persistence.dao;

import io.trino.gateway.ha.router.ResourceGroupsManager.GlobalPropertiesDetail;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

import java.util.ArrayList;
import java.util.List;

@IdName("name")
@Table("resource_groups_global_properties") // located in gateway-ha-persistence.sql
@Cached
public class ResourceGroupsGlobalProperties
        extends Model
{
    private static final String name = "name";
    private static final String value = "value";

    /**
     * Reads all existing global properties and returns them in a List.
     *
     * @return List of ResourceGroupGlobalProperties
     */
    public static List<GlobalPropertiesDetail> upcast(
            List<ResourceGroupsGlobalProperties> globalPropertiesList)
    {
        List<GlobalPropertiesDetail> globalProperties = new ArrayList<>();
        for (ResourceGroupsGlobalProperties dao : globalPropertiesList) {
            GlobalPropertiesDetail globalPropertiesDetail = new GlobalPropertiesDetail();
            globalPropertiesDetail.setName(dao.getString(name));
            globalPropertiesDetail.setValue(dao.getString(value));

            globalProperties.add(globalPropertiesDetail);
        }
        return globalProperties;
    }

    /**
     * Creates a new global property.
     */
    public static void create(
            ResourceGroupsGlobalProperties model, GlobalPropertiesDetail globalPropertiesDetail)
    {
        model.set(name, globalPropertiesDetail.getName());
        model.set(value, globalPropertiesDetail.getValue());

        model.insert();
    }

    /**
     * Updates existing global property.
     */
    public static void update(
            ResourceGroupsGlobalProperties model, GlobalPropertiesDetail globalPropertiesDetail)
    {
        model.set(name, globalPropertiesDetail.getName());
        model.set(value, globalPropertiesDetail.getValue());

        model.saveIt();
    }
}
