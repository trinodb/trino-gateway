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

import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.gateway.ha.handler.HttpUtils.OAUTH_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.TRINO_UI_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.UI_API_STATS_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_INFO_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_NODE_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_QUERY_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPathFilter
{
    private PathFilter pathFilter;

    TestPathFilter()
    {
        List<String> statementPaths = ImmutableList.of(V1_STATEMENT_PATH, "/v2/statement");
        List<String> extraWhitelistPaths = ImmutableList.of(
                "/api/v1/custom/.*",
                "/health/.*",
                "/metrics");
        pathFilter = new PathFilter(statementPaths, extraWhitelistPaths);
    }

    @Test
    void testHardcodedTrinoQueryPath()
    {
        assertThat(pathFilter.isPathWhiteListed(V1_QUERY_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_QUERY_PATH + "/query123")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_QUERY_PATH + "/query123/status")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(TRINO_UI_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(TRINO_UI_PATH + "/query.html")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(TRINO_UI_PATH + "/assets/app.js")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_INFO_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_INFO_PATH + "/status")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_NODE_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_NODE_PATH + "/node123")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_NODE_PATH + "/node123/status")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(UI_API_STATS_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(UI_API_STATS_PATH + "/running")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(UI_API_STATS_PATH + "/completed")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(OAUTH_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(OAUTH_PATH + "/callback")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(OAUTH_PATH + "/token")).isTrue();
    }

    @Test
    void testConfiguredStatementPaths()
    {
        // Test V1 statement path
        assertThat(pathFilter.isPathWhiteListed(V1_STATEMENT_PATH)).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_STATEMENT_PATH + "/executing")).isTrue();
        assertThat(pathFilter.isPathWhiteListed(V1_STATEMENT_PATH + "/queued")).isTrue();

        // Test V2 statement path (from our configuration)
        assertThat(pathFilter.isPathWhiteListed("/v2/statement")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/v2/statement/query456")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/v2/statement/batch")).isTrue();
    }

    @Test
    void testDynamicRegexPaths()
    {
        // Test custom API regex pattern "/api/v1/custom/.*"
        assertThat(pathFilter.isPathWhiteListed("/api/v1/custom/")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/api/v1/custom/users")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/api/v1/custom/users/123")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/api/v1/custom/reports/daily")).isTrue();

        // Test health check regex pattern "/health/.*"
        assertThat(pathFilter.isPathWhiteListed("/health/")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/health/status")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/health/ready")).isTrue();
        assertThat(pathFilter.isPathWhiteListed("/health/live")).isTrue();

        // Test exact match for metrics
        assertThat(pathFilter.isPathWhiteListed("/metrics")).isTrue();
    }

    @Test
    void testNonWhitelistedPaths()
    {
        assertThat(pathFilter.isPathWhiteListed("/v3/statement")).isFalse(); // Not in our statement paths
        assertThat(pathFilter.isPathWhiteListed("/api/v2/custom/users")).isFalse(); // Doesn't match v1 pattern
        assertThat(pathFilter.isPathWhiteListed("/status")).isFalse(); // Not health/status
        assertThat(pathFilter.isPathWhiteListed("/metrics/extra")).isFalse(); // metrics is exact match only
        assertThat(pathFilter.isPathWhiteListed("")).isFalse(); // Empty path
        assertThat(pathFilter.isPathWhiteListed("/")).isFalse(); // Root path
    }

    @Test
    void testEdgeCases()
    {
        // Test case sensitivity
        assertThat(pathFilter.isPathWhiteListed("/V1/query")).isFalse(); // Case sensitive
        assertThat(pathFilter.isPathWhiteListed("/API/v1/custom/test")).isFalse(); // Case sensitive

        // Test partial matches
        assertThat(pathFilter.isPathWhiteListed("/v1/quer")).isFalse(); // Partial match of hardcoded path
        assertThat(pathFilter.isPathWhiteListed("/api/v1/custo")).isFalse(); // Partial match of regex
    }

    @Test
    void testRegexPattern()
    {
        List<String> complexStatementPaths = ImmutableList.of(V1_STATEMENT_PATH);
        List<String> complexRegexPaths = ImmutableList.of(
                "/api/v[1-9]/.*");
        PathFilter complexFilter = new PathFilter(complexStatementPaths, complexRegexPaths);

        // Test version pattern
        assertThat(complexFilter.isPathWhiteListed("/api/v1/users")).isTrue();
        assertThat(complexFilter.isPathWhiteListed("/api/v2/data")).isTrue();
        assertThat(complexFilter.isPathWhiteListed("/api/v9/reports")).isTrue();
        assertThat(complexFilter.isPathWhiteListed("/api/v0/test")).isFalse(); // v0 not in [1-9]
        assertThat(complexFilter.isPathWhiteListed("/api/v10/test")).isFalse(); // v10 not single digit
    }

    @Test
    void testEmptyConfiguration()
    {
        // Test PathFilter with empty lists
        PathFilter emptyFilter = new PathFilter(ImmutableList.of(), ImmutableList.of());

        // Should still allow hardcoded paths
        assertThat(emptyFilter.isPathWhiteListed(V1_QUERY_PATH)).isTrue();
        assertThat(emptyFilter.isPathWhiteListed(TRINO_UI_PATH)).isTrue();
        assertThat(emptyFilter.isPathWhiteListed(V1_INFO_PATH)).isTrue();
        assertThat(emptyFilter.isPathWhiteListed(OAUTH_PATH)).isTrue();

        // Should not allow any custom paths
        assertThat(emptyFilter.isPathWhiteListed(V1_STATEMENT_PATH)).isFalse();
        assertThat(emptyFilter.isPathWhiteListed("/custom/path")).isFalse();
    }

    @Test
    void testStatementPathsOnly()
    {
        // Test PathFilter with only statement paths, no regex patterns
        PathFilter statementOnlyFilter = new PathFilter(
                ImmutableList.of(V1_STATEMENT_PATH, "/v2/statement", "/custom/execute"),
                ImmutableList.of());

        // Should allow hardcoded paths
        assertThat(statementOnlyFilter.isPathWhiteListed(V1_QUERY_PATH)).isTrue();
        assertThat(statementOnlyFilter.isPathWhiteListed(TRINO_UI_PATH)).isTrue();

        // Should allow configured statement paths
        assertThat(statementOnlyFilter.isPathWhiteListed(V1_STATEMENT_PATH)).isTrue();
        assertThat(statementOnlyFilter.isPathWhiteListed("/v2/statement")).isTrue();
        assertThat(statementOnlyFilter.isPathWhiteListed("/custom/execute")).isTrue();
        assertThat(statementOnlyFilter.isPathWhiteListed("/custom/execute/batch")).isTrue();

        // Should not allow other paths
        assertThat(statementOnlyFilter.isPathWhiteListed("/api/custom")).isFalse();
        assertThat(statementOnlyFilter.isPathWhiteListed("/health")).isFalse();
    }

    @Test
    void testInvalidRegexFilter()
    {
        List<String> statementPaths = ImmutableList.of(V1_STATEMENT_PATH, "/v2/statement");
        List<String> extraWhitelistPaths = ImmutableList.of(
                "[/api/v1/custom/.*");
        assertThatThrownBy(() -> {
            PathFilter invalidRegex = new PathFilter(statementPaths, extraWhitelistPaths);
            invalidRegex.isPathWhiteListed("/api/v1/custom");
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefaultHaConfigurationForPaths()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        PathFilter filter = new PathFilter(configuration);

        assertThat(filter.isPathWhiteListed(V1_STATEMENT_PATH)).isTrue();
        assertThat(filter.isPathWhiteListed(V1_STATEMENT_PATH + "/executing")).isTrue();
        assertThat(filter.isPathWhiteListed(V1_STATEMENT_PATH + "/queued")).isTrue();
        assertThat(filter.isPathWhiteListed(V1_QUERY_PATH)).isTrue();
        assertThat(filter.isPathWhiteListed(TRINO_UI_PATH)).isTrue();
        assertThat(filter.isPathWhiteListed(OAUTH_PATH)).isTrue();

        assertThat(filter.isPathWhiteListed("/v2/statement")).isFalse();
    }

    @Test
    void testDefaultHaConfigurationNullPaths()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();

        assertThatThrownBy(() -> {
            new PathFilter(null,
                    configuration.getExtraWhitelistPaths());
        }).isInstanceOf(NullPointerException.class);
    }
}
