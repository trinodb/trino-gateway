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
package io.trino.gateway.ha.clustermonitor;

import io.airlift.log.Logger;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.trino.gateway.ha.clustermonitor.MonitorUtils.logHealthCheckError;
import static io.trino.gateway.ha.clustermonitor.MonitorUtils.logHealthCheckWarning;
import static org.assertj.core.api.Assertions.assertThat;

final class TestMonitorUtils
{
    @Test
    void testHealthCheckFailureLogLevel()
    {
        String loggerName = TestMonitorUtils.class.getName();
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(loggerName);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler()
        {
            @Override
            public void publish(LogRecord logRecord)
            {
                records.add(logRecord);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        Level previousLevel = julLogger.getLevel();
        julLogger.setLevel(Level.ALL);
        julLogger.addHandler(handler);
        try {
            Logger log = Logger.get(loggerName);
            Exception exception = new Exception("connection refused");
            ProxyBackendConfiguration active = createBackend(true);
            ProxyBackendConfiguration inactive = createBackend(false);

            logHealthCheckError(log, active, "error active");
            logHealthCheckError(log, active, exception, "error active");
            logHealthCheckWarning(log, active, exception, "warning active");
            logHealthCheckError(log, inactive, "error inactive");
            logHealthCheckError(log, inactive, exception, "error inactive");
            logHealthCheckWarning(log, inactive, exception, "warning inactive");

            assertThat(records)
                    .extracting(LogRecord::getLevel)
                    .containsExactly(Level.SEVERE, Level.SEVERE, Level.WARNING, Level.FINE, Level.FINE, Level.FINE);
            assertThat(records)
                    .extracting(LogRecord::getMessage)
                    .containsExactly("error active", "error active", "warning active", "error inactive", "error inactive", "warning inactive");
        }
        finally {
            julLogger.removeHandler(handler);
            julLogger.setLevel(previousLevel);
        }
    }

    private static ProxyBackendConfiguration createBackend(boolean active)
    {
        ProxyBackendConfiguration backend = new ProxyBackendConfiguration();
        backend.setName("backend");
        backend.setProxyTo("http://backend.example.com:8080");
        backend.setActive(active);
        return backend;
    }
}
