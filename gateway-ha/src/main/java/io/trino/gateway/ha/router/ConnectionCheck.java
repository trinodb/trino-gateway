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
package io.trino.gateway.ha.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

class ConnectionCheck
{
    static final int NO_CHECK_YET = -100;
    static final int TCP_CHECK_FAILURE = -1;
    static final int TCP_CHECK_SUCCESS = 0;
    private static final Logger log = LoggerFactory.getLogger(ConnectionCheck.class);

    private Instant lastCheckTime;
    private int checkCode;
    private final String server;
    private final int port;
    private final int checkInterval;
    private final int failCount;
    private int failCounter;
    private final int disableDuration;

    ConnectionCheck(String host, int serverPort, int checkInterval,
            int failCount, int disableDuration)
    {
        this.lastCheckTime = Instant.EPOCH;
        this.checkCode = NO_CHECK_YET;
        this.server = host;
        this.port = serverPort;
        this.checkInterval = checkInterval;
        this.failCount = failCount;
        this.failCounter = 0;
        this.disableDuration = disableDuration;
    }

    boolean isCheckNeeded()
    {
        try {
            Instant now = Instant.now();
            log.info("time now is {}", now);
            log.info("last check was at {}", lastCheckTime);
            log.info("check interval is {}", checkInterval);
            long diff = Duration.between(lastCheckTime, now).toMillis();
            lastCheckTime = now;
            return diff > checkInterval;
        }
        catch (ArithmeticException ex) {
            log.error("Error", ex);
        }
        return true;
    }

    boolean isCheckDisabled()
    {
        if (failCounter < failCount) {
            log.info("fail count is NOT reached");
            return false;
        }

        log.info("fail count is reached");
        try {
            Instant now = Instant.now();
            log.info("time now is {}", now);
            log.info("last check was at {}", lastCheckTime);
            log.info("disabled duration is {}", disableDuration);
            long diff = Duration.between(lastCheckTime, now).toSeconds();
            return diff < disableDuration;
        }
        catch (ArithmeticException ex) {
            log.error("Error", ex);
        }
        return true;
    }

    Socket makeSocket(String server, int port)
            throws IOException
    {
        return new Socket(server, port);
    }

    int tcpCheck()
    {
        log.debug("trying the tcp check for {}:{}", server, port);

        if (isCheckDisabled()) {
            return checkCode;
        }

        if (!isCheckNeeded()) {
            return checkCode;
        }

        try (Socket socket = makeSocket(server, port)) {
            checkCode = TCP_CHECK_SUCCESS;
            log.info("able to connect to {}:{}", server, port);
            failCounter = 0;
        }
        catch (Exception ex) {
            log.error("Error while connecting to {}:{}", server, port, ex);
            log.error("FailCounter {}", failCounter);
            checkCode = TCP_CHECK_FAILURE;
            ++failCounter;
        }

        return checkCode;
    }
}
