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

import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import io.trino.gateway.ha.util.PageUtil;
import org.apache.commons.lang3.StringUtils;
import org.javalite.activejdbc.Base;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Override
    public TableData<QueryDetail> findQueryHistory(QueryHistoryRequest query)
    {
        try {
            connectionManager.open();
            String sql = "select * from query_history where 1=1";
            if (StringUtils.isNotBlank(query.getUser())) {
                sql += " and user_name = '" + query.getUser() + "'";
            }
            if (StringUtils.isNotBlank(query.getBackendUrl())) {
                sql += " and backend_url = '" + query.getBackendUrl() + "'";
            }
            if (StringUtils.isNotBlank(query.getQueryId())) {
                sql += " and query_id = '" + query.getQueryId() + "'";
            }
            int start = PageUtil.getStart(query.getPage(), query.getSize());
            List<QueryDetail> rows = QueryHistory.upcast(QueryHistory.findBySQL(String.join(" ",
                    sql,
                    "order by created desc",
                    "limit ", String.valueOf(start), ",", String.valueOf(query.getSize()))));
            Long total = QueryHistory.count();
            return TableData.build(rows, total);
        }
        finally {
            connectionManager.close();
        }
    }

    @Override
    public List<DistributionResponse.LineChart> findDistribution(Long ts)
    {
        List<DistributionResponse.LineChart> resList = new ArrayList<>();
        try {
            connectionManager.open();
            String sql = """
                select FLOOR(created / 1000 / 60)  minute,
                       backend_url                 ,
                       count(1)                    query_count
                from query_history
                where created > %s
                group by FLOOR(created / 1000 / 60), backend_url
                """.formatted(ts);
            List<Map> results = Base.findAll(sql);
            for (Map model : results) {
                // 处理查询结果
                System.out.println(model);
                DistributionResponse.LineChart lineChart = new DistributionResponse.LineChart();
                int minute = Integer.parseInt(model.get("minute").toString());
                Instant instant = Instant.ofEpochSecond(minute * 60L);
                LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                lineChart.setMinute(dateTime.format(formatter));
                lineChart.setQueryCount(Long.parseLong(model.get("query_count").toString()));
                lineChart.setBackendUrl(model.get("backend_url").toString());
                resList.add(lineChart);
            }
            return resList;
        } finally {
            connectionManager.close();
        }
    }
}

