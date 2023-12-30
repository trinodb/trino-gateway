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

import io.trino.gateway.ha.router.QueryHistoryManager;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Cached;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

import java.util.ArrayList;
import java.util.List;

@IdName("query_id")
@Table("query_history")
@Cached
public class QueryHistory
        extends Model
{
    private static final String queryId = "query_id";
    private static final String queryText = "query_text";
    private static final String backendUrl = "backend_url";
    private static final String userName = "user_name";
    private static final String source = "source";
    private static final String created = "created";

    public static List<QueryHistoryManager.QueryDetail> upcast(List<QueryHistory> queryHistoryList)
    {
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

    public static void create(QueryHistory model, QueryHistoryManager.QueryDetail queryDetail)
    {
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
