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

import org.jdbi.v3.core.mapper.MapMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;

import java.util.List;
import java.util.Map;

public interface QueryHistoryDao
{
    @SqlQuery("""
            SELECT * FROM query_history
            ORDER BY created DESC
            LIMIT 2000
            """)
    List<QueryHistory> findRecentQueries();

    @SqlQuery("""
            SELECT * FROM query_history
            ORDER BY created DESC
            FETCH FIRST 2000 ROWS ONLY
            """)
    List<QueryHistory> findRecentQueriesWithFetch();

    default List<QueryHistory> findRecentQueries(boolean isLimitUnsupported)
    {
        if (isLimitUnsupported) {
            return findRecentQueriesWithFetch();
        }
        return findRecentQueries();
    }

    @SqlQuery("""
            SELECT * FROM query_history
            WHERE user_name = :userName
            ORDER BY created DESC
            LIMIT 2000
            """)
    List<QueryHistory> findRecentQueriesByUserName(String userName);

    @SqlQuery("""
            SELECT * FROM query_history
            WHERE user_name = :userName
            ORDER BY created DESC
            FETCH FIRST 2000 ROWS ONLY
            """)
    List<QueryHistory> findRecentQueriesByUserNameWithFetch(String userName);

    default List<QueryHistory> findRecentQueriesByUserName(String userName, boolean isLimitUnsupported)
    {
        if (isLimitUnsupported) {
            return findRecentQueriesByUserNameWithFetch(userName);
        }
        return findRecentQueriesByUserName(userName);
    }

    @SqlQuery("""
            SELECT backend_url FROM query_history
            WHERE query_id = :queryId
            """)
    String findBackendUrlByQueryId(String queryId);

    @SqlQuery("""
            SELECT * FROM query_history
            WHERE 1 = 1 <condition>
            ORDER BY created DESC
            LIMIT :limit
            OFFSET :offset
            """)
    List<QueryHistory> pageQueryHistory(@Define("condition") String condition, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("""
            SELECT count(1) FROM query_history
            WHERE 1 = 1 <condition>
            """)
    Long count(@Define("condition") String condition);

    @SqlQuery("""
            SELECT FLOOR(created / 1000 / 60) AS minute,
                   backend_url AS backend_url,
                   COUNT(1) AS query_count
            FROM query_history
            WHERE created > :created
            GROUP BY FLOOR(created / 1000 / 60), backend_url
            """)
    @UseRowMapper(MapMapper.class)
    List<Map<String, Object>> findDistribution(long created);

    @SqlUpdate("""
            INSERT INTO query_history (query_id, query_text, backend_url, user_name, source, created, routing_group, external_url)
            VALUES (:queryId, :queryText, :backendUrl, :userName, :source, :created, :routingGroup, :externalUrl)
            """)
    void insertHistory(String queryId, String queryText, String backendUrl, String userName, String source, long created, String routingGroup, String externalUrl);

    @SqlUpdate("""
            DELETE FROM query_history
            WHERE created < :created
            """)
    void deleteOldHistory(long created);
}
