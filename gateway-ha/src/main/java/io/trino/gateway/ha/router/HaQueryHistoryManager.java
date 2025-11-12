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
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.WriteBufferConfiguration;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.PreDestroy;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Jdbi;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class HaQueryHistoryManager
        implements QueryHistoryManager
{
    private static final int FIRST_PAGE_NO = 1;
    private static final Logger log = Logger.get(HaQueryHistoryManager.class);

    private final QueryHistoryDao dao;
    private final boolean isOracleBackend;
    private final WriteBuffer<QueryDetail> writeBuffer;
    private final ScheduledExecutorService scheduledExecutor;

    @Inject
    public HaQueryHistoryManager(Jdbi jdbi, boolean isOracleBackend, WriteBufferConfiguration writeBufferConfig)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(QueryHistoryDao.class);
        this.isOracleBackend = isOracleBackend;
        if (writeBufferConfig != null && writeBufferConfig.isEnabled()) {
            this.writeBuffer = new WriteBuffer<>(writeBufferConfig.getMaxCapacity());
            this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "query-history-write-buffer");
                t.setDaemon(true);
                return t;
            });
            scheduledExecutor.scheduleWithFixedDelay(
                    this::flushBufferedWrites,
                    writeBufferConfig.getFlushInterval().toMillis(),
                    writeBufferConfig.getFlushInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
        }
        else {
            this.writeBuffer = null;
            this.scheduledExecutor = null;
        }
    }

    @Override
    public void submitQueryDetail(QueryDetail queryDetail)
    {
        String id = queryDetail.getQueryId();
        if (id == null || id.isEmpty()) {
            return;
        }

        try {
            dao.insertHistory(
                    queryDetail.getQueryId(),
                    queryDetail.getQueryText(),
                    queryDetail.getBackendUrl(),
                    queryDetail.getUser(),
                    queryDetail.getSource(),
                    queryDetail.getCaptureTime(),
                    queryDetail.getRoutingGroup(),
                    queryDetail.getExternalUrl());
        }
        catch (RuntimeException e) {
            if (isConnectionIssue(e) && writeBuffer != null) {
                writeBuffer.buffer(queryDetail);
                log.warn(e, "DB unavailable; buffered query_history entry. queryId=%s, bufferSize=%s",
                        queryDetail.getQueryId(), writeBuffer.size());
            }
            else {
                throw e;
            }
        }
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
            queryDetail.setRoutingGroup(dao.routingGroup());
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
    public String getRoutingGroupForQueryId(String queryId)
    {
        return dao.findRoutingGroupByQueryId(queryId);
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
            long minute = (long) Float.parseFloat(model.get("minute").toString());
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

    private static boolean isConnectionIssue(Throwable t)
    {
        // SQL State codes starting with "08" indicate connection exceptions per ANSI/ISO SQL standard.
        // See: https://en.wikipedia.org/wiki/SQLSTATE
        // Examples: 08000 (connection exception), 08001 (cannot establish connection),
        //           08003 (connection does not exist), 08006 (connection failure), etc.
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof ConnectionException) {
                return true;
            }
            if (cur instanceof SQLException sql) {
                String sqlState = sql.getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void flushBufferedWrites()
    {
        if (writeBuffer == null) {
            return;
        }
        int before = writeBuffer.size();
        int flushed = writeBuffer.flushAll(r -> {
            dao.insertHistory(
                    r.getQueryId(),
                    r.getQueryText(),
                    r.getBackendUrl(),
                    r.getUser(),
                    r.getSource(),
                    r.getCaptureTime(),
                    r.getRoutingGroup(),
                    r.getExternalUrl());
        });
        if (flushed > 0) {
            log.info("Flushed %s buffered query_history entries", flushed);
        }
        else if (before > 0 && writeBuffer.size() == before) {
            log.warn("Failed to flush buffered query_history entries; will retry. bufferSize=%s", before);
        }
    }

    @PreDestroy
    public void stop()
    {
        if (scheduledExecutor == null) {
            return;
        }
        try {
            flushBufferedWrites();
        }
        catch (RuntimeException t) {
            log.warn(t, "Error while flushing buffered query_history entries during shutdown");
        }
        finally {
            scheduledExecutor.shutdownNow();
        }
    }
}
