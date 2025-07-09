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
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RegisterColumnMapper(ExactMatchSourceSelectorsDao.TimestampColumnMapper.class)
public interface ExactMatchSourceSelectorsDao
{
    class TimestampColumnMapper
            implements ColumnMapper<String>
    {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String map(ResultSet r, int columnNumber, StatementContext ctx)
                throws SQLException
        {
            String columnName = r.getMetaData().getColumnName(columnNumber);
            if ("update_time".equals(columnName)) {
                try {
                    Timestamp timestamp = r.getTimestamp(columnNumber);
                    return timestamp != null ? timestamp.toLocalDateTime().format(FORMATTER) : null;
                }
                catch (SQLException e) {
                    // Handle case when timestamp is invalid
                    return "2020-07-06 00:00:00";
                }
            }
            return r.getString(columnNumber);
        }
    }
    @SqlQuery("""
            SELECT * FROM exact_match_source_selectors
            """)
    List<ExactMatchSourceSelectors> findAll();

    @SqlUpdate("""
            INSERT INTO exact_match_source_selectors
            (resource_group_id, update_time, source, environment, query_type)
            VALUES (:resourceGroupId, CAST(:updateTime AS TIMESTAMP), :source, :environment, :queryType)
            """)
    void insert(@BindBean ResourceGroupsManager.ExactSelectorsDetail exactSelectors);
}
