package io.trino.gateway.ha.router;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class TestTcpChecks {
  public static final String TRINO_SOURCE_HEADER = "X-Trino-Source";
  public static final String TRINO_CLIENT_TAGS_HEADER = "X-Trino-Client-Tags";

  @Test
  public void testTcpRuleCheck() throws
      IOException {
    String host = "localhost";
    int port = 8888;
    int checkInterval = 1000; //in ms
    ConnectionChecker checker = mock(ConnectionChecker.class);
    when(checker.tcpCheck(host, port, checkInterval, 1, 0)).thenReturn(true);

    HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getAttribute("connectionChecker")).thenReturn(checker);

    String rulesFile = "src/test/resources/rules/routing_rules_tcp_connection.yml";
    RoutingGroupSelector routingGroupSelector =
        RoutingGroupSelector.byRoutingRulesEngine(rulesFile);

    Assert.assertEquals(routingGroupSelector.findRoutingGroup(mockRequest), "cli");
  }

  @Test
  public void testConnectionCheckerSucess() throws
      IOException,
      InterruptedException {
    // Test successful connection check  
    int checkInterval = 1000; //in ms
    ConnectionChecker checker = new ConnectionChecker();
    ConnectionCheck check = spy(checker.getChecker("abc", 1111, checkInterval, 1, 0));
    doReturn(mock(Socket.class)).when(check).makeSocket("abc", 1111);
    Assert.assertTrue(check.tcpCheck() == ConnectionCheck.TCP_CHECK_SUCCESS);

    // If our inteval to check the request is 1000ms then connection check is needed
    TimeUnit.SECONDS.sleep(2);
    Assert.assertTrue(check.isCheckNeeded());

    // If the inerval to check 1000 ms then we won't need a check
    TimeUnit.MILLISECONDS.sleep(100);
    Assert.assertFalse(check.isCheckNeeded());
  }

  @Test
  public void testConnectionCheckerFailures() throws
      IOException,
      java.lang.InterruptedException {

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
      Assert.assertTrue(check.tcpCheck() != ConnectionCheck.TCP_CHECK_SUCCESS);
    }
    // Make the server available and check if the failure contines for disableDuration
    doReturn(mock(Socket.class)).when(check).makeSocket(host, port);
    TimeUnit.SECONDS.sleep(6);
    Assert.assertTrue(check.tcpCheck() != ConnectionCheck.TCP_CHECK_SUCCESS);
    TimeUnit.SECONDS.sleep(6);
    Assert.assertTrue(check.tcpCheck() == ConnectionCheck.TCP_CHECK_SUCCESS);

    // We maintain a map of the checkers, so verify that we got the same check back, as before
    Object checkObj = checker.getChecker(host, port, checkInterval,
        failcount, disableDuration);
    Assert.assertTrue(checkObj == checker.getChecker(host, port, checkInterval,
        failcount, disableDuration));

    Assert.assertFalse(checkObj == checker.getChecker(host, port, checkInterval + 10,
        failcount, disableDuration));
    Assert.assertFalse(checkObj == checker.getChecker(host, port, checkInterval,
        failcount + 1, disableDuration));
    Assert.assertFalse(checkObj == checker.getChecker(host, port, checkInterval,
        failcount, disableDuration + 10));
  }
}
