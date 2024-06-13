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

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.domain.response.DistributionResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestingQueryManager
        implements QueryHistoryManager
{
    Map<String, QueryDetail> queryDetailMap = new HashMap<>();

    public TestingQueryManager()
    {
    }

    @Override
    public void submitQueryDetail(QueryDetail queryDetail)
    {
        queryDetailMap.put(queryDetail.getQueryId(), queryDetail);
    }

    @Override
    public List<QueryDetail> fetchQueryHistory(Optional<String> user)
    {
        return queryDetailMap.values().stream().toList();
    }

    @Override
    public String getBackendForQueryId(String queryId)
    {
        return queryDetailMap.get(queryId).getBackendUrl();
    }

    @Override
    public TableData<QueryDetail> findQueryHistory(QueryHistoryRequest query)
    {
        return TableData.build(ImmutableList.of(queryDetailMap.get(query.queryId())), 1);
    }

    @Override
    public List<DistributionResponse.LineChart> findDistribution(Long ts)
    {
        throw new UnsupportedOperationException("Not implemented");
    }
}
