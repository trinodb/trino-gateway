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

import io.trino.gateway.ha.router.ResourceGroupsManager;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface ResourceGroupsDao
{
    @SqlQuery("SELECT * FROM resource_groups")
    List<ResourceGroups> findAll();

    @SqlQuery("""
            SELECT * FROM resource_groups
            WHERE resource_group_id = :resourceGroupId
            """)
    List<ResourceGroups> findById(long resourceGroupId);

    @SqlQuery("""
            SELECT * FROM resource_groups
            WHERE resource_group_id = :resourceGroupId
            LIMIT 1
            """)
    ResourceGroups findFirstById(long resourceGroupId);

    @SqlUpdate("""
            INSERT INTO resource_groups (
                name,
                parent,
                jmx_export,
                scheduling_policy,
                scheduling_weight,
                soft_memory_limit,
                max_queued,
                hard_concurrency_limit,
                soft_concurrency_limit,
                soft_cpu_limit,
                hard_cpu_limit,
                environment)
            VALUES (
                :name,
                :parent,
                :jmxExport,
                :schedulingPolicy,
                :schedulingWeight,
                :softMemoryLimit,
                :maxQueued,
                :hardConcurrencyLimit,
                :softConcurrencyLimit,
                :softCpuLimit,
                :hardCpuLimit,
                :environment)
            """)
    void create(@BindBean ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail);

    @SqlUpdate("""
            INSERT INTO resource_groups (
                resource_group_id,
                name,
                parent,
                jmx_export,
                scheduling_policy,
                scheduling_weight,
                soft_memory_limit,
                max_queued,
                hard_concurrency_limit,
                soft_concurrency_limit,
                soft_cpu_limit,
                hard_cpu_limit,
                environment)
            VALUES (
                :resourceGroupId,
                :name,
                :parent,
                :jmxExport,
                :schedulingPolicy,
                :schedulingWeight,
                :softMemoryLimit,
                :maxQueued,
                :hardConcurrencyLimit,
                :softConcurrencyLimit,
                :softCpuLimit,
                :hardCpuLimit,
                :environment)
            """)
    void insert(@BindBean ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail);

    @SqlUpdate("""
            UPDATE resource_groups
            SET
                name                   = :name,
                parent                 = :parent,
                jmx_export             = :jmxExport,
                scheduling_policy      = :schedulingPolicy,
                scheduling_weight      = :schedulingWeight,
                soft_memory_limit      = :softMemoryLimit,
                max_queued             = :maxQueued,
                hard_concurrency_limit = :hardConcurrencyLimit,
                soft_concurrency_limit = :softConcurrencyLimit,
                soft_cpu_limit         = :softCpuLimit,
                hard_cpu_limit         = :hardCpuLimit,
                environment            = :environment
            WHERE
                resource_group_id = :resourceGroupId
            """)
    void update(@BindBean ResourceGroupsManager.ResourceGroupsDetail resourceGroupsDetail);

    @SqlUpdate("""
            DELETE FROM resource_groups
            WHERE resource_group_id = :resourceGroupId
            """)
    void deleteById(long resourceGroupId);
}
