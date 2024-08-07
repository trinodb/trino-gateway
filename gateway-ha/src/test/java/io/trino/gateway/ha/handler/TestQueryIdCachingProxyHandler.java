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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.trino.gateway.ha.handler.ProxyUtils.extractQueryIdIfPresent;
import static io.trino.gateway.ha.handler.ProxyUtils.getQueryUser;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.AUTHORIZATION;
import static io.trino.gateway.ha.handler.QueryIdCachingProxyHandler.USER_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler
{
    @Test
    public void testExtractQueryIdFromUrl()
            throws IOException
    {
        assertThat(extractQueryIdIfPresent("/v1/statement/executing/20200416_160256_03078_6b4yt/ya7e884929c67cdf86207a80e7a77ab2166fa2e7b/1368", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/statement/queued/20200416_160256_03078_6b4yt/y0d7620a6941e78d3950798a1085383234258a566/1", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/killed", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_160256_03078_6b4yt/preempted", null))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/v1/query/20200416_160256_03078_6b4yt", "pretty"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/troubleshooting", "queryId=20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/ui/query.html", "20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");
        assertThat(extractQueryIdIfPresent("/login", "redirect=%2Fui%2Fapi%2Fquery%2F20200416_160256_03078_6b4yt"))
                .isEqualTo("20200416_160256_03078_6b4yt");

        assertThat(extractQueryIdIfPresent("/ui/api/query/myOtherThing", null))
                .isNull();
        assertThat(extractQueryIdIfPresent("/ui/api/query/20200416_blah", "bogus_fictional_param"))
                .isNull();
        assertThat(extractQueryIdIfPresent("/ui/", "lang=en&p=1&id=0_1_2_a"))
                .isNull();
    }

    @Test
    public void testQueryIdFromKill()
            throws IOException
    {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

        String query = "CALL system.runtime.kill_query(query_id ==> '20200416_160256_03078_6b4yt', message ==> 'If he dies, he dies')";
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
        when(req.getInputStream()).thenReturn(new ServletInputStream()
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

            public int read()
                    throws IOException
            {
                return byteArrayInputStream.read();
            }
        });

        when(req.getQueryString()).thenReturn("");

        assertThat(extractQueryIdIfPresent(req)).isEqualTo("20200416_160256_03078_6b4yt");
    }

    @Test
    public void testUserFromRequest()
            throws IOException
    {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

        String authHeader = "Basic dGVzdDoxMjPCow==";
        Mockito.when(req.getHeader(AUTHORIZATION))
                .thenReturn(authHeader);
        assertThat(getQueryUser(req.getHeader(USER_HEADER), req.getHeader(AUTHORIZATION))).isEqualTo("test");

        String user = "trino_user";
        Mockito.when(req.getHeader(QueryIdCachingProxyHandler.USER_HEADER))
                .thenReturn(user);
        assertThat(getQueryUser(req.getHeader(USER_HEADER), req.getHeader(AUTHORIZATION))).isEqualTo(user);
    }
}
