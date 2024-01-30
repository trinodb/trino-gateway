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
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.Closeable;
import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;

public class ProxyServer
        implements Closeable
{
    private static final Logger log = Logger.get(ProxyServer.class);
    private final Server server;
    private final ProxyServletImpl proxy;
    private final ProxyHandler proxyHandler;
    private ServletContextHandler context;

    public ProxyServer(ProxyServerConfiguration config, ProxyHandler proxyHandler)
    {
        this(config, proxyHandler, new ProxyServletImpl());
    }

    public ProxyServer(ProxyServerConfiguration config, ProxyHandler proxyHandler,
            ProxyServletImpl proxy)
    {
        this.server = new Server();
        this.server.setStopAtShutdown(true);
        this.proxy = proxy;
        this.proxyHandler = proxyHandler;

        this.proxy.setServerConfig(config);
        this.setupContext(config);
    }

    private void setupContext(ProxyServerConfiguration config)
    {
        ServerConnector connector;
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setOutputBufferSize(config.getOutputBufferSize());
        httpConfiguration.setRequestHeaderSize(config.getRequestHeaderSize());
        httpConfiguration.setResponseHeaderSize(config.getResponseHeaderSize());

        if (config.isSsl()) {
            String keystorePath = config.getKeystorePath();
            String keystorePass = config.getKeystorePass();
            File keystoreFile = new File(keystorePath);

            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setTrustAll(true);
            sslContextFactory.setSslSessionTimeout((int) TimeUnit.SECONDS.toMillis(15));

            if (!isNullOrEmpty(keystorePath)) {
                sslContextFactory.setKeyStorePath(keystoreFile.getAbsolutePath());
                sslContextFactory.setKeyStorePassword(keystorePass);
                sslContextFactory.setKeyManagerPassword(keystorePass);
            }

            httpConfiguration.setSecureScheme(HttpScheme.HTTPS.asString());
            httpConfiguration.setSecurePort(config.getLocalPort());

            SecureRequestCustomizer src = new SecureRequestCustomizer();
            src.setStsMaxAge(TimeUnit.SECONDS.toSeconds(2000));
            src.setStsIncludeSubDomains(true);
            httpConfiguration.addCustomizer(src);

            HttpConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfiguration);
            connector = new ServerConnector(
                    server,
                    new SslConnectionFactory(sslContextFactory, connectionFactory.getProtocol()),
                    connectionFactory);
        }
        else {
            connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        }
        connector.setHost("0.0.0.0");
        connector.setPort(config.getLocalPort());
        connector.setName(config.getName());
        connector.setAccepting(true);
        this.server.addConnector(connector);

        // Setup proxy handler to handle CONNECT methods
        ConnectHandler proxyConnectHandler = new ConnectHandler();
        this.server.setHandler(proxyConnectHandler);

        if (proxyHandler != null) {
            proxy.setProxyHandler(proxyHandler);
        }

        ServletHolder proxyServlet = new ServletHolder(config.getName(), proxy);

        proxyServlet.setInitParameter("proxyTo", config.getProxyTo());
        proxyServlet.setInitParameter("prefix", config.getPrefix());
        proxyServlet.setInitParameter("trustAll", config.getTrustAll());
        proxyServlet.setInitParameter("preserveHost", config.getPreserveHost());

        // Setup proxy servlet
        this.context =
                new ServletContextHandler(proxyConnectHandler, "/", ServletContextHandler.SESSIONS);
        this.context.addServlet(proxyServlet, "/*");
        this.context.addFilter(RequestFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
    }

    public void start()
    {
        try {
            this.server.start();
        }
        catch (Exception e) {
            log.error(e, "Error starting proxy server");
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close()
    {
        try {
            this.server.stop();
        }
        catch (Exception e) {
            log.error(e, "Could not close the proxy server");
        }
    }
}
