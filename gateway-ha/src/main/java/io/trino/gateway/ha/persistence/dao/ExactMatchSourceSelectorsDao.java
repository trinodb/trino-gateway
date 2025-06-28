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

public interface ExactMatchSourceSelectorsDao
{
    @SqlQuery("""
            SELECT * FROM exact_match_source_selectors
            """)
    List<ExactMatchSourceSelectors> findAll();

    @SqlUpdate("""
            INSERT INTO exact_match_source_selectors
            (resource_group_id, update_time, source, environment, query_type)
            VALUES (:resourceGroupId, :updateTime, :source, :environment, :queryType)
            """)
    void insert(@BindBean ResourceGroupsManager.ExactSelectorsDetail exactSelectors);
}
