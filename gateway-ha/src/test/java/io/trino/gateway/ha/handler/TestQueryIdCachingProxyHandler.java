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
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static io.trino.gateway.ha.handler.ProxyUtils.extractQueryIdIfPresent;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestQueryIdCachingProxyHandler
{
    @Test
    void testExtractQueryIdFromUrl()
            throws IOException
    {
        List<String> statementPaths = ImmutableList.of("/v1/statement", "/custom/api/statement");
        assertThat(extractQueryIdIfPresent("/v1/statement/queued/20200416_160256_03078_6b4yt/ye6c54db413e65c5de0e99612ab1eaabb8611a8aa/1", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/scheduled/20200416_160256_03078_6b4yt/ye6c54db413e65c5de0e99612ab1eaabb8611a8aa/1", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/executing/partialCancel/20200416_160256_03078_6b4yt/0/yce0e0e038758e454d22d7270de30395e19a28eb6/1", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/custom/api/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/queued/20200416_160256_03078_6b4yt/y0d7620a6941e78d3950798a1085383234258a566/1", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/killed", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/preempted", null, statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/query/20200416_160256_03078_6b4yt", "pretty", statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/troubleshooting", "queryId=20200416_160256_03078_6b4yt", statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/query.html", "20200416_160256_03078_6b4yt", statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/login", "redirect=%2Fui%2Fapi%2Fquery%2F20200416_160256_03078_6b4yt", statementPaths))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryIdIfPresent("/ui/api/query/myOtherThing", null, statementPaths))
                .isEmpty();
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_blah", "bogus_fictional_param", statementPaths))
                .isEmpty();
        assertThat(extractQueryIdIfPresent("/ui/", "lang=en&p=1&id=0_1_2_a", statementPaths))
                .isEmpty();
    }

    @Test
    void testQueryIdFromKill()
            throws IOException
    {
        assertThat(extractQueryId(request("CALL system.runtime.kill_query(query_id => '20200416_160256_03078_6b4yt', message => 'If he dies, he dies')")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL system.runtime.kill_query(Query_id => '20200416_160256_03078_6b4yt', Message => 'If he dies, he dies')")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL kill_query('20200416_160256_03078_6b4yt', 'If he dies, he dies')", "system", "runtime")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL runtime.kill_query('20200416_160256_03078_6b4yt', '20200416_160256_03078_7n5uy')", "system")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL system.runtime.kill_query('20200416_160256_03078_6b4yt', 'kill_query(''20200416_160256_03078_7n5uy'')')")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL system.runtime.kill_query('20200416_160256_03078_6b4yt', '20200416_160256_03078_7n5uy')")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL system.runtime.kill_query(query_id=>'20200416_160256_03078_6b4yt')"))).hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL system.runtime.kill_query('20200416_160256_03078_6b4yt')"))).hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("CALL kill_query('20200416_160256_03078_6b4yt')", "system", "runtime")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("call Kill_Query('20200416_160256_03078_6b4yt')", "system", "runtime")))
                .hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request(
                "SELECT * FROM postgres.query_logs.queries WHERE sql LIKE  '%kill_query(''20200416_160256%' ",
                "system",
                "runtime"))).isEmpty();

        assertThat(extractQueryId(request(
                "SELECT * FROM postgres.query_logs.queries WHERE sql LIKE  '%kill_query(''20200416_160256_03078_6b4yt' ",
                "system",
                "runtime"))).isEmpty();

        assertThat(extractQueryId(request(
                "SELECT * FROM postgres.query_logs.queries WHERE sql LIKE 'CALL kill_query(_20200416_160256_03078_6b4yt_)' ",
                "system",
                "runtime"))).isEmpty();

        assertThat(extractQueryId(request("""
                        --CALL kill_query('20200416_160256_03078_6b4yt', 'If he dies, he dies')
                        SELECT 1
                        """,
                "system",
                "runtime"))).isEmpty();

        assertThat(extractQueryId(request("""
                        /*
                        CALL kill_query('20200416_160256_03078_6b4yt', 'If he dies, he dies')
                        */
                        SELECT 1
                        """,
                "system",
                "runtime"))).isEmpty();

        assertThat(extractQueryId(request("""
                        CALL KILL_QUERY('20200416_160256_03078_6b4yt', 'If he dies, he dies')
                        """,
                "system",
                "runtime"))).hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("""
                        CALL KILL_QUERY ('20200416_160256_03078_6b4yt', 'If he dies, he dies')
                        """,
                "system",
                "runtime"))).hasValue("20200416_160256_03078_6b4yt");

        assertThat(extractQueryId(request("""
                        CALL
                        KILL_QUERY
                        (
                        -- this is a comment
                        '20200416_160256_03078_6b4yt' --this is a trailing comment
                        ,
                        /*
                        this is
                        a multiline comment
                        */
                        'If he dies, he dies
                        ')
                        """,
                "system",
                "runtime"))).hasValue("20200416_160256_03078_6b4yt");

        assertThatThrownBy(() -> extractQueryId(request("""
                        CALL KILL_QUERY (lower('20200416_160256_03078_6b4yt'), 'If he dies, he dies')
                        """,
                "system",
                "runtime"))).isInstanceOf(IllegalArgumentException.class);

        assertThat(extractQueryId(request("CALL notsystem.runtime.kill_query(query_id => '20200416_160256_03078_6b4yt', message => 'If he dies, he dies')"))).isEmpty();

        assertThat(extractQueryId(request("CALL runtime.kill_query(query_id => '20200416_160256_03078_6b4yt', message => 'If he dies, he dies')", "notsystem")))
                .isEmpty();

        assertThat(extractQueryId(request("CALL notruntime.kill_query(query_id => '20200416_160256_03078_6b4yt', message => 'If he dies, he dies')", "system")))
                .isEmpty();

        assertThat(extractQueryId(request(
                "CALL kill_query(query_id => '20200416_160256_03078_6b4yt', message => 'If he dies, he dies')",
                "system",
                "notruntime")))
                .isEmpty();
    }

    private static Optional<String> extractQueryId(HttpServletRequest request)
    {
        return extractQueryIdIfPresent(request, ImmutableList.of(), false, 1_000_000);
    }

    private static HttpServletRequest request(String query, String defaultCatalog)
            throws IOException
    {
        // Warning - this is not a fully featured mock of the behavior of HttpServlet with respect to headers. For example,
        // getHeaderNames will return an empty list, and getHeader is not fully case-insensitive. This is only intended to be
        // a minimal mock for this test.
        HttpServletRequest request = request(query);
        when(request.getHeader("X-Trino-Catalog")).thenReturn(defaultCatalog);
        when(request.getHeader("X-trino-catalog")).thenReturn(defaultCatalog);
        return request;
    }

    private static HttpServletRequest request(String query, String defaultCatalog, String defaultSchema)
            throws IOException
    {
        HttpServletRequest request = request(query);
        when(request.getHeader("X-Trino-Catalog")).thenReturn(defaultCatalog);
        when(request.getHeader("X-trino-catalog")).thenReturn(defaultCatalog);
        when(request.getHeader("X-Trino-Schema")).thenReturn(defaultSchema);
        when(request.getHeader("X-trino-schema")).thenReturn(defaultSchema);
        return request;
    }

    private static HttpServletRequest request(String query)
            throws IOException
    {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(query.getBytes(UTF_8));
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getInputStream()).thenReturn(new ServletInputStream()
        {
            @Override
            public boolean isFinished()
            {
                return byteArrayInputStream.available() > 0;
            }

            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener)
            {}

            @Override
            public int read()
                    throws IOException
            {
                return byteArrayInputStream.read();
            }
        });

        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(query)));

        when(request.getQueryString()).thenReturn("");

        return request;
    }
}
