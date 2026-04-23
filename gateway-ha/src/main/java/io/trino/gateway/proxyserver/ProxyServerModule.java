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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.security.ClientCertificateJwtRequestAuthenticator;
import io.trino.gateway.ha.security.NoopProxyRequestAuthenticator;
import io.trino.gateway.ha.security.ProxyRequestAuthenticator;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class ProxyServerModule
        extends AbstractModule
{
    @Override
    protected void configure()
    {
        jaxrsBinder(binder()).bind(RouteToBackendResource.class);
        jaxrsBinder(binder()).bind(RouterPreMatchContainerRequestFilter.class);
        jaxrsBinder(binder()).bind(ProxyRequestHandler.class);
        httpClientBinder(binder()).bindHttpClient("proxy", ForProxy.class);
    }

    @Provides
    @Singleton
    static ProxyRequestAuthenticator createProxyRequestAuthenticator(HaGatewayConfiguration configuration)
    {
        if (configuration.getClientCertificateJwtAuthentication() == null) {
            return new NoopProxyRequestAuthenticator();
        }
        return new ClientCertificateJwtRequestAuthenticator(configuration);
    }
}
