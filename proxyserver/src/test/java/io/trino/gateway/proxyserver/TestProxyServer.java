package io.trino.gateway.proxyserver;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProxyServer
{
    private static int serverPort;
    private static MockWebServer backend;
    private static ProxyServer proxyServer;

    @Test
    public void testProxyServer()
            throws IOException
    {
        String mockResponseText = "Test1234";
        setProxyServer(mockResponseText);
        try {
            proxyServer.start();
            CloseableHttpClient httpclient = HttpClientBuilder.create().build();
            HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);
            HttpResponse response = httpclient.execute(httpUriRequest);
            assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
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
            CloseableHttpClient httpclient = HttpClientBuilder.create().build();
            HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);
            httpUriRequest.setHeader("HEADER1", "FOO");
            httpUriRequest.setHeader("HEADER2", "BAR");

            HttpResponse response = httpclient.execute(httpUriRequest);
            assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
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
        // Mockserver has max 8k for HTTP Header values so test with header value larger than default 4k
        int headerLength = 5 * 1024;
        String mockLongHeaderValue = "x".repeat(headerLength);
        try {
            proxyServer.start();
            CloseableHttpClient httpclient = HttpClientBuilder.create().build();
            HttpUriRequest httpUriRequest = new HttpGet("http://localhost:" + serverPort);
            httpUriRequest.setHeader(mockLongHeaderKey, mockLongHeaderValue);

            HttpResponse response = httpclient.execute(httpUriRequest);
            assertEquals(mockResponseText, EntityUtils.toString(response.getEntity()));
            RecordedRequest recordedRequest = backend.takeRequest();
            assertEquals(mockLongHeaderValue, recordedRequest.getHeader(mockLongHeaderKey));
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
