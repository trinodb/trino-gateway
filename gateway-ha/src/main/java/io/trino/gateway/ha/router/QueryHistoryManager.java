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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;

public interface QueryHistoryManager
{
    void submitQueryDetail(QueryDetail queryDetail);

    List<QueryDetail> fetchQueryHistory(Optional<String> user);

    String getBackendForQueryId(String queryId);

    TableData<QueryDetail> findQueryHistory(QueryHistoryRequest query);

    List<DistributionResponse.LineChart> findDistribution(Long ts);

    class QueryDetail
            implements Comparable<QueryDetail>
    {
        private String queryId;
        private String queryText;
        private String user;
        private String source;
        private String backendUrl;
        private long captureTime;

        public QueryDetail() {}

        @Override
        public int compareTo(QueryDetail o)
        {
            if (this.captureTime < o.captureTime) {
                return 1;
            }
            else {
                return this.captureTime == o.captureTime ? 0 : -1;
            }
        }

        public String getQueryId()
        {
            return this.queryId;
        }

        public void setQueryId(String queryId)
        {
            this.queryId = queryId;
        }

        public String getQueryText()
        {
            return this.queryText;
        }

        public void setQueryText(String queryText)
        {
            this.queryText = queryText;
        }

        public String getUser()
        {
            return this.user;
        }

        public void setUser(String user)
        {
            this.user = user;
        }

        public String getSource()
        {
            return this.source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getBackendUrl()
        {
            return this.backendUrl;
        }

        public void setBackendUrl(String backendUrl)
        {
            this.backendUrl = backendUrl;
        }

        public long getCaptureTime()
        {
            return this.captureTime;
        }

        public void setCaptureTime(long captureTime)
        {
            this.captureTime = captureTime;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QueryDetail that = (QueryDetail) o;
            return captureTime == that.captureTime &&
                    Objects.equals(queryId, that.queryId) &&
                    Objects.equals(queryText, that.queryText) &&
                    Objects.equals(user, that.user) &&
                    Objects.equals(source, that.source) &&
                    Objects.equals(backendUrl, that.backendUrl);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(queryId, queryText, user, source, backendUrl, captureTime);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("queryId", queryId)
                    .add("queryText", queryText)
                    .add("user", user)
                    .add("source", source)
                    .add("backendUrl", backendUrl)
                    .add("captureTime", captureTime)
                    .toString();
        }
    }
}
