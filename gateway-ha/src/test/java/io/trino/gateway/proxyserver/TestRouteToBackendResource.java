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

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HttpClient;
import io.trino.gateway.ha.config.ClientCertificateJwtAuthenticationConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfiguration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.handler.ProxyHandlerStats;
import io.trino.gateway.ha.handler.RoutingTargetHandler;
import io.trino.gateway.ha.handler.schema.RoutingDestination;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.security.ClientCertificateIdentityExtractor;
import io.trino.gateway.ha.security.ClientCertificateJwtRequestAuthenticator;
import io.trino.gateway.ha.security.LbKeyProvider;
import io.trino.gateway.ha.security.LbTokenUtil;
import io.trino.gateway.ha.security.NoopProxyRequestAuthenticator;
import io.trino.gateway.ha.security.ProxyRequestAuthenticator;
import io.trino.gateway.ha.testing.TestingHttpServletRequest;
import io.trino.gateway.ha.testing.TestingX509Certificate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.TimeoutHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.trino.gateway.ha.handler.HttpUtils.USER_HEADER;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;

final class TestRouteToBackendResource
{
    private static final SelfSignKeyPairConfiguration TEST_KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_private_key.pem",
            "src/test/resources/auth/test_public_key.pem");

    @Test
    void testPostHandlerDecoratesRoutedQueryRequestWithJwt()
    {
        initializeCookieConfiguration();
        ProxyHandlerStats proxyHandlerStats = new ProxyHandlerStats();
        RecordingProxyRequestHandler proxyRequestHandler = new RecordingProxyRequestHandler();
        RoutingDestination destination = new RoutingDestination("adhoc", "cluster-a", URI.create("https://cluster-a.example/v1/statement"), "https://cluster-a.example");
        FixedRoutingTargetHandler routingTargetHandler = new FixedRoutingTargetHandler(destination);
        AsyncResponse asyncResponse = new TestingAsyncResponse();

        ProxyRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuredBridge());
        RouteToBackendResource resource = new RouteToBackendResource(
                proxyHandlerStats,
                proxyRequestHandler,
                routingTargetHandler,
                authenticator);

        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.addHeader(USER_HEADER, "bob");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        resource.postHandler("SELECT 1", request, asyncResponse);

        assertThat(proxyHandlerStats.getRequestCount().getTotalCount()).isEqualTo(1);
        assertThat(proxyRequestHandler.recordedBody).isEqualTo("SELECT 1");
        assertThat(proxyRequestHandler.recordedAsyncResponse).isSameAs(asyncResponse);
        assertThat(proxyRequestHandler.recordedDestination).isEqualTo(destination);
        assertThat(proxyRequestHandler.recordedRequest.getHeader(USER_HEADER)).isEqualTo("bob");
        assertThat(proxyRequestHandler.recordedRequest.getHeader("Authorization")).startsWith("Bearer ");

        String token = proxyRequestHandler.recordedRequest.getHeader("Authorization").substring("Bearer ".length());
        LbKeyProvider keyProvider = new LbKeyProvider(TEST_KEY_PAIR);
        assertThat(LbTokenUtil.validateToken(token, keyProvider.publicKey(), "trino-gateway-tests", Optional.of(ImmutableList.of("trino")))).isTrue();

        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaim("preferred_username").asString()).isEqualTo("alice");
    }

    @Test
    void testPostHandlerUsesNoopAuthenticatorWithoutBridge()
    {
        initializeCookieConfiguration();
        ProxyHandlerStats proxyHandlerStats = new ProxyHandlerStats();
        RecordingProxyRequestHandler proxyRequestHandler = new RecordingProxyRequestHandler();
        RoutingDestination destination = new RoutingDestination("adhoc", "cluster-a", URI.create("https://cluster-a.example/v1/statement"), "https://cluster-a.example");
        FixedRoutingTargetHandler routingTargetHandler = new FixedRoutingTargetHandler(destination);
        AsyncResponse asyncResponse = new TestingAsyncResponse();

        RouteToBackendResource resource = new RouteToBackendResource(
                proxyHandlerStats,
                proxyRequestHandler,
                routingTargetHandler,
                new NoopProxyRequestAuthenticator());

        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.addHeader(USER_HEADER, "bob");

        resource.postHandler("SELECT 1", request, asyncResponse);

        assertThat(proxyRequestHandler.recordedRequest.getHeader("Authorization")).isNull();
        assertThat(proxyRequestHandler.recordedRequest.getHeader(USER_HEADER)).isEqualTo("bob");
    }

    @Test
    void testProxyServerModuleSelectsNoopAuthenticatorWithoutBridge()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setRequestAnalyzerConfig(new RequestAnalyzerConfig());

        assertThat(ProxyServerModule.createProxyRequestAuthenticator(configuration))
                .isInstanceOf(NoopProxyRequestAuthenticator.class);
    }

    @Test
    void testProxyServerModuleSelectsJwtAuthenticatorWhenBridgeConfigured()
    {
        assertThat(ProxyServerModule.createProxyRequestAuthenticator(configuredBridge()))
                .isInstanceOf(ClientCertificateJwtRequestAuthenticator.class);
    }

    private static HaGatewayConfiguration configuredBridge()
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = new ClientCertificateJwtAuthenticationConfiguration();
        authConfig.setJwtSigningKeyPair(TEST_KEY_PAIR);
        authConfig.setJwtIssuer("trino-gateway-tests");
        authConfig.setJwtAudiences(ImmutableList.of("trino"));
        authConfig.setJwtPrincipalClaim("preferred_username");

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setClientCertificateIdentityField("CN");

        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setRequestAnalyzerConfig(requestAnalyzerConfig);
        configuration.setClientCertificateJwtAuthentication(authConfig);
        return configuration;
    }

    private static X509Certificate certificate(String dn)
    {
        return new TestingX509Certificate(dn);
    }

    private static void initializeCookieConfiguration()
    {
        GatewayCookieConfiguration cookieConfiguration = new GatewayCookieConfiguration();
        cookieConfiguration.setEnabled(false);
        GatewayCookieConfigurationPropertiesProvider.getInstance().initialize(cookieConfiguration);
    }

    @SuppressWarnings("unchecked")
    private static <T> T unsupportedInterface(Class<T> type)
    {
        return (T) Proxy.newProxyInstance(
                TestRouteToBackendResource.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unexpected primitive type: " + type);
    }

    private static final class RecordingProxyRequestHandler
            extends ProxyRequestHandler
    {
        private String recordedBody;
        private HttpServletRequest recordedRequest;
        private AsyncResponse recordedAsyncResponse;
        private RoutingDestination recordedDestination;

        private RecordingProxyRequestHandler()
        {
            super(
                    unsupportedInterface(HttpClient.class),
                    unsupportedInterface(RoutingManager.class),
                    unsupportedInterface(QueryHistoryManager.class),
                    new HaGatewayConfiguration());
        }

        @Override
        public void postRequest(String statement, HttpServletRequest servletRequest, AsyncResponse asyncResponse, RoutingDestination routingDestination)
        {
            recordedBody = statement;
            recordedRequest = servletRequest;
            recordedAsyncResponse = asyncResponse;
            recordedDestination = routingDestination;
        }
    }

    private static final class FixedRoutingTargetHandler
            extends RoutingTargetHandler
    {
        private final RoutingDestination destination;

        private FixedRoutingTargetHandler(RoutingDestination destination)
        {
            super(
                    unsupportedInterface(RoutingManager.class),
                    (RoutingGroupSelector) request -> null,
                    new HaGatewayConfiguration());
            this.destination = destination;
        }

        @Override
        public RoutingTargetResponse resolveRouting(HttpServletRequest request)
        {
            return new RoutingTargetResponse(destination, request);
        }
    }

    private static final class TestingAsyncResponse
            implements AsyncResponse
    {
        @Override
        public boolean resume(Object response)
        {
            return true;
        }

        @Override
        public boolean resume(Throwable response)
        {
            return true;
        }

        @Override
        public boolean cancel()
        {
            return false;
        }

        @Override
        public boolean cancel(int retryAfter)
        {
            return false;
        }

        @Override
        public boolean cancel(Date retryAfter)
        {
            return false;
        }

        @Override
        public boolean isSuspended()
        {
            return true;
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return false;
        }

        @Override
        public boolean setTimeout(long time, TimeUnit unit)
        {
            return true;
        }

        @Override
        public void setTimeoutHandler(TimeoutHandler handler)
        {
        }

        @Override
        public Collection<Class<?>> register(Class<?> callback)
        {
            return ImmutableList.of();
        }

        @Override
        public Map<Class<?>, Collection<Class<?>>> register(Class<?> callback, Class<?>... callbacks)
        {
            return ImmutableMap.of();
        }

        @Override
        public Collection<Class<?>> register(Object callback)
        {
            return ImmutableList.of();
        }

        @Override
        public Map<Class<?>, Collection<Class<?>>> register(Object callback, Object... callbacks)
        {
            return ImmutableMap.of();
        }
    }
}
