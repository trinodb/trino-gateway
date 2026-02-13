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

import io.trino.gateway.ha.persistence.dao.AuditLogDao;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestAuditLogDispatcher
{
    private static final String USER = "testUser";
    private static final String IP = "127.0.0.1";
    private static final String BACKEND = "trino-cluster";
    private static final AuditContext CONTEXT = AuditContext.TRINO_GW_API;

    private AuditLogger mockLogger1;
    private AuditLogger mockLogger2;
    private AuditLogDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        mockLogger1 = mock(AuditLogger.class);
        mockLogger2 = mock(AuditLogger.class);
        dispatcher = new AuditLogDispatcher(Set.of(mockLogger1, mockLogger2));
    }

    @Test
    void testDispatchesToAllLoggers()
    {
        AuditAction action = AuditAction.CREATE;
        String comment = "test comment";

        dispatcher.logAudit(USER, IP, BACKEND, action, CONTEXT, true, comment);

        verify(mockLogger1, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, comment);
        verify(mockLogger2, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, comment);
    }

    @Test
    void testOneLoggerFailureDoesNotAffectOthers()
    {
        AuditAction action = AuditAction.UPDATE;
        String comment = "test failure handling";

        doThrow(new RuntimeException("Simulated failure"))
                .when(mockLogger1)
                .logAudit(USER, IP, BACKEND, action, CONTEXT, false, comment);

        assertThatCode(() -> dispatcher.logAudit(USER, IP, BACKEND, action, CONTEXT, false, comment))
                .doesNotThrowAnyException();

        verify(mockLogger1, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, false, comment);
        verify(mockLogger2, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, false, comment);
    }

    @Test
    void testSanitizesWhitespaceInComments()
    {
        AuditAction action = AuditAction.DELETE;
        String rawComment = "  Multiple   spaces\n\tand\nnewlines  ";
        String sanitizedComment = "Multiple spaces and newlines";

        dispatcher.logAudit(USER, IP, BACKEND, action, CONTEXT, true, rawComment);

        verify(mockLogger1, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, sanitizedComment);
        verify(mockLogger2, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, sanitizedComment);
    }

    @Test
    void testNullCommentBecomesEmptyString()
    {
        AuditAction action = AuditAction.ACTIVATE;

        dispatcher.logAudit(USER, IP, BACKEND, action, CONTEXT, true, null);

        verify(mockLogger1, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, "");
        verify(mockLogger2, times(1)).logAudit(USER, IP, BACKEND, action, CONTEXT, true, "");
    }

    @Test
    void testEmptyLoggerSet()
    {
        AuditLogDispatcher emptyDispatcher = new AuditLogDispatcher(Set.of());

        assertThatCode(() -> emptyDispatcher.logAudit(USER, IP, BACKEND, AuditAction.UNKNOWN, CONTEXT, true, "comment"))
                .doesNotThrowAnyException();
    }

    @Test
    void testNullLoggerSetThrowsException()
    {
        assertThatThrownBy(() -> new AuditLogDispatcher(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("loggers is null");
    }

    @Test
    void testAllAuditActions()
    {
        for (AuditAction action : AuditAction.values()) {
            dispatcher.logAudit(USER, IP, BACKEND, action, CONTEXT, true, "action test");
        }

        int actionCount = AuditAction.values().length;
        verify(mockLogger1, times(actionCount)).logAudit(eq(USER), eq(IP), eq(BACKEND), any(AuditAction.class), eq(CONTEXT), eq(true), eq("action test"));
        verify(mockLogger2, times(actionCount)).logAudit(eq(USER), eq(IP), eq(BACKEND), any(AuditAction.class), eq(CONTEXT), eq(true), eq("action test"));
    }

    @Test
    void testAllAuditContexts()
    {
        for (AuditContext context : AuditContext.values()) {
            dispatcher.logAudit(USER, IP, BACKEND, AuditAction.CREATE, context, true, "context test");
        }

        int contextCount = AuditContext.values().length;
        verify(mockLogger1, times(contextCount)).logAudit(eq(USER), eq(IP), eq(BACKEND), eq(AuditAction.CREATE), any(AuditContext.class), eq(true), eq("context test"));
        verify(mockLogger2, times(contextCount)).logAudit(eq(USER), eq(IP), eq(BACKEND), eq(AuditAction.CREATE), any(AuditContext.class), eq(true), eq("context test"));
    }

    @Test
    void testWithMockedDatabaseAuditLogger()
    {
        Jdbi jdbi = mock(Jdbi.class);
        AuditLogDao dao = mock(AuditLogDao.class);
        when(jdbi.onDemand(AuditLogDao.class)).thenReturn(dao);

        DatabaseAuditLogger dbLogger = new DatabaseAuditLogger(jdbi);
        AuditLogDispatcher dispatcherWithDb = new AuditLogDispatcher(Set.of(dbLogger));

        dispatcherWithDb.logAudit(USER, IP, BACKEND, AuditAction.CREATE, CONTEXT, true, "db test");

        verify(dao, times(1)).log(
                eq(USER),
                eq(IP),
                eq(BACKEND),
                eq("CREATE"),
                eq("TRINO_GW_API"),
                eq(true),
                eq("db test"),
                any(Timestamp.class));
    }

    @Test
    void testBothLoggersFailGracefully()
    {
        doThrow(new RuntimeException("Logger 1 failed"))
                .when(mockLogger1)
                .logAudit(any(), any(), any(), any(), any(), any(Boolean.class), any());

        doThrow(new RuntimeException("Logger 2 failed"))
                .when(mockLogger2)
                .logAudit(any(), any(), any(), any(), any(), any(Boolean.class), any());

        assertThatCode(() -> dispatcher.logAudit(USER, IP, BACKEND, AuditAction.DELETE, CONTEXT, false, "both fail"))
                .doesNotThrowAnyException();
    }
}
