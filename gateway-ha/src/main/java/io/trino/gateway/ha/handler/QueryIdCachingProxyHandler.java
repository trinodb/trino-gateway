package io.trino.gateway.ha.handler;

import com.codahale.metrics.Meter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.proxyserver.ProxyHandler;
import io.trino.gateway.proxyserver.wrapper.MultiReadHttpServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Callback;

@Slf4j
public class QueryIdCachingProxyHandler extends ProxyHandler {
  public static final String PROXY_TARGET_HEADER = "proxytarget";
  public static final String V1_STATEMENT_PATH = "/v1/statement";
  public static final String V1_QUERY_PATH = "/v1/query";
  public static final String V1_INFO_PATH = "/v1/info";
  public static final String V1_NODE_PATH = "/v1/node";
  public static final String UI_API_STATS_PATH = "/ui/api/stats";
  public static final String UI_LOGIN_PATH = "/ui/login";
  public static final String UI_API_QUEUED_LIST_PATH = "/ui/api/query?state=QUEUED";
  public static final String TRINO_UI_PATH = "/ui";
  public static final String OAUTH_PATH = "/oauth2";
  public static final String AUTHORIZATION = "Authorization";
  public static final String USER_HEADER = "X-Trino-User";
  public static final String SOURCE_HEADER = "X-Trino-Source";
  public static final String HOST_HEADER = "Host";
  private static final int QUERY_TEXT_LENGTH_FOR_HISTORY = 200;
  private static final Pattern QUERY_ID_PATTERN = Pattern.compile(".*[/=?](\\d+_\\d+_\\d+_\\w+).*");

  private static final Pattern EXTRACT_BETWEEN_SINGLE_QUOTES = Pattern.compile("'([^\\s']+)'");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final RoutingManager routingManager;
  private final RoutingGroupSelector routingGroupSelector;
  private final QueryHistoryManager queryHistoryManager;

  private final Meter requestMeter;
  private final int serverApplicationPort;
  private final List<String> extraWhitelistPaths;
  private final List<String> extraStatementPaths;

  private final Map<Integer, String> requestIdBackendMap = new HashMap<>();
  private final Set<String> cookiePaths;
  private final Set<String> logoutCookiePaths;

  public QueryIdCachingProxyHandler(
      QueryHistoryManager queryHistoryManager,
      RoutingManager routingManager,
      RoutingGroupSelector routingGroupSelector,
      int serverApplicationPort,
      Meter requestMeter,
      List<String> extraWhitelistPaths,
      List<String> extraStatementPaths,
      Set<String> cookiePaths,
      Set<String> logoutCookiePaths) {
    this.requestMeter = requestMeter;
    this.routingManager = routingManager;
    this.routingGroupSelector = routingGroupSelector;
    this.queryHistoryManager = queryHistoryManager;
    this.serverApplicationPort = serverApplicationPort;
    this.extraWhitelistPaths = extraWhitelistPaths;
    this.extraStatementPaths = extraStatementPaths;
    this.cookiePaths = cookiePaths;
    this.logoutCookiePaths = logoutCookiePaths;
  }

  protected String extractQueryIdIfPresent(String path, String queryParams) {
    if (path == null) {
      return null;
    }
    String queryId = null;

    log.debug("trying to extract query id from  path [{}] or queryString [{}]", path, queryParams);
    if (path.startsWith(V1_STATEMENT_PATH) || path.startsWith(V1_QUERY_PATH)) {
      String[] tokens = path.split("/");
      if (tokens.length >= 4) {
        if (path.contains("queued")
            || path.contains("scheduled")
            || path.contains("executing")
            || path.contains("partialCancel")) {
          queryId = tokens[4];
        } else {
          queryId = tokens[3];
        }
      }
    } else if (path.startsWith(TRINO_UI_PATH) ||
            extraStatementPaths.stream().anyMatch(s -> path.startsWith(s))) {
      Matcher matcher = QUERY_ID_PATTERN.matcher(path);
      if (matcher.matches()) {
        queryId = matcher.group(1);
      }
    }
    log.debug("query id in url [{}]", queryId);
    return queryId;
  }

