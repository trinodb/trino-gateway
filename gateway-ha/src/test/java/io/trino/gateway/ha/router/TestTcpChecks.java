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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
public class TestTcpChecks
{
    @Test
    public void testTcpRuleCheck()
    {
        String host = "localhost";
        int port = 8888;
        int checkInterval = 1000; //in ms
        ConnectionChecker checker = mock(ConnectionChecker.class);
        when(checker.tcpCheck(host, port, checkInterval, 1, 0)).thenReturn(true);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getAttribute("connectionChecker")).thenReturn(checker);

        String rulesFile = "src/test/resources/rules/routing_rules_tcp_connection.yml";
        RoutingGroupSelector routingGroupSelector = RoutingGroupSelector.byRoutingRulesEngine(rulesFile);

        assertThat(routingGroupSelector.findRoutingGroup(mockRequest)).isEqualTo("cli");
    }

    @Test
    public void testConnectionCheckerSuccess()
            throws Exception
    {
        // Test successful connection check
        int checkInterval = 1000; //in ms
        ConnectionChecker checker = new ConnectionChecker();
        ConnectionCheck check = spy(checker.getChecker("abc", 1111, checkInterval, 1, 0));
        doReturn(mock(Socket.class)).when(check).makeSocket("abc", 1111);
        assertThat(check.tcpCheck()).isEqualTo(ConnectionCheck.TCP_CHECK_SUCCESS);

        // If our interval to check the request is 1000ms then connection check is needed
        TimeUnit.SECONDS.sleep(2);
        assertThat(check.isCheckNeeded()).isTrue();

        // If the interval to check 1000 ms then we won't need a check
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(check.isCheckNeeded()).isFalse();
    }

    @Test
    public void testConnectionCheckerFailures()
            throws Exception
    {
        int checkInterval = 1000; //in ms
        ConnectionChecker checker = new ConnectionChecker();

        // Test failed connection check
        String host = "xyz";
        int port = 1111;
        int failcount = 3;
        int disableDuration = 10; //in sec
        ConnectionCheck check = spy(checker.getChecker(host, port, checkInterval,
                failcount, disableDuration));
        doAnswer(invocation -> {
            throw new UnknownHostException(host);
        }).when(check).makeSocket(host, port);

        // test the disable check
        for (int i = 0; i < failcount; ++i) {
            // wait for checkinterval, fail till failcount is reached to disable the check
            TimeUnit.MILLISECONDS.sleep(1100);
            assertThat(check.tcpCheck()).isNotEqualTo(ConnectionCheck.TCP_CHECK_SUCCESS);
        }
        // Make the server available and check if the failure contines for disableDuration
        doReturn(mock(Socket.class)).when(check).makeSocket(host, port);
        TimeUnit.SECONDS.sleep(6);
        assertThat(check.tcpCheck()).isNotEqualTo(ConnectionCheck.TCP_CHECK_SUCCESS);
        TimeUnit.SECONDS.sleep(6);
        assertThat(check.tcpCheck()).isEqualTo(ConnectionCheck.TCP_CHECK_SUCCESS);

        // We maintain a map of the checkers, so verify that we got the same check back, as before
        Object checkObj = checker.getChecker(host, port, checkInterval,
                failcount, disableDuration);
        assertThat(checker.getChecker(host, port, checkInterval, failcount, disableDuration))
                .isEqualTo(checkObj);

        assertThat(checker.getChecker(host, port, checkInterval + 10, failcount, disableDuration))
                .isNotEqualTo(checkObj);
        assertThat(checker.getChecker(host, port, checkInterval, failcount + 1, disableDuration))
                .isNotEqualTo(checkObj);
        assertThat(checker.getChecker(host, port, checkInterval, failcount, disableDuration + 10))
                .isNotEqualTo(checkObj);
    }
}
