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

public record ExactMatchSourceSelectors(
        @ColumnName("resource_group_id") String resourceGroupId,
        @ColumnName("update_time") String updateTime,
        @ColumnName("source") String source,
        @ColumnName("environment") String environment,
        @ColumnName("query_type") String queryType)
{
    public ExactMatchSourceSelectors
    {
        requireNonNull(resourceGroupId);
        requireNonNull(updateTime);
        requireNonNull(source);
    }
}