  protected String extractQueryIdIfPresent(HttpServletRequest request) {
    String path = request.getRequestURI();
    String queryParams = request.getQueryString();
    try {
      String queryText = CharStreams.toString(request.getReader());
      if (!Strings.isNullOrEmpty(queryText)
          && queryText.toLowerCase().contains("system.runtime.kill_query")) {
        // extract and return the queryId
        String[] parts = queryText.split(",");
        for (String part : parts) {
          if (part.contains("query_id")) {
            Matcher m = EXTRACT_BETWEEN_SINGLE_QUOTES.matcher(part);
            if (m.find()) {
              String queryQuoted = m.group();
              if (!Strings.isNullOrEmpty(queryQuoted) && queryQuoted.length() > 0) {
                return queryQuoted.substring(1, queryQuoted.length() - 1);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Error extracting query payload from request", e);
    }

    return extractQueryIdIfPresent(path, queryParams);
  }

  String getBackendForRequest(HttpServletRequest request) {
    String routingGroup = routingGroupSelector.findRoutingGroup(request);
    String user = getQueryUser(request);
    if (!Strings.isNullOrEmpty(routingGroup)) {
      // This falls back on adhoc backend if there are no cluster found for the routing group.
      return routingManager.provideBackendForRoutingGroup(routingGroup, user);
    } else {
      return routingManager.provideAdhocBackend(user);
    }
  }

  private boolean doRecordQueryId(HttpServletRequest request) {
    String requestPath = request.getRequestURI();
    return (requestPath.startsWith(V1_STATEMENT_PATH)
            || extraStatementPaths.stream().anyMatch(s -> requestPath.startsWith(s)))
            && request.getMethod().equals(HttpMethod.POST);
    //TODO: add queryPaths config
  }

  static void setForwardedHostHeaderOnProxyRequest(HttpServletRequest request,
                                                   Request proxyRequest) {
    if (request.getHeader(PROXY_TARGET_HEADER) != null) {
      try {
        URI backendUri = new URI(request.getHeader(PROXY_TARGET_HEADER));
        StringBuilder hostName = new StringBuilder();
        hostName.append(backendUri.getHost());
        if (backendUri.getPort() != -1) {
          hostName.append(":").append(backendUri.getPort());
        }
        String overrideHostName = hostName.toString();
        log.debug("Incoming Request Host header : [{}], proxy request host header : [{}]",
            request.getHeader(HOST_HEADER), overrideHostName);

        proxyRequest.header(HOST_HEADER, overrideHostName);
      } catch (URISyntaxException e) {
        log.warn(e.toString());
      }
    } else {
      log.warn("Proxy Target not set on request, unable to decipher HOST header");
    }
  }

  static String getQueryUser(HttpServletRequest request) {
    String trinoUser = request.getHeader(USER_HEADER);

    if (!Strings.isNullOrEmpty(trinoUser)) {
      log.info("user from %s", USER_HEADER);
      return trinoUser;
    }

    log.info("user from basic auth");
    String user = "";
    String header = request.getHeader(AUTHORIZATION);
    if (header == null) {
      log.error("didn't find any basic auth header");
      return user;
    }

    int space = header.indexOf(' ');
    if ((space < 0) || !header.substring(0, space).equalsIgnoreCase("basic")) {
      log.error("basic auth format is incorrect");
      return user;
    }

    String headerInfo = header.substring(space + 1).trim();
    if (Strings.isNullOrEmpty(headerInfo)) {
      log.error("The encoded value of basic auth doesn't exist");
      return user;
    }

    String info = new String(Base64.getDecoder().decode(headerInfo));
    List<String> parts = Splitter.on(':').limit(2).splitToList(info);
    if (parts.size() < 1) {
      log.error("no user inside the basic auth text");
      return user;
    }
    return parts.get(0);
  }

  @Override
  public void preConnectionHook(HttpServletRequest request, Request proxyRequest) {
    if (request.getMethod().equals(HttpMethod.POST)
        && (request.getRequestURI().startsWith(V1_STATEMENT_PATH)
            || extraStatementPaths.stream().anyMatch(
                    s -> request.getRequestURI().startsWith(s)))){
      requestMeter.mark();
      try {
        String requestBody = CharStreams.toString(request.getReader());
        log.info(
            "Processing request endpoint: [{}], payload: [{}]",
            request.getRequestURI(),
            requestBody);
        debugLogHeaders(request);
      } catch (Exception e) {
        log.warn("Error fetching the request payload", e);
      }
    }

    if (isPathWhiteListed(request.getRequestURI())) {
      setForwardedHostHeaderOnProxyRequest(request, proxyRequest);
    }

  }

  private boolean isPathWhiteListed(String path) {
    return path.startsWith(V1_STATEMENT_PATH)
        || path.startsWith(V1_QUERY_PATH)
        || path.startsWith(TRINO_UI_PATH)
        || path.startsWith(V1_INFO_PATH)
        || path.startsWith(V1_NODE_PATH)
        || path.startsWith(UI_API_STATS_PATH)
        || path.startsWith(OAUTH_PATH)
        || extraWhitelistPaths.stream().anyMatch(s -> path.startsWith(s))
        || extraStatementPaths.stream().anyMatch(s -> path.startsWith(s));
  }

  public boolean isAuthEnabled() {
    return false;
  }

  public boolean handleAuthRequest(HttpServletRequest request) {
    return true;
  }

  public boolean isKnownSessionId(String sessionId) {
    return !Strings.isNullOrEmpty(routingManager.findBackendForUiCookie(sessionId));
  }

  @Override
  public Optional<Cookie> deleteCookie(HttpServletRequest clientRequest) {
    if (!logoutCookiePaths.contains(clientRequest.getRequestURI())) {
      return Optional.empty();
    }
    Optional<Cookie> cookie = Arrays.stream(clientRequest.getCookies()).filter(
        c -> c.getName().equalsIgnoreCase("JSESSIONID")).findAny();
    Optional<String> sessionId = cookie.map(cookie1 -> cookie1.getValue().split("\\.")[0]);
    Optional<String> path = cookie.map(cookie1 -> cookie1.getPath());
    if (cookie.isPresent()) {
      cookie.get().setMaxAge(0);
      cookie.get().setValue("delete");
      cookie.get().setPath(path.orElse("/"));

      routingManager.deleteUiCookie(sessionId.get());
      return cookie;
    }
    return Optional.empty();
  }

  @Override
  public String rewriteTarget(HttpServletRequest request, int requestId) {
    /* Here comes the load balancer / gateway */
    String backendAddress = "http://localhost:" + serverApplicationPort;

    // Only load balance trino query and oauth APIs.
    if (isPathWhiteListed(request.getRequestURI())) {
      String queryId = extractQueryIdIfPresent(request);

      // Find query id and get url from cache
      if (!Strings.isNullOrEmpty(queryId)) {
        backendAddress = routingManager.findBackendForQueryId(queryId);
      } else if (doRecordQueryId(request)) {
        backendAddress = getBackendForRequest(request);
        log.debug("mapping " + requestId + " to " + backendAddress);
        requestIdBackendMap.put(requestId, backendAddress);
      } else if (!Strings.isNullOrEmpty(request.getRequestedSessionId())) {
        //pin browser sessions to the same backend based on jsessionid, but load balance queries
        backendAddress = routingManager.findBackendForUiCookie(
                request.getRequestedSessionId().split("\\.")[0]);
        if (Strings.isNullOrEmpty(backendAddress)) {
          log.error("Unknown session id: " + request.getRequestedSessionId()
                  + " for request to: " + request.getRequestURI());
          backendAddress = getBackendForRequest(request);
        }
      } else {
        backendAddress = getBackendForRequest(request);
        if (cookiePaths.contains(request.getRequestURI())) {
          routingManager.setBackendForCookie(request.getSession().getId(), backendAddress);
          log.debug("using session id " + request.getSession().getId());
        }
      }
      // set target backend so that we could save queryId to backend mapping later.
      ((MultiReadHttpServletRequest) request).addHeader(PROXY_TARGET_HEADER, backendAddress);
    }

    if (isAuthEnabled() && request.getHeader("Authorization") != null) {
      if (!handleAuthRequest(request)) {
        // This implies the AuthRequest was not authenticated, hence we error out from here.
        log.info("Could not authenticate Request: " + request.toString());
        return null;
      }
    }

    String targetLocation =
        backendAddress
            + request.getRequestURI()
            + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

    String originalLocation =
        request.getScheme()
            + "://"
            + request.getRemoteHost()
            + ":"
            + request.getServerPort()
            + request.getRequestURI()
            + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

    log.info("Rerouting [{}]--> [{}]", originalLocation, targetLocation);
    return targetLocation;
  }

  protected void postConnectionHook(
      HttpServletRequest request,
      HttpServletResponse response,
      byte[] buffer,
      int offset,
      int length,
      Callback callback,
      int requestId) {
    try {
      if (doRecordQueryId(request)) {
        recordBackendForQueryId(request, response, buffer, requestId);
      } else {
        log.debug("SKIPPING For {}", request.getRequestURI());
      }
    } catch (Exception e) {
      log.error("Error in proxying falling back to super call", e);
    }
    super.postConnectionHook(request, response, buffer, offset, length, callback);
  }

  void recordBackendForQueryId(
          HttpServletRequest request,
          HttpServletResponse response,
          byte[] buffer,
          int requestId)
          throws IOException {
    String output;
    boolean isGZipEncoding = isGZipEncoding(response);
    if (isGZipEncoding) {
      output = plainTextFromGz(buffer);
    } else {
      output = new String(buffer);
    }
    log.debug("For Request [{}] got Response output [{}]", request.getRequestURI(), output);
    log.debug("Request Id: " + requestId);

    QueryHistoryManager.QueryDetail queryDetail = getQueryDetailsFromRequest(request);
    String backendUrl = Strings.isNullOrEmpty(queryDetail.getBackendUrl())
            ? requestIdBackendMap.get(requestId)
            : queryDetail.getBackendUrl();
    if (backendUrl == null) {
      log.warn("request id not found in "
              + Arrays.toString(requestIdBackendMap.keySet().toArray()));
    }
    log.debug("Extracting Proxy destination : [{}] for request : [{}]",
            backendUrl, request.getRequestURI());

    if (response.getStatus() == HttpStatus.OK_200) {
      HashMap<String, String> results = OBJECT_MAPPER.readValue(output, HashMap.class);
      queryDetail.setQueryId(results.get("id"));

      if (!Strings.isNullOrEmpty(queryDetail.getQueryId())) {
        //TODO: use the DB to back the queryId cache so it is shared across gateway instances
        routingManager.setBackendForQueryId(
                queryDetail.getQueryId(), backendUrl);
        log.debug(
                "QueryId [{}] mapped with proxy [{}]",
                queryDetail.getQueryId(),
                backendUrl);
        requestIdBackendMap.remove(requestId);
      } else {
        log.debug("QueryId [{}] could not be cached", queryDetail.getQueryId());
      }
    } else {
      log.error(
              "Non OK HTTP Status code with response [{}] , Status code [{}]",
              output,
              response.getStatus());
    }
    // Saving history at gateway.
    queryHistoryManager.submitQueryDetail(queryDetail);
  }

  private QueryHistoryManager.QueryDetail getQueryDetailsFromRequest(HttpServletRequest request)
      throws IOException {
    QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
    queryDetail.setBackendUrl(request.getHeader(PROXY_TARGET_HEADER));
    queryDetail.setCaptureTime(System.currentTimeMillis());
    queryDetail.setUser(getQueryUser(request));
    queryDetail.setSource(request.getHeader(SOURCE_HEADER));
    String queryText = CharStreams.toString(request.getReader());
    queryDetail.setQueryText(
        queryText.length() > QUERY_TEXT_LENGTH_FOR_HISTORY
            ? queryText.substring(0, QUERY_TEXT_LENGTH_FOR_HISTORY) + "..."
            : queryText);
    return queryDetail;
  }
}
