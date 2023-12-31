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
package io.trino.gateway.ha.router;

import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.QueryHistory;

import java.util.List;
import java.util.Optional;

public class HaQueryHistoryManager
        implements QueryHistoryManager
{
    private final JdbcConnectionManager connectionManager;

    public HaQueryHistoryManager(JdbcConnectionManager connectionManager)
    {
        this.connectionManager = connectionManager;
    }

    @Override
    public void submitQueryDetail(QueryDetail queryDetail)
    {
        try {
            connectionManager.open();
            QueryHistory dao = new QueryHistory();
            QueryHistory.create(dao, queryDetail);
        }
        finally {
            connectionManager.close();
        }
    }

    @Override
    public List<QueryDetail> fetchQueryHistory(Optional<String> user)
    {
        try {
            connectionManager.open();
            String sql = "select * from query_history";
            if (user.isPresent()) {
                sql += " where user_name = '" + user.orElseThrow() + "'";
            }
            return QueryHistory.upcast(QueryHistory.findBySQL(String.join(" ",
                    sql,
                    "order by created desc",
                    "limit 2000")));
        }
        finally {
            connectionManager.close();
        }
    }

    @Override
    public String getBackendForQueryId(String queryId)
    {
        String backend = null;
        try {
            connectionManager.open();
            QueryHistory queryHistory = QueryHistory.findById(queryId);
            if (queryHistory != null) {
                backend = queryHistory.get("backend_url").toString();
            }
        }
        finally {
            connectionManager.close();
        }
        return backend;
    }
}
