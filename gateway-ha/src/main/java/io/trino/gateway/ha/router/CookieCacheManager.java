package io.trino.gateway.ha.router;

import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.CookieBackend;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CookieCacheManager {
  private JdbcConnectionManager connectionManager;
  
  public CookieCacheManager(JdbcConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }
  
  public void submitCookieBackend(String cookie, String backend) {
    try {
      connectionManager.open();
      CookieBackend dao = new CookieBackend();
      log.debug(String.format("Writing cookie %s for backend %s", cookie, backend));
      CookieBackend.create(dao, cookie, backend);
    } catch (Exception e) {
      log.warn(String.format("Error saving cookie %s for backend %s: %s", cookie, backend, e));
    } finally {
      connectionManager.close();
    }
  }
  
  public Optional<String> getBackendForCookie(String cookie) {
    try {
      connectionManager.open();
      CookieBackend cookieBackend = CookieBackend.findById(cookie);
      if (cookieBackend != null) {
        return Optional.of((String) cookieBackend.get(CookieBackend.backend));
      }
      return Optional.empty();
    } finally {
      connectionManager.close();
    }
  }
  
  public boolean removeCookie(String cookie) {
    try {
      connectionManager.open();
      CookieBackend cookieBackend = CookieBackend.findById(cookie);
      return cookieBackend.delete();
    } finally {
      connectionManager.close();
    }
  }
}