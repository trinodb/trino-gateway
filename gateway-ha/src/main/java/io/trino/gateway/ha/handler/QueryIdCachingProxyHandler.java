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
package io.trino.gateway.ha.handler;

public class QueryIdCachingProxyHandler
{
    public static final String V1_STATEMENT_PATH = "/v1/statement";
    public static final String V1_QUERY_PATH = "/v1/query";
    public static final String V1_INFO_PATH = "/v1/info";
    public static final String V1_NODE_PATH = "/v1/node";
    public static final String UI_API_STATS_PATH = "/ui/api/stats";
    public static final String UI_LOGIN_PATH = "/ui/login";
    public static final String UI_API_QUEUED_LIST_PATH = "/ui/api/query?state=QUEUED";
    public static final String TRINO_UI_PATH = "/ui";
    public static final String OAUTH_PATH = "/oauth2";
    public static final String AUTHORIZATION = "Authorization";
    public static final String USER_HEADER = "X-Trino-User";

    private QueryIdCachingProxyHandler() {}
}
