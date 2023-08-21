package com.lyft.data.gateway.ha.clustermonitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.lyft.data.gateway.ha.config.BackendStateConfiguration;
import com.lyft.data.gateway.ha.config.ProxyBackendConfiguration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lyft.data.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_QUEUED_LIST_PATH;
import static com.lyft.data.gateway.ha.handler.QueryIdCachingProxyHandler.UI_API_STATS_PATH;
import static com.lyft.data.gateway.ha.handler.QueryIdCachingProxyHandler.UI_LOGIN_PATH;

@Slf4j
public class ClusterStatsHttpMonitor implements ClusterStatsMonitor {

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

    try {
      Response res = client.newCall(loginRequest).execute();
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

    try {
      Request request = new Request.Builder().url(targetUrl).get().build();
      Response res = client.newCall(request).execute();
      switch (res.code()) {
        case HttpStatus.SC_OK:
          return res.body().string();
        case HttpStatus.SC_UNAUTHORIZED:
          log.info("Unauthorized to fetch cluster stats");
          log.debug("username: {}, targetUrl: {}, cookieStore: {}",
              backendStateConfiguration.getUsername(),
              targetUrl,
              client.cookieJar().loadForRequest(HttpUrl.parse(targetUrl)));
        default:
          return null;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

}
