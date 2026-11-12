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

import io.trino.gateway.ha.model.BlockedUser;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface BlockedUserDao
{
    @SqlQuery(
            """
            SELECT * FROM blocked_users
            ORDER BY username, scope_type, scope_value
            """)
    List<BlockedUser> findAll();

    @SqlQuery(
            """
            SELECT * FROM blocked_users
            WHERE username = :username
            AND scope_type = :scopeType
            AND scope_value = :scopeValue
            """)
    BlockedUser findByUsernameAndScope(String username, String scopeType, String scopeValue);

    @SqlUpdate(
            """
            INSERT INTO blocked_users (username, scope_type, scope_value, reason, blocked_by)
            VALUES (:username, :scopeType, :scopeValue, :reason, :blockedBy)
            """)
    void insert(String username, String scopeType, String scopeValue, String reason, String blockedBy);

    @SqlUpdate(
            """
            DELETE FROM blocked_users
            WHERE username = :username
            AND scope_type = :scopeType
            AND scope_value = :scopeValue
            """)
    int delete(String username, String scopeType, String scopeValue);
}
