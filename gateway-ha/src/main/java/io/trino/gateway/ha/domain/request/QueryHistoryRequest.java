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
package io.trino.gateway.ha.domain.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Query parameters for History
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryHistoryRequest
{
    /**
     * page index
     */
    @JsonProperty
    private Integer page = 1;

    /**
     * page size
     */
    @JsonProperty
    private Integer size = 10;

    /**
     * Query histories of specified user.
     * ADMIN role is optional, other roles are mandatory.
     */
    @JsonProperty
    private String user;

    /**
     * Optional, you can query the history based on the backendUrl.
     */
    @JsonProperty
    private String backendUrl;

    /**
     * Optional, you can query the query history based on the queryId of Trino.
     */
    @JsonProperty
    private String queryId;

    public Integer getPage()
    {
        return page;
    }

    public void setPage(Integer page)
    {
        this.page = page;
    }

    public Integer getSize()
    {
        return size;
    }

    public void setSize(Integer size)
    {
        this.size = size;
    }

    public String getUser()
    {
        return user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public String getBackendUrl()
    {
        return backendUrl;
    }

    public void setBackendUrl(String backendUrl)
    {
        this.backendUrl = backendUrl;
    }

    public String getQueryId()
    {
        return queryId;
    }

    public void setQueryId(String queryId)
    {
        this.queryId = queryId;
    }
}
