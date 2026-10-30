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

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.sql.Timestamp;

public interface AuditLogDao
{
    @SqlUpdate(
            """
            INSERT INTO gateway_audit_logs (user_name, ip_address, backend_name, operation, context, success, user_comment, change_time)
            VALUES (:user_name, :ip_address, :backend_name, :operation, :context, :success, :user_comment, :change_time)
            """)
    void log(
            @Bind("user_name") String userName,
            @Bind("ip_address") String ipAddress,
            @Bind("backend_name") String backendName,
            @Bind("operation") String operation,
            @Bind("context") String context,
            @Bind("success") Boolean success,
            @Bind("user_comment") String userComment,
            @Bind("change_time") Timestamp changeTime);
}
