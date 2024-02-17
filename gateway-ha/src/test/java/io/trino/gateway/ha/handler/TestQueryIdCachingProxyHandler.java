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

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;

import java.io.IOException;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler
{
    @Test
    public void testExtractQueryIdFromUrl()
            throws IOException
    {
        String[] paths = {
                "/ui/api/query/20200416_160256_03078_6b4yt",
                "/ui/api/query/20200416_160256_03078_6b4yt?bogus_fictional_param",
                "/ui/api/query?query_id=20200416_160256_03078_6b4yt",
                "/ui/api/query.html?20200416_160256_03078_6b4yt"};
        for (String path : paths) {
            String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
            assertThat(queryId).isEqualTo("20200416_160256_03078_6b4yt");
        }
        String[] nonPaths = {
                "/ui/api/query/myOtherThing",
                "/ui/api/query/20200416_blah?bogus_fictional_param"};
        for (String path : nonPaths) {
            String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
            assertThat(queryId).isNull();
        }
    }

    @Test
    public void testForwardedHostHeaderOnProxyRequest()
            throws IOException
    {
        String backendServer = "trinocluster";
        String backendPort = "80";
        HttpServletRequest mockServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockServletRequest.getHeader("proxytarget")).thenReturn(format("http://%s:%s", backendServer, backendPort));
        HttpClient httpClient = new HttpClient();
        Request proxyRequest = httpClient.newRequest("http://localhost:80");
        QueryIdCachingProxyHandler.setForwardedHostHeaderOnProxyRequest(mockServletRequest,
                proxyRequest);
        assertThat(proxyRequest.getHeaders().get("Host"))
                .isEqualTo(format("%s:%s", backendServer, backendPort));
    }

    @Test
    public void testUserFromRequest()
            throws IOException
    {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

        String authHeader = "Basic dGVzdDoxMjPCow==";
        Mockito.when(req.getHeader(QueryIdCachingProxyHandler.AUTHORIZATION))
                .thenReturn(authHeader);
        assertThat(QueryIdCachingProxyHandler.getQueryUser(req)).isEqualTo("test");

        String user = "trino_user";
        Mockito.when(req.getHeader(QueryIdCachingProxyHandler.USER_HEADER))
                .thenReturn(user);
        assertThat(QueryIdCachingProxyHandler.getQueryUser(req)).isEqualTo(user);
    }
}
