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

public record Selectors(
        @ColumnName("resource_group_id") long resourceGroupId,
        @ColumnName("priority") long priority,
        @ColumnName("user_regex") String userRegex,
        @ColumnName("source_regex") String sourceRegex,
        @ColumnName("query_type") String queryType,
        @ColumnName("client_tags") String clientTags,
        @ColumnName("selector_resource_estimate") String selectorResourceEstimate)
{
}
