package io.trino.gateway.ha.router;

import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.QueryHistory;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HaQueryHistoryManager implements QueryHistoryManager {
  private JdbcConnectionManager connectionManager;

  public HaQueryHistoryManager(JdbcConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @Override
  public void submitQueryDetail(QueryDetail queryDetail) {
    try {
      connectionManager.open();
      QueryHistory dao = new QueryHistory();
      QueryHistory.create(dao, queryDetail);
    } finally {
      connectionManager.close();
    }
  }

  @Override
  public List<QueryDetail> fetchQueryHistory(Optional<String> user) {
    try {
      connectionManager.open();
      String sql = "select * from query_history";
      if (user.isPresent()) {
        sql += " where user_name = '" + user.get() + "'";
      }
      return QueryHistory.upcast(QueryHistory.findBySQL(String.join(" ",
          sql,
          "order by created desc",
          "limit 2000")));
    } finally {
      connectionManager.close();
    }
  }

  @Override
  public String getBackendForQueryId(String queryId) {
    String backend = null;
    try {
      connectionManager.open();
      QueryHistory queryHistory = QueryHistory.findById(queryId);
      if (queryHistory != null) {
        backend = queryHistory.get("backend_url").toString();
      }
    } finally {
      connectionManager.close();
    }
    return backend;
  }
}
