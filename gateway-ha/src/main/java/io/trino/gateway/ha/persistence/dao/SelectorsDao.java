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

public interface SelectorsDao
{
    @SqlQuery("""
            SELECT * FROM selectors
            """)
    List<Selectors> findAll();

    @SqlQuery("""
            SELECT * FROM selectors
            WHERE resource_group_id = :resourceGroupId
            """)
    List<Selectors> findByResourceGroupId(long resourceGroupId);

    @SqlQuery("""
            SELECT * FROM selectors
            WHERE
                resource_group_id = :resourceGroupId
                AND priority      = :priority
                AND user_regex    IS NOT DISTINCT FROM :userRegex
                AND source_regex  IS NOT DISTINCT FROM :sourceRegex
                AND query_type    IS NOT DISTINCT FROM :queryType
                AND client_tags   IS NOT DISTINCT FROM :clientTags
                AND selector_resource_estimate IS NOT DISTINCT FROM :selectorResourceEstimate
            LIMIT 1
            """)
    Selectors findFirst(@BindBean ResourceGroupsManager.SelectorsDetail selector);

    @SqlUpdate("""
            INSERT INTO selectors
            (resource_group_id, priority, user_regex, source_regex, query_type, client_tags, selector_resource_estimate)
            VALUES (:resourceGroupId, :priority, :userRegex, :sourceRegex, :queryType, :clientTags, :selectorResourceEstimate)
            """)
    void insert(@BindBean ResourceGroupsManager.SelectorsDetail selector);

    @SqlUpdate("""
            UPDATE selectors
            SET
                resource_group_id = :updatedSelector.resourceGroupId,
                priority          = :updatedSelector.priority,
                user_regex        = :updatedSelector.userRegex,
                source_regex      = :updatedSelector.sourceRegex,
                query_type        = :updatedSelector.queryType,
                client_tags       = :updatedSelector.clientTags,
                selector_resource_estimate = :updatedSelector.selectorResourceEstimate
            WHERE
                resource_group_id = :selector.resourceGroupId
                AND priority      = :selector.priority
                AND user_regex    IS NOT DISTINCT FROM :selector.userRegex
                AND source_regex  IS NOT DISTINCT FROM :selector.sourceRegex
                AND query_type    IS NOT DISTINCT FROM :selector.queryType
                AND client_tags   IS NOT DISTINCT FROM :selector.clientTags
                AND selector_resource_estimate IS NOT DISTINCT FROM :selector.selectorResourceEstimate
            """)
    void update(
            @BindBean("selector") ResourceGroupsManager.SelectorsDetail selector,
            @BindBean("updatedSelector") ResourceGroupsManager.SelectorsDetail updatedSelector);

    @SqlUpdate("""
            DELETE FROM selectors
            WHERE
                resource_group_id = :resourceGroupId
                AND priority      = :priority
                AND user_regex    IS NOT DISTINCT FROM :userRegex
                AND source_regex  IS NOT DISTINCT FROM :sourceRegex
                AND query_type    IS NOT DISTINCT FROM :queryType
                AND client_tags   IS NOT DISTINCT FROM :clientTags
                AND selector_resource_estimate IS NOT DISTINCT FROM :selectorResourceEstimate
            """)
    void delete(@BindBean ResourceGroupsManager.SelectorsDetail selector);
}
