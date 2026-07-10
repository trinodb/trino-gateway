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

import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface OAuth2RoutingDao
{
    @SqlQuery(
            """
            SELECT backend_url FROM oauth2_routing
            WHERE oauth_id = :oauthId
            """)
    String findBackendByOAuthId(String oauthId);

    @SqlUpdate(
            """
            INSERT INTO oauth2_routing (oauth_id, backend_url, created)
            VALUES (:oauthId, :backendUrl, :created)
            """)
    void insert(String oauthId, String backendUrl, long created);

    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE oauth_id = :oauthId
            """)
    void delete(String oauthId);

    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE created < :created
            """)
    void deleteOldOAuth2Pins(long created);
}
