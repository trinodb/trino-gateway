package io.trino.gateway.ha.clustermonitor;

import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_QUEUED_LIST_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.UI_LOGIN_PATH;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.trino.gateway.ha.config.BackendStateConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterStatsHttpMonitor  implements ClusterStatsMonitor
{
  private static final Logger log = LoggerFactory.getLogger(ClusterStatsHttpMonitor.class);
  private final String SESSION_USER = "sessionUser";

  private final BackendStateConfiguration backendStateConfiguration;

  public ClusterStatsHttpMonitor(BackendStateConfiguration backendStateConfiguration) {
    this.backendStateConfiguration = backendStateConfiguration;
  }

  @Override
  public ClusterStats monitor(ProxyBackendConfiguration backend) {
    ClusterStats clusterStats = new ClusterStats();
    clusterStats.setClusterId(backend.getName());

    // Fetch Cluster level Stats.
    String response = queryCluster(backend, UI_API_STATS_PATH);
    if (Strings.isNullOrEmpty(response)) {
      log.error("Received null/empty response for {}", UI_API_STATS_PATH);
      return clusterStats;
    }

    try {
      HashMap<String, Object> result = null;
      result = new ObjectMapper().readValue(response, HashMap.class);

      clusterStats.setNumWorkerNodes((int) result.get("activeWorkers"));
      clusterStats.setQueuedQueryCount((int) result.get("queuedQueries"));
      clusterStats.setRunningQueryCount((int) result.get("runningQueries"));
      clusterStats.setBlockedQueryCount((int) result.get("blockedQueries"));
      clusterStats.setHealthy(clusterStats.getNumWorkerNodes() > 0);
      clusterStats.setProxyTo(backend.getProxyTo());
      clusterStats.setExternalUrl(backend.getExternalUrl());
      clusterStats.setRoutingGroup(backend.getRoutingGroup());

    } catch (Exception e) {
      log.error("Error parsing cluster stats from [{}]", response, e);
    }

    // Fetch User Level Stats.
    Map<String, Integer> clusterUserStats = new HashMap<>();
    response = queryCluster(backend, UI_API_QUEUED_LIST_PATH);
    if (Strings.isNullOrEmpty(response)) {
      log.error("Received null/empty response for {}", UI_API_QUEUED_LIST_PATH);
      return clusterStats;
    }
    try {
      List<Map<String, Object>> queries = new ObjectMapper().readValue(response,
          new TypeReference<List<Map<String, Object>>>() {
          });

      for (Map<String, Object> q : queries) {
        String user = (String) q.get(SESSION_USER);
        clusterUserStats.put(user, clusterUserStats.getOrDefault(user, 0) + 1);
      }
    } catch (Exception e) {
      log.error("Error parsing cluster user stats: {}", e);
    }
    clusterStats.setUserQueuedCount(clusterUserStats);

    return clusterStats;
  }

  private OkHttpClient acquireClientWithCookie(String loginUrl) {
    UiApiCookieJar cookieJar = new UiApiCookieJar();
    OkHttpClient client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
    RequestBody formBody = new FormBody.Builder()
        .add("username", backendStateConfiguration.getUsername())
        .add("password", backendStateConfiguration.getPassword())
        .build();
    Request loginRequest = new Request.Builder()
        .url(HttpUrl.parse(loginUrl))
        .post(formBody)
        .build();

    Call call = client.newCall(loginRequest);

    try (Response res = call.execute()) {
      log.info("login request received response code {}", res.code());
      return client;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private String queryCluster(ProxyBackendConfiguration backend, String path) {
    String loginUrl = backend.getProxyTo() + UI_LOGIN_PATH;
    OkHttpClient client = acquireClientWithCookie(loginUrl);
    if (client == null) {
      log.error("Client received is null");
      return null;
    }

    String targetUrl = backend.getProxyTo() + path;
    Request request = new Request.Builder()
        .url(HttpUrl.parse(targetUrl))
        .get()
        .build();

    Call call = client.newCall(request);

    try (Response res = call.execute()) {
      switch (res.code()) {
        case HttpStatus.SC_OK:
          return res.body().string();
        case HttpStatus.SC_UNAUTHORIZED:
          log.info("Unauthorized to fetch cluster stats");
          log.debug("username: {}, targetUrl: {}, cookieStore: {}",
              backendStateConfiguration.getUsername(),
              targetUrl,
              client.cookieJar().loadForRequest(HttpUrl.parse(targetUrl)));
          return null;
        default:
          return null;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
