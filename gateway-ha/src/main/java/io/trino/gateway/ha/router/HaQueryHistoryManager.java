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
import com.google.inject.Inject;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class HaQueryHistoryManager
        implements QueryHistoryManager
{
    private static final int FIRST_PAGE_NO = 1;

    private final QueryHistoryDao dao;
    private final boolean isOracleBackend;
    private final boolean queryHistoryEnabled;

    @Inject
    public HaQueryHistoryManager(Jdbi jdbi, DataStoreConfiguration configuration)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(QueryHistoryDao.class);
        this.isOracleBackend = configuration.getJdbcUrl().startsWith("jdbc:oracle");
        queryHistoryEnabled = configuration.isQueryHistoryEnabled();
    }

    @Override
    public void submitQueryDetail(QueryDetail queryDetail)
    {
        if (!queryHistoryEnabled) {
            return;
        }

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
                queryDetail.getCaptureTime(),
                queryDetail.getRoutingDecision(),
                queryDetail.getExternalUrl());
    }

    @Override
    public List<QueryDetail> fetchQueryHistory(Optional<String> user)
    {
        List<QueryHistory> histories;
        if (user.isPresent()) {
            histories = dao.findRecentQueriesByUserName(user.orElseThrow(), isOracleBackend);
        }
        else {
            histories = dao.findRecentQueries(isOracleBackend);
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
            queryDetail.setRoutingDecision(dao.routingDecision());
            queryDetail.setExternalUrl(dao.externalUrl());
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
    public String getRoutingDecisionForQueryId(String queryId)
    {
        return dao.findRoutingDecisionByQueryId(queryId);
    }

    @Override
    public String getExternalUrlForQueryId(String queryId)
    {
        return dao.findExternalUrlByQueryId(queryId);
    }

    @Override
    public TableData<QueryDetail> findQueryHistory(QueryHistoryRequest query)
    {
        int start = getStart(query.page(), query.size());
        String condition = "";
        if (!Strings.isNullOrEmpty(query.user())) {
            condition += " and user_name = '" + query.user() + "'";
        }
        if (!Strings.isNullOrEmpty(query.externalUrl())) {
            condition += " and external_url = '" + query.externalUrl() + "'";
        }
        if (!Strings.isNullOrEmpty(query.queryId())) {
            condition += " and query_id = '" + query.queryId() + "'";
        }
        if (!Strings.isNullOrEmpty(query.source())) {
            condition += " and source = '" + query.source() + "'";
        }
        List<QueryHistory> histories = dao.pageQueryHistory(condition, query.size(), start);
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
            long minute = new BigDecimal(model.get("minute").toString()).longValue();
            Instant instant = Instant.ofEpochSecond(minute * 60L);
            long epochMillis = instant.toEpochMilli();
            lineChart.setEpochMillis(epochMillis);
            lineChart.setQueryCount(Long.parseLong(model.get("query_count").toString()));
            lineChart.setBackendUrl(model.get("backend_url").toString());
            resList.add(lineChart);
        }
        return resList;
    }

    private static int getStart(int pageNo, int pageSize)
    {
        if (pageNo < FIRST_PAGE_NO) {
            pageNo = FIRST_PAGE_NO;
        }
        if (pageSize < 1) {
            pageSize = 0;
        }
        return (pageNo - FIRST_PAGE_NO) * pageSize;
    }
}
