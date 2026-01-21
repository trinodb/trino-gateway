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
    @SqlUpdate("""
            INSERT INTO gateway_audit_logs (user_name, ip_address, backend_name, operation, context, success, user_comment, change_time)
            VALUES (:user_name, :ip_address, :backend_name, :operation, :context, :success, LEFT(:user_comment, 1024), :change_time)
            """)
    void log(@Bind("user_name") String user_name,
            @Bind("ip_address") String ip_address,
            @Bind("backend_name") String backend_name,
            @Bind("operation") String operation,
            @Bind("context") String context,
            @Bind("success") Boolean success,
            @Bind("user_comment") String user_comment,
            @Bind("change_time") Timestamp change_time);
}
