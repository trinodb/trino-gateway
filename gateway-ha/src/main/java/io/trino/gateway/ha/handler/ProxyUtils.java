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
package io.trino.gateway.ha.handler;

import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.HeaderName;
import io.airlift.log.Logger;
import io.trino.gateway.ha.router.OAuth2RoutingUtils;
import io.trino.gateway.ha.router.TrinoQueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_QUERY_PROPERTIES;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_UI_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_QUERY_PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

public final class ProxyUtils
{
    public static final HeaderName SOURCE_HEADER = HeaderName.of("X-Trino-Source");
    public static final String AUTHORIZATION = "Authorization";

    private static final Logger log = Logger.get(ProxyUtils.class);
    /// A Trino query ID of the form `YYYYMMDD_hhmmss_index_coordinatorId`, as created by
    /// [`QueryIdGenerator`](https://github.com/trinodb/trino/blob/master/core/trino-main/src/main/java/io/trino/execution/QueryIdGenerator.java)
    /// in Trino. The coordinator ID is random lowercase base32, and Trino rejects query IDs with other
    /// characters than lowercase letters, digits, and underscores.
    private static final String QUERY_ID_REGEX = "\\d+_\\d+_\\d+_[a-z0-9]+";
    private static final Pattern QUERY_ID_PATTERN = Pattern.compile(QUERY_ID_REGEX);
    /// Matches query IDs as they appear in the path of a URL. The query ID must be preceded by a `/`,
    /// and must not be followed by another letter, digit, or underscore.
    private static final Pattern QUERY_ID_PATH_PATTERN = Pattern.compile(".*/(" + QUERY_ID_REGEX + ")(?![A-Za-z0-9_]).*");
    /// Matches query IDs as they appear in the query parameters of a URL. The query ID must be at the beginning
    /// of the query string, or be preceded by `%2F` (a URL-encoded `/`) or `query_id=`, with or without the
    /// underscore and in any capitalization. It must not be followed by another letter, digit, or underscore.
    private static final Pattern QUERY_ID_PARAM_PATTERN = Pattern.compile(".*(?:%2F|(?i)query_?id(?-i)=|^)(" + QUERY_ID_REGEX + ")(?![A-Za-z0-9_]).*");
    private static final Set<String> QUERY_STATE_PATH = ImmutableSet.of("queued", "scheduled", "executing");
    private static final String PARTIAL_CANCEL_PATH = "partialCancel";

    private ProxyUtils() {}

    public static Optional<String> extractQueryIdIfPresent(
            HttpServletRequest request,
            List<String> statementPaths)
    {
        String path = request.getRequestURI();
        String queryParams = request.getQueryString();
        if (!request.getMethod().equals(HttpMethod.POST)) {
            return extractQueryIdIfPresent(path, queryParams, statementPaths);
        }
        String queryText;
        try {
            queryText = new InputStreamReader(request.getInputStream(), UTF_8).readAllAsString();
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading request body", e);
        }
        if (!isNullOrEmpty(queryText) && queryText.toLowerCase(ENGLISH).contains("kill_query")) {
            TrinoQueryProperties properties = (TrinoQueryProperties) request.getAttribute(TRINO_QUERY_PROPERTIES);
            if (properties == null) {
                log.debug("Cannot route kill_query to the cluster of the query, because requestAnalyzerConfig.analyzeRequest is false");
                return Optional.empty();
            }
            return properties.getQueryId()
                    .filter(ProxyUtils::isQueryId);
        }
        return Optional.empty();
    }

    public static Optional<String> extractQueryIdIfPresent(String path, String queryParams, List<String> statementPaths)
    {
        return extractQueryIdCandidate(path, queryParams, statementPaths).filter(ProxyUtils::isQueryId);
    }

    private static boolean isQueryId(String queryId)
    {
        return QUERY_ID_PATTERN.matcher(queryId).matches();
    }

    private static Optional<String> extractQueryIdCandidate(String path, String queryParams, List<String> statementPaths)
    {
        if (path == null) {
            return Optional.empty();
        }
        log.debug("Trying to extract query id from path/queryString [%s]", OAuth2RoutingUtils.redactForLog(path, queryParams));
        // matchingStatementPath should match paths such as /v1/statement/executing/query_id/nonce/sequence_number,
        // and if custom paths are supplied using the statementPaths configuration, paths such as
        // /custom/statement/path/executing/query_id/nonce/sequence_number
        Optional<String> matchingStatementPath = statementPaths.stream().filter(path::startsWith).findAny();
        if (!isNullOrEmpty(queryParams)) {
            Matcher matcher = QUERY_ID_PARAM_PATTERN.matcher(queryParams);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1));
            }
        }
        if (matchingStatementPath.isPresent() || path.startsWith(V1_QUERY_PATH)) {
            path = path.replace(matchingStatementPath.orElse(V1_QUERY_PATH), "");
            String[] tokens = path.split("/");
            if (tokens.length >= 2) {
                if (tokens.length >= 3 && QUERY_STATE_PATH.contains(tokens[1])) {
                    if (tokens.length >= 4 && tokens[2].equals(PARTIAL_CANCEL_PATH)) {
                        return Optional.of(tokens[3]);
                    }
                    return Optional.of(tokens[2]);
                }
                return Optional.of(tokens[1]);
            }
        }
        else if (path.startsWith(TRINO_UI_PATH)) {
            Matcher matcher = QUERY_ID_PATH_PATTERN.matcher(path);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    public static URI buildUriWithNewCluster(String backendHost, HttpServletRequest request)
    {
        return URI.create(backendHost + request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
    }
}
