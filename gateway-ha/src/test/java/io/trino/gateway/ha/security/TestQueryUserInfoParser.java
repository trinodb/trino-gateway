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
package io.trino.gateway.ha.security;

import io.trino.gateway.ha.config.ClientCertificateJwtAuthenticationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.handler.HttpUtils;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.TrinoRequestUser;
import io.trino.gateway.ha.testing.TestingHttpServletRequest;
import io.trino.gateway.ha.testing.TestingX509Certificate;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestQueryUserInfoParser
{
    private static final SelfSignKeyPairConfiguration TEST_KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_private_key.pem",
            "src/test/resources/auth/test_public_key.pem");

    @Test
    void testFilterSetsQueryUserInfo()
            throws Exception
    {
        QueryUserInfoParser filter = createFilter(false);

        ContainerRequestContext requestContext = mock(ContainerRequest.class);
        when(requestContext.getMethod()).thenReturn("POST");

        UriInfo uriInfo = mock(ExtendedUriInfo.class);
        when(uriInfo.getRequestUri()).thenReturn(new URI("http://localhost" + V1_STATEMENT_PATH));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        MediaType mediaType = new MediaType("application", "json", java.util.Map.of("charset", "UTF-8"));
        when(requestContext.getMediaType()).thenReturn(mediaType);

        String encodedUsernamePassword = Base64.getEncoder().encodeToString("MrXYZ:OutInTheOpen".getBytes(UTF_8));
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic " + encodedUsernamePassword);

        filter.filter(requestContext);

        ArgumentCaptor<TrinoRequestUser> userCaptor = ArgumentCaptor.forClass(TrinoRequestUser.class);
        verify(requestContext).setProperty(eq(HttpUtils.TRINO_REQUEST_USER), userCaptor.capture());
    }

    @Test
    void testFilterPrefersClientCertificateOverAuthorization()
            throws Exception
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        requestAnalyzerConfig.setClientCertificateUserMappingPattern("(.*)@example\\.com");
        QueryUserInfoParser filter = createFilter(requestAnalyzerConfig, true);
        ContainerRequest requestContext = newRequestContext();
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Basic " + base64("MrXYZ:OutInTheOpen"));

        TestingHttpServletRequest servletRequest = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        servletRequest.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice@example.com, OU=data, O=example")});

        filter.filter(requestContext, servletRequest);

        TrinoRequestUser user = (TrinoRequestUser) requestContext.getProperty(TRINO_REQUEST_USER);
        assertThat(servletRequest.getAttribute(ClientCertificateUserResolver.MAPPED_USER_ATTRIBUTE)).isEqualTo("alice");
        assertThat(user.getUser()).hasValue("alice");
    }

    @Test
    void testFilterRejectsRequestWhenClientCertificateMappingFails()
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        requestAnalyzerConfig.setClientCertificateUserMappingPattern("(.*)@example\\.com");
        QueryUserInfoParser filter = createFilter(requestAnalyzerConfig, true);
        ContainerRequest requestContext = newRequestContext();

        TestingHttpServletRequest servletRequest = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        servletRequest.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        assertThat(catchThrowable(() -> filter.filter(requestContext, servletRequest)))
                .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void testFilterPrefersUserHeaderOverClientCertificate()
            throws Exception
    {
        QueryUserInfoParser filter = createFilter(false);
        ContainerRequest requestContext = newRequestContext();
        requestContext.getHeaders().putSingle(HttpUtils.USER_HEADER, "bob");

        TestingHttpServletRequest servletRequest = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        servletRequest.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        filter.filter(requestContext, servletRequest);

        TrinoRequestUser user = (TrinoRequestUser) requestContext.getProperty(TRINO_REQUEST_USER);
        assertThat(user.getUser()).hasValue("bob");
    }

    private static X509Certificate certificate(String dn)
    {
        return new TestingX509Certificate(dn);
    }

    private static String base64(String value)
    {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }

    private static ContainerRequest newRequestContext()
    {
        try {
            Class<?> propertiesDelegateClass = Class.forName("org.glassfish.jersey.internal.PropertiesDelegate");
            Object propertiesDelegate = Class.forName("org.glassfish.jersey.internal.MapPropertiesDelegate")
                    .getConstructor()
                    .newInstance();
            return ContainerRequest.class
                    .getConstructor(URI.class, URI.class, String.class, jakarta.ws.rs.core.SecurityContext.class, propertiesDelegateClass)
                    .newInstance(
                            URI.create("http://localhost/"),
                            URI.create("http://localhost" + V1_STATEMENT_PATH),
                            "POST",
                            null,
                            propertiesDelegate);
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create ContainerRequest test context", e);
        }
    }

    private static QueryUserInfoParser createFilter(boolean clientCertificateBridgeEnabled)
    {
        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setAnalyzeRequest(true);
        return createFilter(requestAnalyzerConfig, clientCertificateBridgeEnabled);
    }

    private static QueryUserInfoParser createFilter(RequestAnalyzerConfig requestAnalyzerConfig)
    {
        return createFilter(requestAnalyzerConfig, false);
    }

    private static QueryUserInfoParser createFilter(RequestAnalyzerConfig requestAnalyzerConfig, boolean clientCertificateBridgeEnabled)
    {
        HaGatewayConfiguration config = new HaGatewayConfiguration();
        config.setRequestAnalyzerConfig(requestAnalyzerConfig);
        if (clientCertificateBridgeEnabled) {
            ClientCertificateJwtAuthenticationConfiguration clientCertificateJwtAuthenticationConfiguration = new ClientCertificateJwtAuthenticationConfiguration();
            clientCertificateJwtAuthenticationConfiguration.setJwtSigningKeyPair(TEST_KEY_PAIR);
            config.setClientCertificateJwtAuthentication(clientCertificateJwtAuthenticationConfiguration);
        }

        return new QueryUserInfoParser(config, new PathFilter(config));
    }
}
