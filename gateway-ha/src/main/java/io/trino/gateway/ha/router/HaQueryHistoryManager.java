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
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.config.WriteBufferConfiguration;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import io.trino.gateway.ha.persistence.dao.QueryHistoryDao;
import jakarta.annotation.PreDestroy;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class HaQueryHistoryManager
        implements QueryHistoryManager
{
    private static final int FIRST_PAGE_NO = 1;
    private static final String TRUNCATION_MARKER = "\n-- [truncated by trino-gateway: %d of %d chars]";
    private static final Logger log = Logger.get(HaQueryHistoryManager.class);

    private final QueryHistoryDao dao;
    private final boolean isOracleBackend;
    private final boolean queryHistoryEnabled;
    private final Integer maxQueryTextLength;
    private final WriteBuffer<QueryDetail> writeBuffer;
    private final ScheduledExecutorService scheduledExecutor;

    @Inject
    public HaQueryHistoryManager(Jdbi jdbi, DataStoreConfiguration configuration, WriteBufferConfiguration writeBufferConfig)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(QueryHistoryDao.class);
        requireNonNull(configuration, "configuration is null");
        requireNonNull(writeBufferConfig, "writeBufferConfig is null");
        this.isOracleBackend = configuration.getJdbcUrl().startsWith("jdbc:oracle");
        queryHistoryEnabled = configuration.isQueryHistoryEnabled();
        maxQueryTextLength = configuration.getQueryHistoryMaxQueryTextLength();
        checkArgument(maxQueryTextLength == null || maxQueryTextLength > 0, "queryHistoryMaxQueryTextLength must be greater than 0");
        if (writeBufferConfig.isEnabled()) {
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
        if (!queryHistoryEnabled) {
            return;
        }

        String id = queryDetail.getQueryId();
        if (id == null || id.isEmpty()) {
            return;
        }

        try {
            dao.insertHistory(
                    queryDetail.getQueryId(),
                    truncateQueryText(queryDetail.getQueryText()),
                    queryDetail.getBackendUrl(),
                    queryDetail.getUser(),
                    queryDetail.getSource(),
                    queryDetail.getCaptureTime(),
                    queryDetail.getRoutingGroup(),
                    queryDetail.getExternalUrl());
        }
        catch (RuntimeException e) {
            if (isConnectionIssue(e) && writeBuffer != null) {
                boolean dropped = writeBuffer.buffer(queryDetail);
                if (dropped) {
                    log.error(
                            "Write buffer full; dropped oldest query_history entry to make room. Increase writeBuffer.maxCapacity or decrease writeBuffer.flushInterval. queryId=%s, bufferSize=%s",
                            queryDetail.getQueryId(),
                            writeBuffer.size());
                }
                else {
                    log.warn(e,
                            "DB unavailable; buffered query_history entry. queryId=%s, bufferSize=%s",
                            queryDetail.getQueryId(),
                            writeBuffer.size());
                }
            }
            else {
                throw e;
            }
        }
    }

    private String truncateQueryText(String queryText)
    {
        if (queryText == null || maxQueryTextLength == null || queryText.length() <= maxQueryTextLength) {
            return queryText;
        }
        int end = maxQueryTextLength;
        // Do not split a surrogate pair
        if (Character.isHighSurrogate(queryText.charAt(end - 1))) {
            end--;
        }
        return queryText.substring(0, end) + TRUNCATION_MARKER.formatted(end, queryText.length());
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
        String userName = Strings.emptyToNull(query.user());
        String externalUrl = Strings.emptyToNull(query.externalUrl());
        String queryId = Strings.emptyToNull(query.queryId());
        String source = Strings.emptyToNull(query.source());
        List<QueryHistory> histories = dao.pageQueryHistory(userName, externalUrl, queryId, source, query.size(), start, isOracleBackend);
        List<QueryDetail> rows = upcast(histories);
        Long total = dao.count(userName, externalUrl, queryId, source);
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
                    truncateQueryText(r.getQueryText()),
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
