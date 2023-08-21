package io.trino.gateway.ha.clustermonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class UiApiCookieJar implements CookieJar {
  private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

  @Override
  public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
    String addr = url.host() + ":" + url.port();
    cookieStore.put(addr, cookies);
  }

  @Override
  public List<Cookie> loadForRequest(HttpUrl url) {
    String addr = url.host() + ":" + url.port();
    return cookieStore.getOrDefault(addr, new ArrayList<>());
  }
}
