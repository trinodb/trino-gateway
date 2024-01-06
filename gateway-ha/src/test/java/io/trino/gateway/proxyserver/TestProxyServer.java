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
package io.trino.gateway.proxyserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Random;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProxyServer
{
    private static int serverPort;
    private static MockWebServer backend;
    private static ProxyServer proxyServer;
    private static HttpClient httpClient = new JettyHttpClient(new HttpClientConfig());

    @Test
    public void testProxyServer()
            throws IOException
    {
        String mockResponseText = "Test1234";
        setProxyServer(mockResponseText);
        try {
            proxyServer.start();
            Request request = prepareGet()
                    .setUri(URI.create("http://localhost:" + serverPort))
                    .build();
            StringResponseHandler.StringResponse response = httpClient.execute(request, createStringResponseHandler());
            assertEquals(mockResponseText, response.getBody());
        }
        finally {
            proxyServer.close();
            backend.shutdown();
        }
    }

    @Test
    public void testCustomHeader()
            throws Exception
    {
        String mockResponseText = "CUSTOM HEADER TEST";
        setProxyServer(mockResponseText);
        try {
            proxyServer.start();
            Request request = prepareGet()
                    .setUri(URI.create("http://localhost:" + serverPort))
                    .setHeader("HEADER1", "FOO")
                    .setHeader("HEADER2", "BAR")
                    .build();
            StringResponseHandler.StringResponse response = httpClient.execute(request, createStringResponseHandler());

            assertEquals(mockResponseText, response.getBody());
            RecordedRequest recordedRequest = backend.takeRequest();
            assertEquals("FOO", recordedRequest.getHeader("HEADER1"));
            assertEquals("BAR", recordedRequest.getHeader("HEADER2"));
        }
        finally {
            proxyServer.close();
            backend.shutdown();
        }
    }

    @Test
    public void testLongHeader()
            throws Exception
    {
        String mockResponseText = "CUSTOM LONG HEADER TEST";
        setProxyServer(mockResponseText);
        String mockLongHeaderKey = "HEADER_LONG";
        int headerLength = 4040;
        String mockLongHeaderValue = "x".repeat(headerLength);
        try {
            proxyServer.start();
            Request request = prepareGet()
                    .setUri(URI.create("http://localhost:" + serverPort))
                    .setHeader(mockLongHeaderKey, mockLongHeaderValue)
                    .build();
            StringResponseHandler.StringResponse response = httpClient.execute(request, createStringResponseHandler());
            assertEquals(mockResponseText, response.getBody());
            RecordedRequest recordedRequest = backend.takeRequest();
            assertEquals(mockLongHeaderValue, recordedRequest.getHeader(mockLongHeaderKey));

            Request tooLargeHeaderRequest = prepareGet()
                    .setUri(URI.create("http://localhost:" + serverPort))
                    .setHeader(mockLongHeaderKey, "x".repeat(headerLength + 1))
                    .build();
            assertThatThrownBy(() -> httpClient.execute(tooLargeHeaderRequest, createStringResponseHandler()))
                    .hasMessage("Request header too large");
        }
        finally {
            proxyServer.close();
            backend.shutdown();
        }
    }

    private ProxyServerConfiguration buildConfig(String backendUrl, int localPort)
    {
        ProxyServerConfiguration config = new ProxyServerConfiguration();
        config.setName("MockBackend");
        config.setPrefix("/");
        config.setPreserveHost("true");
        config.setProxyTo(backendUrl);
        config.setLocalPort(localPort);
        config.setOutputBufferSize(32 * 1024);
        config.setResponseHeaderSize(8 * 1024);
        config.setRequestHeaderSize(8 * 1024);
        config.setRequestBufferSize(16 * 1024); // default 4 * 1024
        config.setResponseBufferSize(16 * 1024);
        return config;
    }

    private void setProxyServer(String mockResponseText)
            throws IOException
    {
        int backendPort = 30000 + new Random().nextInt(1000);

        backend = new MockWebServer();
        backend.enqueue(new MockResponse().setBody(mockResponseText));
        backend.play(backendPort);

        serverPort = backendPort + 1;
        ProxyServerConfiguration config = buildConfig(backend.getUrl("/").toString(), serverPort);
        proxyServer = new ProxyServer(config, new ProxyHandler());
    }
}
