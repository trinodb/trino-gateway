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

import com.google.inject.Inject;
import io.airlift.log.Logger;

import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

public class CompositeAuditLogger
        implements AuditLogger
{
    private static final Logger log = Logger.get(CompositeAuditLogger.class);
    private final Set<AuditLogger> loggers;

    @Inject
    public CompositeAuditLogger(Set<AuditLogger> loggers)
    {
        this.loggers = requireNonNullElse(loggers, Collections.emptySet());
    }

    @Override
    public void logAudit(String user, String ip, String backend, AuditAction action, AuditContext context, boolean success, String userComment)
    {
        String sanitizedComment = AuditLogger.sanitizeComment(userComment);
        for (AuditLogger logger : loggers) {
            try {
                logger.logAudit(user, ip, backend, action, context, success, sanitizedComment);
            }
            catch (Exception e) {
                log.error(e, "Audit sink %s failed", logger.getClass().getSimpleName());
            }
        }
    }
}
