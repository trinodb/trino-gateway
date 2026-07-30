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
package io.trino.gateway.ha.router;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.trino.gateway.ha.config.HaGatewayConfiguration;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.trino.gateway.ha.handler.HttpUtils.OAUTH_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_UI_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_INFO_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_NODE_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_QUERY_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_SPOOLED_PATH;
import static java.util.Objects.requireNonNull;

/**
 * A filter component that determines whether a given path should be whitelisted
 * for routing to Trino clusters.
 */
public class PathFilter
{
    private static final Pattern PATH_SEPARATOR = Pattern.compile("/");

    private final Set<String> statementPaths;
    private final List<Pattern> extraWhitelistPatterns;

    @Inject
    public PathFilter(HaGatewayConfiguration config)
    {
        this(config.getStatementPaths(), config.getExtraWhitelistPaths());
    }

    @VisibleForTesting
    PathFilter(List<String> statementPaths, List<String> extraWhitelistPaths)
    {
        this.statementPaths = Set.copyOf(requireNonNull(statementPaths, "Required configuration 'statementPaths' can't be null"));
        this.extraWhitelistPatterns = requireNonNull(extraWhitelistPaths, "extraWhitelistPaths cannot be null").stream()
                .map(pattern -> {
                    try {
                        return Pattern.compile(pattern);
                    }
                    catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("Invalid regex pattern: " + pattern, e);
                    }
                })
                .collect(toImmutableList());
    }

    /**
     * Determines if the given path is whitelisted for routing to backend.
     *
     * @param path the request path to check
     * @return true if the path should be routed to backend, false otherwise
     */
    public boolean isPathWhiteListed(String path)
    {
        return statementPaths.stream().anyMatch(path::startsWith)
                || path.startsWith(V1_QUERY_PATH)
                || path.startsWith(V1_SPOOLED_PATH)
                || path.startsWith(TRINO_UI_PATH)
                || path.startsWith(V1_INFO_PATH)
                || path.startsWith(V1_NODE_PATH)
                || path.startsWith(UI_API_STATS_PATH)
                || path.startsWith(OAUTH_PATH)
                || extraWhitelistPatterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    // Complement of an exemption list, so anything else routed to a backend fails closed
    public boolean requiresBackendAuthentication(String path)
    {
        if (path == null) {
            return true;
        }
        try {
            return !isExemptFromBackendAuthentication(normalize(path));
        }
        catch (IllegalArgumentException e) {
            return true;
        }
    }

    // Browser and health endpoints, which cannot present a client certificate
    private static boolean isExemptFromBackendAuthentication(String path)
    {
        return matchesPathBoundary(path, TRINO_UI_PATH)
                || matchesPathBoundary(path, OAUTH_PATH)
                || matchesPathBoundary(path, V1_INFO_PATH)
                || matchesPathBoundary(path, V1_NODE_PATH);
    }

    // Decodes percent-encoding and drops matrix parameters and dot segments, so this agrees with what the backend resolves the path to
    @VisibleForTesting
    static String normalize(String path)
    {
        Deque<String> segments = new ArrayDeque<>();
        for (String rawSegment : PATH_SEPARATOR.split(decodePath(path))) {
            int matrixParameter = rawSegment.indexOf(';');
            String segment = matrixParameter < 0 ? rawSegment : rawSegment.substring(0, matrixParameter);
            switch (segment) {
                case "", "." -> {}
                case ".." -> segments.pollLast();
                default -> segments.addLast(segment);
            }
        }
        return "/" + String.join("/", segments);
    }

    private static String decodePath(String path)
    {
        String absolutePath = path.startsWith("/") ? path : "/" + path;
        return URI.create("http://localhost" + absolutePath).getPath();
    }

    private static boolean matchesPathBoundary(String path, String prefix)
    {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
