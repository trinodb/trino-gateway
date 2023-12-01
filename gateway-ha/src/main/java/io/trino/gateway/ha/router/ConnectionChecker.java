package io.trino.gateway.ha.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class ConnectionChecker {

  private static final Logger log = LoggerFactory.getLogger(ConnectionChecker.class);
  private HashMap<String, ConnectionCheck> connectionChecks;

  ConnectionChecker() {
    connectionChecks = new HashMap<String, ConnectionCheck>();
  }

  ConnectionCheck getChecker(String server, int port, int interval,
                             int failcount, int disableDuration) {
    String key = String.format("%s-%d-%d-%d-%d",
        server, port, interval, failcount, disableDuration);
    log.info("key is {}", key);
    ConnectionCheck obj = connectionChecks.get(key);

    if (obj == null) {
      log.info("didn't find key {}", key);
      obj = new ConnectionCheck(server, port, interval, failcount, disableDuration);
      connectionChecks.put(key, obj);
    }

    return obj;
  }

  /**
   * Check the tcp connectivity to be used in the routing rules.
   *
   * @param server server to connect for the check
   * @param port port to connect for the check
   * @param interval minimum time between 2 checks, in ms
   * @param failCount how many times the check needs to fail consecutively
   * before we call it a failure
   * @param disableDuration how long the check should be disabled
   * after it has failed in seconds
   */
  public boolean tcpCheck(String server, int port,
                          int interval, int failCount, int disableDuration) {
    ConnectionCheck checker = getChecker(server, port, interval, failCount, disableDuration);
    return checker.tcpCheck() == ConnectionCheck.TCP_CHECK_SUCCESS;
  }
}