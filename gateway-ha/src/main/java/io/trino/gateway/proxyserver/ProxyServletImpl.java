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

import io.airlift.log.Logger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.concurrent.TimeUnit;

public class ProxyServletImpl
        extends ProxyServlet.Transparent
{
    private static final Logger log = Logger.get(ProxyServletImpl.class);
    private ProxyHandler proxyHandler;
    private ProxyServerConfiguration serverConfig;

    public void setProxyHandler(ProxyHandler proxyHandler)
    {
        this.proxyHandler = proxyHandler;
        // This needs to be high as external clients may take longer to connect.
        this.setTimeout(TimeUnit.MINUTES.toMillis(1));
    }

    public void setServerConfig(ProxyServerConfiguration config)
    {
        this.serverConfig = config;
    }

    // Overriding this method to support ssl
    @Override
    protected HttpClient newHttpClient()
    {
        SslContextFactory.Client sslFactory = new SslContextFactory.Client();

        if (serverConfig != null && serverConfig.isForwardKeystore()) {
            sslFactory.setKeyStorePath(serverConfig.getKeystorePath());
            sslFactory.setKeyStorePassword(serverConfig.getKeystorePass());
        }
        else {
            sslFactory.setTrustAll(true);
        }
        sslFactory.setSslSessionTimeout((int) TimeUnit.SECONDS.toMillis(15));

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslFactory);

        HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
        httpClient.setMaxConnectionsPerDestination(10000);
        httpClient.setConnectTimeout(TimeUnit.SECONDS.toMillis(60));
        httpClient.setRequestBufferSize(serverConfig.getRequestBufferSize());
        httpClient.setResponseBufferSize(serverConfig.getResponseBufferSize());
        return httpClient;
    }

    /**
     * Customize the headers of forwarding proxy requests.
     */
    @Override
    protected void addProxyHeaders(HttpServletRequest request, Request proxyRequest)
    {
        super.addProxyHeaders(request, proxyRequest);
        if (proxyHandler != null) {
            proxyHandler.preConnectionHook(request, proxyRequest);
        }
    }

    @Override
    protected String rewriteTarget(HttpServletRequest request)
    {
        String target = null;
        if (proxyHandler != null) {
            target = proxyHandler.rewriteTarget(request);
        }
        if (target == null) {
            target = super.rewriteTarget(request);
        }
        log.debug("Target : " + target);
        return target;
    }

    @Override
    protected void onServerResponseHeaders(
            HttpServletRequest clientRequest,
            HttpServletResponse proxyResponse,
            Response serverResponse)
    {
        this.proxyHandler.generateDeleteCookieList(clientRequest).forEach(proxyResponse::addCookie);
        super.onServerResponseHeaders(clientRequest, proxyResponse, serverResponse);
    }

    /**
     * Customize the response returned from remote server.
     */
    @Override
    protected void onResponseContent(
            HttpServletRequest request,
            HttpServletResponse response,
            Response proxyResponse,
            byte[] buffer,
            int offset,
            int length,
            Callback callback)
    {
        try {
            log.debug("[%d] proxying content to downstream: [%d] bytes", this.getRequestId(request), length);
            if (this.proxyHandler != null) {
                proxyHandler.postConnectionHook(request, response, buffer, offset, length, callback);
            }
            else {
                super.onResponseContent(request, response, proxyResponse, buffer, offset, length, callback);
            }
        }
        catch (Throwable var9) {
            callback.failed(var9);
        }
    }
}
