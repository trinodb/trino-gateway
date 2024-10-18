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

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.IOException;
import java.util.List;

import static io.trino.gateway.ha.handler.ProxyUtils.extractQueryIdIfPresent;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestQueryIdCachingProxyHandler
{
    @Test
    void testExtractQueryIdFromUrl()
            throws IOException
    {
        List<String> statementPaths = ImmutableList.of("/v1/statement", "/custom/api/statement");
        assertThat(extractQueryIdIfPresent("/v1/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/custom/api/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/queued/20200416_160256_03078_6b4yt/y0d7620a6941e78d3950798a1085383234258a566/1", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/killed", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/preempted", null, statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/query/20200416_160256_03078_6b4yt", "pretty", statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/troubleshooting", "queryId=20200416_160256_03078_6b4yt", statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/query.html", "20200416_160256_03078_6b4yt", statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/login", "redirect=%2Fui%2Fapi%2Fquery%2F20200416_160256_03078_6b4yt", statementPaths))
                .isEqualTo("20200416_160256_03078_6b4yt");

        assertThat(extractQueryIdIfPresent("/ui/api/query/myOtherThing", null, statementPaths))
                .isNull();
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_blah", "bogus_fictional_param", statementPaths))
                .isNull();
        assertThat(extractQueryIdIfPresent("/ui/", "lang=en&p=1&id=0_1_2_a", statementPaths))
                .isNull();
    }
}
