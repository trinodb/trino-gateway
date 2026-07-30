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
import io.trino.gateway.ha.testing.TestingHttpServletRequest;
import io.trino.gateway.ha.testing.TestingX509Certificate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A request target that JAX-RS still routes to Trino's {@code /v1/statement} resource must not
 * escape the bridge, which decides from the raw {@link HttpServletRequest#getRequestURI()}.
 */
final class TestClientCertificateJwtBridgePathEnforcement
{
    private static final SelfSignKeyPairConfiguration TEST_KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_private_key.pem",
            "src/test/resources/auth/test_public_key.pem");

    @Test
    void testClientCertificateRequiredFailsClosedOnMatrixParameterStatementPath()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        // no client certificate at all
        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/statement;a=b");
        request.addHeader("Authorization", "Bearer client-supplied-token");

        assertUnauthorized(() -> authenticator.authenticate(request));
    }

    @Test
    void testClientCertificateRequiredFailsClosedOnMatrixParameterFollowUpPath()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/statement;a=b/executing/20260101_000000_00000_aaaaa/slug/1");

        assertUnauthorized(() -> authenticator.authenticate(request));
    }

    @Test
    void testClientCertificateRequiredFailsClosedOnDotSegmentEscapeFromExemptPath()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        // canonicalises to /v1/statement, so it must not inherit /v1/info's exemption
        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/info/../statement");

        assertUnauthorized(() -> authenticator.authenticate(request));
    }

    @Test
    void testClientCertificateRequiredFailsClosedOnEncodedDotSegmentEscapeFromExemptPath()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        for (String path : List.of("/v1/info/%2e%2e/statement", "/v1/info/%2E%2E/statement", "/v1/info%2f..%2fstatement")) {
            TestingHttpServletRequest request = new TestingHttpServletRequest(path);
            request.addHeader("Authorization", "Bearer client-supplied-token");

            assertUnauthorized(() -> authenticator.authenticate(request));
        }
    }

    @Test
    void testClientCertificateRequiredFailsClosedOnExtraWhitelistedPath()
    {
        HaGatewayConfiguration configuration = configuredBridge();
        configuration.setExtraWhitelistPaths(List.of("/v1/custom.*"));
        configuration.getClientCertificateJwtAuthentication().setClientCertificateRequired(true);
        ClientCertificateJwtRequestAuthenticator authenticator = new ClientCertificateJwtRequestAuthenticator(configuration);

        assertUnauthorized(() -> authenticator.authenticate(new TestingHttpServletRequest("/v1/custom")));
    }

    @Test
    void testCanonicalStatementPathStillFailsClosed()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        assertUnauthorized(() -> authenticator.authenticate(new TestingHttpServletRequest("/v1/statement")));
    }

    @Test
    void testExemptPathsAreStillForwardedWithoutACertificate()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setClientCertificateRequired(true));

        for (String exemptPath : List.of("/ui", "/ui/query.html", "/ui/api/stats", "/oauth2/callback", "/v1/info", "/v1/node")) {
            TestingHttpServletRequest request = new TestingHttpServletRequest(exemptPath);
            assertThat(authenticator.authenticate(request))
                    .describedAs(exemptPath)
                    .isSameAs(request);
        }
    }

    @Test
    void testMatrixParameterStatementPathIsBridgedWhenACertificateIsPresent()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> {
            bridge.setClientCertificateRequired(true);
            bridge.setOverrideTrinoUser(true);
        });

        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/statement;a=b");
        request.addHeader("X-Trino-User", "bob");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {new TestingX509Certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest forwarded = authenticator.authenticate(request);

        assertThat(forwarded.getHeader("Authorization")).startsWith("Bearer ");
        assertThat(forwarded.getHeader("X-Trino-User")).isEqualTo("alice");
    }

    @Test
    void testOverrideTrinoUserAlsoPinsOriginalUserHeader()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(bridge -> bridge.setOverrideTrinoUser(true));

        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/statement");
        request.addHeader("X-Trino-User", "bob");
        request.addHeader("X-Trino-Original-User", "carol");
        request.addHeader("X-Trino-Original-Roles", "system=ROLE{admin}");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {new TestingX509Certificate("CN=alice, OU=data, O=example")});

        HttpServletRequest forwarded = authenticator.authenticate(request);

        assertThat(forwarded.getHeader("X-Trino-User")).isEqualTo("alice");
        // drives Trino's checkCanSetUser / checkCanImpersonateUser, so it must not stay client-controlled
        assertThat(forwarded.getHeader("X-Trino-Original-User")).isEqualTo("alice");
        // Trino validates roles against the now-pinned original identity, so they pass through
        assertThat(forwarded.getHeader("X-Trino-Original-Roles")).isEqualTo("system=ROLE{admin}");
    }

    @Test
    void testOriginalUserHeaderIsPreservedWhenOverrideIsOff()
    {
        ClientCertificateJwtRequestAuthenticator authenticator = authenticator(_ -> {});

        TestingHttpServletRequest request = new TestingHttpServletRequest("/v1/statement");
        request.addHeader("X-Trino-Original-User", "carol");
        request.setAttribute(
                ClientCertificateIdentityExtractor.JAKARTA_X509_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[] {new TestingX509Certificate("CN=alice, OU=data, O=example")});

        assertThat(authenticator.authenticate(request).getHeader("X-Trino-Original-User")).isEqualTo("carol");
    }

    @Test
    void testSubjectDnMatchesTrinoCertificateAuthenticator()
    {
        X509Certificate certificate = new TestingX509Certificate("CN=alice, OU=data, O=example");

        // Trino maps Identity.forUser(principal.toString())
        assertThat(ClientCertificateIdentityExtractor.extractIdentity(certificate, "SUBJECT_DN"))
                .hasValue(certificate.getSubjectX500Principal().toString());
    }

    @Test
    void testTrinoDenyRuleIsAppliedWhenMappingTheSubjectDn()
            throws Exception
    {
        // a user mapping file copied verbatim from a Trino cluster
        UserMapping trinoRules = new UserMapping(List.of(
                new UserMapping.Rule("CN=admin,.*", "$0", false, UserMappingCase.KEEP),
                new UserMapping.Rule("CN=([^,]+),.*", "$1", true, UserMappingCase.KEEP),
                new UserMapping.Rule("(.*)", "$1", true, UserMappingCase.KEEP)));

        X509Certificate adminCertificate = new TestingX509Certificate("CN=admin, OU=data, O=example");
        String gatewayInput = ClientCertificateIdentityExtractor.extractIdentity(adminCertificate, "SUBJECT_DN").orElseThrow();

        // the deny rule now fires on the gateway exactly as on the cluster
        assertThat(catchThrowable(() -> trinoRules.mapUser(gatewayInput)))
                .isInstanceOf(UserMappingException.class);
        assertThat(trinoRules.mapUser(
                ClientCertificateIdentityExtractor.extractIdentity(
                        new TestingX509Certificate("CN=alice, OU=data, O=example"), "SUBJECT_DN").orElseThrow()))
                .isEqualTo("alice");
    }

    @Test
    void testDenyRulesWithAnExtractedIdentityFieldAreRejectedAtStartup()
    {
        HaGatewayConfiguration configuration = configuredBridge();
        configuration.getRequestAnalyzerConfig().setClientCertificateUserMappingFile("src/test/resources/auth/test-user-mapping.json");

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(HaGatewayConfiguration.HaGatewayConfigurationException.class)
                .hasMessageContaining("SUBJECT_DN");
    }

    @Test
    void testDenyRulesAreAcceptedWithSubjectDn()
    {
        HaGatewayConfiguration configuration = configuredBridge();
        configuration.getRequestAnalyzerConfig().setClientCertificateIdentityField("SUBJECT_DN");
        configuration.getRequestAnalyzerConfig().setClientCertificateUserMappingFile("src/test/resources/auth/test-user-mapping.json");

        configuration.validate();
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable)
    {
        assertThatThrownBy(callable)
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    private static ClientCertificateJwtRequestAuthenticator authenticator(java.util.function.Consumer<ClientCertificateJwtAuthenticationConfiguration> customizer)
    {
        HaGatewayConfiguration configuration = configuredBridge();
        customizer.accept(configuration.getClientCertificateJwtAuthentication());
        return new ClientCertificateJwtRequestAuthenticator(configuration);
    }

    private static HaGatewayConfiguration configuredBridge()
    {
        ClientCertificateJwtAuthenticationConfiguration authConfig = new ClientCertificateJwtAuthenticationConfiguration();
        authConfig.setJwtSigningKeyPair(TEST_KEY_PAIR);

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setClientCertificateIdentityField("CN");

        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        configuration.setRequestAnalyzerConfig(requestAnalyzerConfig);
        configuration.setClientCertificateJwtAuthentication(authConfig);
        return configuration;
    }
}
