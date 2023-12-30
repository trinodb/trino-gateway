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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface QueryHistoryManager
{
    void submitQueryDetail(QueryDetail queryDetail);

    List<QueryDetail> fetchQueryHistory(Optional<String> user);

    String getBackendForQueryId(String queryId);

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

        public boolean equals(final Object o)
        {
            if (o == this) {
                return true;
            }
            if (!(o instanceof QueryDetail other)) {
                return false;
            }
            if (!other.canEqual(this)) {
                return false;
            }
            final Object queryId = this.getQueryId();
            final Object otherQueryId = other.getQueryId();
            if (!Objects.equals(queryId, otherQueryId)) {
                return false;
            }
            final Object queryText = this.getQueryText();
            final Object otherQueryText = other.getQueryText();
            if (!Objects.equals(queryText, otherQueryText)) {
                return false;
            }
            final Object user = this.getUser();
            final Object otherUser = other.getUser();
            if (!Objects.equals(user, otherUser)) {
                return false;
            }
            final Object source = this.getSource();
            final Object otherSource = other.getSource();
            if (!Objects.equals(source, otherSource)) {
                return false;
            }
            final Object backendUrl = this.getBackendUrl();
            final Object otherBackendUrl = other.getBackendUrl();
            if (!Objects.equals(backendUrl, otherBackendUrl)) {
                return false;
            }
            return this.getCaptureTime() == other.getCaptureTime();
        }

        protected boolean canEqual(final Object other)
        {
            return other instanceof QueryDetail;
        }

        public int hashCode()
        {
            final int prime = 59;
            int result = 1;
            final Object queryId = this.getQueryId();
            result = result * prime + (queryId == null ? 43 : queryId.hashCode());
            final Object queryText = this.getQueryText();
            result = result * prime + (queryText == null ? 43 : queryText.hashCode());
            final Object user = this.getUser();
            result = result * prime + (user == null ? 43 : user.hashCode());
            final Object source = this.getSource();
            result = result * prime + (source == null ? 43 : source.hashCode());
            final Object backendUrl = this.getBackendUrl();
            result = result * prime + (backendUrl == null ? 43 : backendUrl.hashCode());
            final long captureTime = this.getCaptureTime();
            result = result * prime + (int) (captureTime >>> 32 ^ captureTime);
            return result;
        }

        public String toString()
        {
            return "QueryHistoryManager.QueryDetail(queryId=" + this.getQueryId() + ", " +
                    "queryText=" + this.getQueryText() + ", user=" + this.getUser() +
                    ", source=" + this.getSource() + ", backendUrl=" + this.getBackendUrl() +
                    ", captureTime=" + this.getCaptureTime() + ")";
        }
    }
}
