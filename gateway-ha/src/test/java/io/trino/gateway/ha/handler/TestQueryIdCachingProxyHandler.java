package io.trino.gateway.ha.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.DataStoreConfiguration;
import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.router.CookieCacheManager;
import io.trino.gateway.ha.router.HaGatewayManager;
import io.trino.gateway.ha.router.HaQueryHistoryManager;
import io.trino.gateway.ha.router.HaRoutingManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.RuleReloadingRoutingGroupSelector;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import jakarta.ws.rs.HttpMethod;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler {

  JdbcConnectionManager unconfiguredConnectionManager =
          new JdbcConnectionManager(new DataStoreConfiguration());
  QueryIdCachingProxyHandler queryIdCachingProxyHandler = new QueryIdCachingProxyHandler(
          new HaQueryHistoryManager(unconfiguredConnectionManager),
          new HaRoutingManager(new HaGatewayManager(unconfiguredConnectionManager),
                  new HaQueryHistoryManager(unconfiguredConnectionManager),
                  new CookieCacheManager(unconfiguredConnectionManager)),
          new RoutingGroupSelector() {
            @Override
            public String findRoutingGroup(HttpServletRequest request)
            {
              return null;
            }
          },
          -1,
          new Meter(),
          new ArrayList<>(),
          ImmutableList.of("/ui/insights/api/statement"),
          new HashSet<>(),
          new HashSet<>()
  );

  @Test
  public void testExtractQueryIdFromUrl() throws IOException {
    String[] paths = {
        "/ui/api/query/20200416_160256_03078_6b4yt",
        "/ui/api/query/20200416_160256_03078_6b4yt?bogus_fictional_param",
        "/ui/api/query?query_id=20200416_160256_03078_6b4yt",
        "/ui/api/query.html?20200416_160256_03078_6b4yt",
        "/ui/api/insights/ide/statement/executing/20200416_160256_03078_6b4yt"};

    for (String path : paths) {
      String queryId = queryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
      assertEquals("20200416_160256_03078_6b4yt", queryId);
    }
    String[] nonPaths = {
        "/ui/api/query/myOtherThing",
        "/ui/api/query/20200416_blah?bogus_fictional_param"};
    for (String path : nonPaths) {
      String queryId = queryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
      assertNull(queryId);
    }
  }

  @Test
  public void testForwardedHostHeaderOnProxyRequest() throws IOException {
    String backendServer = "trinocluster";
    String backendPort = "80";
    HttpServletRequest mockServletRequest = Mockito.mock(HttpServletRequest.class);
    Mockito.when(mockServletRequest.getHeader("proxytarget")).thenReturn(String.format("http://%s"
        + ":%s", backendServer, backendPort));
    HttpClient httpClient = new HttpClient();
    Request proxyRequest = httpClient.newRequest("http://localhost:80");
    QueryIdCachingProxyHandler.setForwardedHostHeaderOnProxyRequest(mockServletRequest,
        proxyRequest);
    assertEquals(String.format("%s:%s",
        backendServer, backendPort), proxyRequest.getHeaders().get("Host"));
  }

  @Test
  public void testPreconnectionHook() {
    String backendServer = "trinocluster";
    String backendPort = "80";
    HttpServletRequest mockServletRequest = Mockito.mock(HttpServletRequest.class);
    Mockito.when(mockServletRequest.getHeader("proxytarget")).thenReturn(String.format("http://%s"
            + ":%s", backendServer, backendPort));
    Mockito.when(mockServletRequest.getRequestURI())
            .thenReturn(String.format("/v1/statement", backendServer, backendPort));
    Mockito.when(mockServletRequest.getMethod())
            .thenReturn(HttpMethod.POST);
    HttpClient httpClient = new HttpClient();
    Request proxyRequest = httpClient.newRequest("http://localhost:80");
    queryIdCachingProxyHandler.preConnectionHook(mockServletRequest, proxyRequest);
    assertEquals(String.format("%s:%s",
            backendServer, backendPort), proxyRequest.getHeaders().get("Host"));

    Mockito.reset(mockServletRequest);
    Mockito.when(mockServletRequest.getHeader("proxytarget")).thenReturn(String.format("http://%s"
            + ":%s", backendServer, backendPort));
    Mockito.when(mockServletRequest.getRequestURI())
            .thenReturn(String.format("/ui/api/statement", backendServer, backendPort));
    Mockito.when(mockServletRequest.getMethod())
            .thenReturn(HttpMethod.POST);
    httpClient = new HttpClient();
    proxyRequest = httpClient.newRequest("http://localhost:80");
    queryIdCachingProxyHandler.preConnectionHook(mockServletRequest, proxyRequest);
    assertEquals(String.format("%s:%s",
            backendServer, backendPort), proxyRequest.getHeaders().get("Host"));
  }

  @Test
  public void testUserFromRequest() throws IOException {

    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

    String authHeader = "Basic dGVzdDoxMjPCow==";
    Mockito.when(req.getHeader(QueryIdCachingProxyHandler.AUTHORIZATION))
            .thenReturn(authHeader);
    assertEquals("test", QueryIdCachingProxyHandler.getQueryUser(req));

    String user = "trino_user";
    Mockito.when(req.getHeader(QueryIdCachingProxyHandler.USER_HEADER))
            .thenReturn(user);
    assertEquals(user, QueryIdCachingProxyHandler.getQueryUser(req));
  }
}
