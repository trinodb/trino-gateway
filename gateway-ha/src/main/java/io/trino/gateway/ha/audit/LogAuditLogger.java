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

public class LogAuditLogger
        implements AuditLogger
{
    private static final Logger log = Logger.get(LogAuditLogger.class);

    @Override
    public void logAudit(String user, String ip, String backendName, AuditAction action, AuditContext context, boolean success, String userComment)
    {
        log.info("GW_AUDIT_LOG: user=%s, ipAddress=%s, backend=%s, action=%s, context=%s, success=%s, userComment=%s",
                user, ip, backendName, action, context, success, userComment);
    }
}
