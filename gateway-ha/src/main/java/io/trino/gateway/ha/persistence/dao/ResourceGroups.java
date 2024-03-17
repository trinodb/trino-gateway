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

import org.jdbi.v3.core.mapper.reflect.ColumnName;

import static java.util.Objects.requireNonNull;

public record ResourceGroups(
        @ColumnName("resource_group_id") long resourceGroupId,
        @ColumnName("name") String name,
        @ColumnName("parent") Long parent,
        @ColumnName("jmx_export") Boolean jmxExport,
        @ColumnName("scheduling_policy") String schedulingPolicy,
        @ColumnName("scheduling_weight") Integer schedulingWeight,
        @ColumnName("soft_memory_limit") String softMemoryLimit,
        @ColumnName("max_queued") int maxQueued,
        @ColumnName("hard_concurrency_limit") int hardConcurrencyLimit,
        @ColumnName("soft_concurrency_limit") Integer softConcurrencyLimit,
        @ColumnName("soft_cpu_limit") String softCpuLimit,
        @ColumnName("hard_cpu_limit") String hardCpuLimit,
        @ColumnName("environment") String environment)
{
    public ResourceGroups
    {
        requireNonNull(name);
        requireNonNull(softMemoryLimit);
    }
}
