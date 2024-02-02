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

import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface ResourceGroupsGlobalPropertiesDao
{
    @SqlQuery("""
            SELECT * FROM resource_groups_global_properties
            """)
    List<ResourceGroupsGlobalProperties> findAll();

    @SqlQuery("""
            SELECT * FROM resource_groups_global_properties
            WHERE name = :name
            """)
    List<ResourceGroupsGlobalProperties> findByName(String name);

    @SqlQuery("""
            SELECT * FROM resource_groups_global_properties
            WHERE name = :name
            LIMIT 1
            """)
    ResourceGroupsGlobalProperties findFirstByName(String name);

    @SqlUpdate("""
            INSERT INTO resource_groups_global_properties (name, value)
            VALUES (:name, :value)
            """)
    void insert(String name, String value);

    @SqlUpdate("""
            UPDATE resource_groups_global_properties
            SET value = :value
            WHERE name = :name
            """)
    void update(String name, String value);

    @SqlUpdate("""
            DELETE FROM resource_groups_global_properties
            WHERE name = :name
            """)
    void deleteByName(String name);
}
