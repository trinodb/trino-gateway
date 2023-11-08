package io.trino.gateway.ha.persistence.dao;

import io.trino.gateway.ha.router.QueryHistoryManager;
import java.util.ArrayList;
import java.util.List;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

@IdName("query_id")
@Table("query_history")
@Cached
public class QueryHistory extends Model {
  public static final String queryId = "query_id";
  public static final String queryText = "query_text";
  public static final String backendUrl = "backend_url";
  public static final String userName = "user_name";
  public static final String source = "source";
  public static final String created = "created";

  public static List<QueryHistoryManager.QueryDetail> upcast(List<QueryHistory> queryHistoryList) {
    List<QueryHistoryManager.QueryDetail> queryDetails = new ArrayList<>();
    for (QueryHistory dao : queryHistoryList) {
      QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
      queryDetail.setQueryId(dao.getString(queryId));
      queryDetail.setQueryText(dao.getString(queryText));
      queryDetail.setCaptureTime(dao.getLong(created));
      queryDetail.setBackendUrl(dao.getString(backendUrl));
      queryDetail.setUser(dao.getString(userName));
      queryDetail.setSource(dao.getString(source));
      queryDetails.add(queryDetail);
    }
    return queryDetails;
  }

  public static void create(QueryHistory model, QueryHistoryManager.QueryDetail queryDetail) {
    //Checks
    String id = queryDetail.getQueryId();
    if (id == null || id.isEmpty()) {
      return;
    }

    model.set(queryId, id);
    model.set(queryText, queryDetail.getQueryText());
    model.set(backendUrl, queryDetail.getBackendUrl());
    model.set(userName, queryDetail.getUser());
    model.set(source, queryDetail.getSource());
    model.set(created, queryDetail.getCaptureTime());
    if (!queryDetail.getQueryId().isEmpty()) {
      model.insert();
    }
  }
}
