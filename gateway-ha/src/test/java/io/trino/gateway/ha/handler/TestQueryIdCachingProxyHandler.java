package io.trino.gateway.ha.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler {
  @Test
  public void testExtractQueryIdFromUrl() throws IOException {
    String[] paths = {
        "/ui/api/query/20200416_160256_03078_6b4yt",
        "/ui/api/query/20200416_160256_03078_6b4yt?bogus_fictional_param",
        "/ui/api/query?query_id=20200416_160256_03078_6b4yt",
        "/ui/api/query.html?20200416_160256_03078_6b4yt"};
    for (String path : paths) {
      String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
      assertEquals("20200416_160256_03078_6b4yt", queryId);
    }
    String[] nonPaths = {
        "/ui/api/query/myOtherThing",
        "/ui/api/query/20200416_blah?bogus_fictional_param"};
    for (String path : nonPaths) {
      String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
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
