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
package io.trino.gateway.ha.audit;

import io.airlift.log.Logger;
import io.trino.gateway.ha.persistence.dao.AuditLogDao;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public class DatabaseAuditLogger
        implements AuditLogger
{
    private static final Logger log = Logger.get(DatabaseAuditLogger.class);
    private final AuditLogDao dao;

    public DatabaseAuditLogger(Jdbi jdbi)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(AuditLogDao.class);
    }

    @Override
    public void logAudit(String user, String ip, String backendName, AuditAction action, AuditContext context, boolean success, String userComment)
    {
        try {
            dao.log(user, ip, backendName, action.toString(), context.toString(), success,
                    AuditLogger.sanitizeComment(userComment), Timestamp.from(Instant.now()));
        }
        catch (Exception e) {
            log.error("Failed to write audit log to database: %s", e.getMessage());
        }
    }
}
