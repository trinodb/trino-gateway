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

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.ImmutableList;
import io.trino.gateway.ha.config.ClientCertificateJwtAuthenticationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.handler.HttpUtils;
import io.trino.gateway.ha.testing.TestingHttpServletRequest;
import io.trino.gateway.ha.testing.TestingX509Certificate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static io.trino.gateway.ha.handler.HttpUtils.V1_SPOOLED_PATH;
import static io.trino.gateway.ha.handler.HttpUtils.V1_STATEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAddClientCertificateJwtHeader
{
    private static final SelfSignKeyPairConfiguration TEST_KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_private_key.pem",
            "src/test/resources/auth/test_public_key.pem");
    private static final SelfSignKeyPairConfiguration TEST_EC_KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_ec_private_key.pem",
            "src/test/resources/auth/test_ec_public_key.pem");

    @Test
    void testExtractIdentityFromCommonName()
    {
        X509Certificate certificate = certificate("CN=alice, OU=data, O=example");
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "CN")).hasValue("alice");
    }

    @Test
    void testExtractIdentityPrefersLeafCommonNameOnMultiRdnDn()
    {
        X509Certificate certificate = certificate("CN=alice,OU=data,CN=legacy,O=example");
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "CN")).hasValue("alice");
    }

    @Test
    void testExtractFullSubjectDn()
    {
        X509Certificate certificate = certificate("CN=alice, OU=data, O=example");
        // byte-for-byte what Trino's CertificateAuthenticator feeds to UserMapping
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "SUBJECT_DN"))
                .hasValue(certificate.getSubjectX500Principal().toString())
                .hasValue("CN=alice, OU=data, O=example");
    }

    @Test
    void testExtractIdentitySkipsHexEncodedRdnValue()
    {
        // single CN holding an OCTET STRING, which Rdn.getValue() hands back as byte[]
        X509Certificate certificate = new TestingX509Certificate(new byte[] {
                0x30, 0x10, 0x31, 0x0E, 0x30, 0x0C, 0x06, 0x03, 0x55, 0x04, 0x03, 0x04, 0x05, 'a', 'l', 'i', 'c', 'e',
        });

        assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=#0405616c696365");
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "CN")).isEmpty();
    }

    @Test
    void testExtractFullSubjectDnIsCaseInsensitive()
    {
        X509Certificate certificate = certificate("CN=alice, OU=data, O=example");
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "subject_dn"))
                .hasValue("CN=alice, OU=data, O=example");
    }

    @Test
    void testProxyDecorationAddsJwtAndPreservesClientRequestedUserHeader()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);
        configuration.getClientCertificateJwtAuthentication().setJwtIssuer("trino-gateway-tests");
        configuration.getClientCertificateJwtAuthentication().setJwtPrincipalClaim("preferred_username");
        configuration.getClientCertificateJwtAuthentication().setJwtAudiences(ImmutableList.of("trino"));
        configuration.getRequestAnalyzerConfig().setClientCertificateUserMappingPattern("(.*)@example\\.com");

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.addHeader(HttpUtils.USER_HEADER, "bob");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice@example.com, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        assertThat(decoratedRequest.getHeader(HttpUtils.USER_HEADER)).isEqualTo("bob");
        assertThat(decoratedRequest.getHeader("Authorization")).startsWith("Bearer ");

        String token = decoratedRequest.getHeader("Authorization").substring("Bearer ".length());
        LbKeyProvider keyProvider = new LbKeyProvider(TEST_KEY_PAIR);
        assertThat(LbTokenUtil.validateToken(token, keyProvider.publicKey(), "trino-gateway-tests", Optional.of(ImmutableList.of("trino")))).isTrue();

        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaim("preferred_username").asString()).isEqualTo("alice");
    }

    @Test
    void testProxyDecorationOmitsIssuerWhenNotConfigured()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        String token = decoratedRequest.getHeader("Authorization").substring("Bearer ".length());
        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getIssuer()).isNull();
        assertThat(jwt.getSubject()).isEqualTo("alice");
    }

    @Test
    void testProxyDecorationSupportsEcSigning()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_EC_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        String token = decoratedRequest.getHeader("Authorization").substring("Bearer ".length());
        LbKeyProvider keyProvider = new LbKeyProvider(TEST_EC_KEY_PAIR);
        assertThat(LbTokenUtil.validateToken(token, keyProvider.publicKey(), null, Optional.empty())).isTrue();

        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getAlgorithm()).isEqualTo("ES256");
        assertThat(jwt.getSubject()).isEqualTo("alice");
    }

    @Test
    void testProxyDecorationDoesNotRewriteUserHeader()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.addHeader(HttpUtils.USER_HEADER, "bob");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest routingRequest = authenticator.authenticate(request);

        assertThat(routingRequest.getHeader(HttpUtils.USER_HEADER)).isEqualTo("bob");
        assertThat(routingRequest.getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    void testProxyDecorationOverridesUserHeaderWhenEnabled()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);
        configuration.getClientCertificateJwtAuthentication().setOverrideTrinoUser(true);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.addHeader(HttpUtils.USER_HEADER, "bob");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        assertThat(decoratedRequest.getHeader(HttpUtils.USER_HEADER)).isEqualTo("alice");
        assertThat(decoratedRequest.getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    void testDecoratorAuthenticatesSpooledPath()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_SPOOLED_PATH + "/segments/abc");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        assertThat(decoratedRequest.getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    void testJwtNotBeforeToleratesClockSkew()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        String token = decoratedRequest.getHeader("Authorization").substring("Bearer ".length());
        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getNotBeforeAsInstant()).isBefore(jwt.getIssuedAtAsInstant());
        assertThat(java.time.Duration.between(jwt.getNotBeforeAsInstant(), jwt.getIssuedAtAsInstant()).toSeconds()).isEqualTo(60);
    }

    @Test
    void testDecoratorSkipsNonQueryPaths()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest("/oauth2/callback");

        assertThat(authenticator.authenticate(request)).isSameAs(request);
    }

    @Test
    void testDecoratorSkipsMissingCertificate()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);

        assertThat(authenticator.authenticate(request)).isSameAs(request);
        assertThat(request.getAttribute(HttpUtils.TRINO_REQUEST_USER)).isNull();
    }

    @Test
    void testDecoratorRejectsMissingCertificateWhenRequired()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);
        configuration.getClientCertificateJwtAuthentication().setClientCertificateRequired(true);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);

        assertThatThrownBy(() -> authenticator.authenticate(request))
                .isInstanceOf(jakarta.ws.rs.WebApplicationException.class)
                .satisfies(e -> assertThat(((jakarta.ws.rs.WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    @Test
    void testDecoratorAllowsCertificateWhenRequired()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);
        configuration.getClientCertificateJwtAuthentication().setClientCertificateRequired(true);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        assertThat(decoratedRequest.getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    void testConfiguringBridgeWithoutClientCertificateIdentityFieldFails()
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = new ClientCertificateJwtAuthenticationConfiguration();
        authConfig.setJwtSigningKeyPair(TEST_KEY_PAIR);

        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setRequestAnalyzerConfig(new RequestAnalyzerConfig());
        configuration.setClientCertificateJwtAuthentication(authConfig);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(HaGatewayConfiguration.HaGatewayConfigurationException.class)
                .hasMessageContaining("clientCertificateIdentityField must be set");
    }

    @Test
    void testDecoratorReusesCachedIdentity()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(ClientCertificateUserResolver.MAPPED_USER_ATTRIBUTE, "alice");

        HttpServletRequest decoratedRequest = authenticator.authenticate(request);

        assertThat(decoratedRequest.getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    void testDecoratorReusesJwtAcrossRequestsForSameIdentity()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);

        TestingHttpServletRequest firstRequest = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        firstRequest.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        TestingHttpServletRequest secondRequest = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        secondRequest.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest firstDecoratedRequest = authenticator.authenticate(firstRequest);
        HttpServletRequest secondDecoratedRequest = authenticator.authenticate(secondRequest);

        assertThat(firstDecoratedRequest.getHeader("Authorization")).isEqualTo(secondDecoratedRequest.getHeader("Authorization"));
    }

    @Test
    void testDecoratorRejectsRequestWhenClientCertificateMappingFails()
    {
        HaGatewayConfiguration configuration = configuredBridge(TEST_KEY_PAIR);
        configuration.getRequestAnalyzerConfig().setClientCertificateUserMappingPattern("(.*)@example\\.com");

        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {certificate("CN=alice, OU=data, O=example")});

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> authenticator.authenticate(request)))
                .isInstanceOf(jakarta.ws.rs.WebApplicationException.class);
    }

    @Test
    void testNoopAuthenticatorReturnsOriginalRequest()
    {
        NoopProxyRequestAuthenticator authenticator = new NoopProxyRequestAuthenticator();
        TestingHttpServletRequest request = new TestingHttpServletRequest(V1_STATEMENT_PATH);

        assertThat(authenticator.authenticate(request)).isSameAs(request);
    }

    private static HaGatewayConfiguration configuredBridge(SelfSignKeyPairConfiguration signingKeyPair)
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = new ClientCertificateJwtAuthenticationConfiguration();
        authConfig.setJwtSigningKeyPair(signingKeyPair);

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
}
