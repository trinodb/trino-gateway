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
import java.util.Optional;

public interface QueryHistoryManager {
  void submitQueryDetail(QueryDetail queryDetail);

  List<QueryDetail> fetchQueryHistory(Optional<String> user);

  String getBackendForQueryId(String queryId);

  class QueryDetail implements Comparable<QueryDetail> {
    private String queryId;
    private String queryText;
    private String user;
    private String source;
    private String backendUrl;
    private long captureTime;

    public QueryDetail() {}

    @Override
    public int compareTo(QueryDetail o) {
      if (this.captureTime < o.captureTime) {
        return 1;
      } else {
        return this.captureTime == o.captureTime ? 0 : -1;
      }
    }

    public String getQueryId()
    {return this.queryId;}

    public String getQueryText()
    {return this.queryText;}

    public String getUser()
    {return this.user;}

    public String getSource()
    {return this.source;}

    public String getBackendUrl()
    {return this.backendUrl;}

    public long getCaptureTime()
    {return this.captureTime;}

    public void setQueryId(String queryId)
    {this.queryId = queryId;}

    public void setQueryText(String queryText)
    {this.queryText = queryText;}

    public void setUser(String user)
    {this.user = user;}

    public void setSource(String source)
    {this.source = source;}

    public void setBackendUrl(String backendUrl)
    {this.backendUrl = backendUrl;}

    public void setCaptureTime(long captureTime)
    {this.captureTime = captureTime;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof QueryDetail)) {
            return false;
        }
        final QueryDetail other = (QueryDetail) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$queryId = this.getQueryId();
        final Object other$queryId = other.getQueryId();
        if (this$queryId == null ? other$queryId != null : !this$queryId.equals(other$queryId)) {
            return false;
        }
        final Object this$queryText = this.getQueryText();
        final Object other$queryText = other.getQueryText();
        if (this$queryText == null ? other$queryText != null : !this$queryText.equals(other$queryText)) {
            return false;
        }
        final Object this$user = this.getUser();
        final Object other$user = other.getUser();
        if (this$user == null ? other$user != null : !this$user.equals(other$user)) {
            return false;
        }
        final Object this$source = this.getSource();
        final Object other$source = other.getSource();
        if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
            return false;
        }
        final Object this$backendUrl = this.getBackendUrl();
        final Object other$backendUrl = other.getBackendUrl();
        if (this$backendUrl == null ? other$backendUrl != null : !this$backendUrl.equals(other$backendUrl)) {
            return false;
        }
        if (this.getCaptureTime() != other.getCaptureTime()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof QueryDetail;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $queryId = this.getQueryId();
        result = result * PRIME + ($queryId == null ? 43 : $queryId.hashCode());
        final Object $queryText = this.getQueryText();
        result = result * PRIME + ($queryText == null ? 43 : $queryText.hashCode());
        final Object $user = this.getUser();
        result = result * PRIME + ($user == null ? 43 : $user.hashCode());
        final Object $source = this.getSource();
        result = result * PRIME + ($source == null ? 43 : $source.hashCode());
        final Object $backendUrl = this.getBackendUrl();
        result = result * PRIME + ($backendUrl == null ? 43 : $backendUrl.hashCode());
        final long $captureTime = this.getCaptureTime();
        result = result * PRIME + (int) ($captureTime >>> 32 ^ $captureTime);
        return result;
    }

    public String toString() {
        return "QueryHistoryManager.QueryDetail(queryId=" + this.getQueryId() + ", " +
                "queryText=" + this.getQueryText() + ", user=" + this.getUser() +
                ", source=" + this.getSource() + ", backendUrl=" + this.getBackendUrl() +
                ", captureTime=" + this.getCaptureTime() + ")";}
  }
}
