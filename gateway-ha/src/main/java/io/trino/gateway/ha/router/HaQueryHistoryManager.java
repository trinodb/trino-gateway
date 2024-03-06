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

import com.google.common.base.Strings;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import io.trino.gateway.ha.util.PageUtil;
import org.jdbi.v3.core.Jdbi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class HaQueryHistoryManager
        implements QueryHistoryManager
{
    private final QueryHistoryDao dao;

    public HaQueryHistoryManager(Jdbi jdbi)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(QueryHistoryDao.class);
    }

    @Override
    public void submitQueryDetail(QueryDetail queryDetail)
    {
        String id = queryDetail.getQueryId();
        if (id == null || id.isEmpty()) {
            return;
        }

        dao.insertHistory(
                queryDetail.getQueryId(),
                queryDetail.getQueryText(),
                queryDetail.getBackendUrl(),
                queryDetail.getUser(),
                queryDetail.getSource(),
                queryDetail.getCaptureTime());
    }

    @Override
    public List<QueryDetail> fetchQueryHistory(Optional<String> user)
    {
        List<QueryHistory> histories;
        if (user.isPresent()) {
            histories = dao.findRecentQueriesByUserName(user.orElseThrow());
        }
        else {
            histories = dao.findRecentQueries();
        }
        return upcast(histories);
    }

    private static List<QueryHistoryManager.QueryDetail> upcast(List<QueryHistory> queryHistoryList)
    {
        List<QueryHistoryManager.QueryDetail> queryDetails = new ArrayList<>();
        for (QueryHistory dao : queryHistoryList) {
            QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
            queryDetail.setQueryId(dao.queryId());
            queryDetail.setQueryText(dao.queryText());
            queryDetail.setCaptureTime(dao.created());
            queryDetail.setBackendUrl(dao.backendUrl());
            queryDetail.setUser(dao.userName());
            queryDetail.setSource(dao.source());
            queryDetails.add(queryDetail);
        }
        return queryDetails;
    }

    @Override
    public String getBackendForQueryId(String queryId)
    {
        return dao.findBackendUrlByQueryId(queryId);
    }

    @Override
    public TableData<QueryDetail> findQueryHistory(QueryHistoryRequest query)
    {
        int start = PageUtil.getStart(query.getPage(), query.getSize());
        String condition = "";
        if (!Strings.isNullOrEmpty(query.getUser())) {
            condition += " and user_name = '" + query.getUser() + "'";
        }
        if (!Strings.isNullOrEmpty(query.getBackendUrl())) {
            condition += " and backend_url = '" + query.getBackendUrl() + "'";
        }
        if (!Strings.isNullOrEmpty(query.getQueryId())) {
            condition += " and query_id = '" + query.getQueryId() + "'";
        }
        List<QueryHistory> histories = dao.pageQueryHistory(condition, query.getSize(), start);
        List<QueryDetail> rows = upcast(histories);
        Long total = dao.count(condition);
        return TableData.build(rows, total);
    }

    @Override
    public List<DistributionResponse.LineChart> findDistribution(Long ts)
    {
        List<Map<String, Object>> results = dao.findDistribution(ts);
        List<DistributionResponse.LineChart> resList = new ArrayList<>();
        for (Map<String, Object> model : results) {
            DistributionResponse.LineChart lineChart = new DistributionResponse.LineChart();
            long minute = Long.parseLong(model.get("minute").toString());
            Instant instant = Instant.ofEpochSecond(minute * 60L);
            LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            lineChart.setMinute(dateTime.format(formatter));
            lineChart.setQueryCount(Long.parseLong(model.get("query_count").toString()));
            lineChart.setBackendUrl(model.get("backend_url").toString());
            resList.add(lineChart);
        }
        return resList;
    }
}
