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
 *
 * @param page page index
 * @param size page size
 * @param user Query histories of specified user. ADMIN role is optional, other roles are mandatory.
 * @param backendUrl Optional, you can query the history based on the backendUrl.
 * @param queryId Optional, you can query the query history based on the queryId of Trino.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryHistoryRequest(
        @JsonProperty("page") Integer page,
        @JsonProperty("size") Integer size,
        @JsonProperty("user") String user,
        @JsonProperty("backendUrl") String backendUrl,
        @JsonProperty("queryId") String queryId)
{
    public QueryHistoryRequest
    {
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
    }
}
