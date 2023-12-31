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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(Lifecycle.PER_CLASS)
public class TestQueryIdCachingProxyHandler
{
    @Test
    public void testExtractQueryIdFromUrl()
    {
        String[] paths = {
                "/ui/api/query/20200416_160256_03078_6b4yt",
                "/ui/api/query/20200416_160256_03078_6b4yt?bogus_fictional_param",
                "/ui/api/query?query_id=20200416_160256_03078_6b4yt",
                "/ui/api/query.html?20200416_160256_03078_6b4yt"};
        for (String path : paths) {
            String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
            assertEquals("20200416_160256_03078_6b4yt", queryId);
        }
        String[] nonPaths = {
                "/ui/api/query/myOtherThing",
                "/ui/api/query/20200416_blah?bogus_fictional_param"};
        for (String path : nonPaths) {
            String queryId = QueryIdCachingProxyHandler.extractQueryIdIfPresent(path, null);
            assertNull(queryId);
        }
    }

    @Test
    public void testForwardedHostHeaderOnProxyRequest()
    {
        String backendServer = "trinocluster";
        String backendPort = "80";
        HttpServletRequest mockServletRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockServletRequest.getHeader("proxytarget")).thenReturn(String.format("http://%s"
                + ":%s", backendServer, backendPort));
        HttpClient httpClient = new HttpClient();
        Request proxyRequest = httpClient.newRequest("http://localhost:80");
        QueryIdCachingProxyHandler.setForwardedHostHeaderOnProxyRequest(mockServletRequest,
                proxyRequest);
        assertEquals(String.format("%s:%s",
                backendServer, backendPort), proxyRequest.getHeaders().get("Host"));
    }

    @Test
    public void testUserFromRequest()
    {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);

        String authHeader = "Basic dGVzdDoxMjPCow==";
        Mockito.when(req.getHeader(QueryIdCachingProxyHandler.AUTHORIZATION))
                .thenReturn(authHeader);
        assertEquals("test", QueryIdCachingProxyHandler.getQueryUser(req));

        String user = "trino_user";
        Mockito.when(req.getHeader(QueryIdCachingProxyHandler.USER_HEADER))
                .thenReturn(user);
        assertEquals(user, QueryIdCachingProxyHandler.getQueryUser(req));
    }
}
