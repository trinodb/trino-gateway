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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;

import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public final class AuditLogDispatcher
{
    private static final Logger log = Logger.get(AuditLogDispatcher.class);
    private final Set<AuditLogger> loggers;

    @Inject
    public AuditLogDispatcher(Set<AuditLogger> loggers)
    {
        this.loggers = ImmutableSet.copyOf(requireNonNull(loggers, "loggers is null"));
    }

    public void logAudit(String user, String ip, String backend, AuditAction action, AuditContext context, boolean success, String userComment)
    {
        String sanitizedComment = sanitizeComment(userComment);
        for (AuditLogger logger : loggers) {
            try {
                logger.logAudit(user, ip, backend, action, context, success, sanitizedComment);
            }
            catch (Exception e) {
                log.error(e, "Audit sink %s failed", logger.getClass().getSimpleName());
            }
        }
    }

    private static String sanitizeComment(String comment)
    {
        String c = requireNonNullElse(comment, "");
        return c.replaceAll("\\s+", " ").trim();
    }
}
