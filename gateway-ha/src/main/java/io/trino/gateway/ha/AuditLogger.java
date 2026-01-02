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
package io.trino.gateway.ha;

import io.airlift.log.Logger;
import io.trino.gateway.ha.persistence.dao.AuditLogDao;
import org.jdbi.v3.core.Jdbi;

import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public class AuditLogger
{
    private static final Logger log = Logger.get(AuditLogger.class);

    private final AuditLogDao dao;

    public AuditLogger(Jdbi jdbi)
    {
        dao = requireNonNull(jdbi, "jdbi is null").onDemand(AuditLogDao.class);
    }

    public void logAudit(String user, String ip, String backend, AuditAction action, AuditContext context, boolean success, String userComment)
    {
        String comment = sanitizeComment(userComment);
        log.info("GW_AUDIT_LOG: user=%s, ipAddress=%s, backend=%s, action=%s, context=%s, success=%s, userComment=%s",
                user, ip, backend, action, context, success, comment);
        try {
            dao.log(user, ip, backend, action.toString(), context.toString(), success ? 1 : 0, comment, Timestamp.from(Instant.now()));
        }
        catch (Exception e) {
            log.error("Failed to write audit log to database: %s", e.getMessage());
        }
    }

    private String sanitizeComment(String comment)
    {
        return comment.replaceAll("\\s+", " ").trim();
    }
}
