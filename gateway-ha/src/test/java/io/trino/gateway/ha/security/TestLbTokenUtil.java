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
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
final class TestLbTokenUtil
{
    private String idToken;
    private final String rsaPrivateKey = "auth/test_private_key.pem";
    private final String rsaPublicKey = "auth/test_public_key.pem";
    private LbKeyProvider lbKeyProvider;
    private DecodedJWT jwt;

    @BeforeAll
    void setup()
    {
        lbKeyProvider = new LbKeyProvider(new SelfSignKeyPairConfiguration(
                requireNonNull(getClass().getClassLoader().getResource(rsaPrivateKey)).getFile(),
                requireNonNull(getClass().getClassLoader().getResource(rsaPublicKey)).getFile()));
        idToken = JWT.create()
                .withHeader(ImmutableMap.of("alg", lbKeyProvider.jwtAlgorithmName()))
                .withIssuer(SessionCookie.SELF_ISSUER_ID)
                .withSubject("test")
                .withAudience("test.com")
                .sign(lbKeyProvider.signingAlgorithm());
        jwt = JWT.decode(idToken);
    }

    @Test
    void testAudiencesShouldPassIfNoAudiencesAreRequired()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.publicKey(), jwt.getIssuer(), Optional.empty())).isTrue();
    }

    @Test
    void testAudiencesShouldPassIfAnAudienceIsRequired()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.publicKey(), jwt.getIssuer(), Optional.of(ImmutableList.of("test.com")))).isTrue();
    }

    @Test
    void testAudiencesShouldFailIfAudienceDoesNotMatch()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.publicKey(), jwt.getIssuer(), Optional.of(ImmutableList.of("no_match.com")))).isFalse();
    }

    @Test
    void testAudiencesShouldPassIfAnyAudienceIsMatched()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.publicKey(), jwt.getIssuer(), Optional.of(ImmutableList.of("test.com", "test1.com")))).isTrue();
    }

    @Test
    void testEcTokenValidationWithAudiences()
    {
        LbKeyProvider ecKeyProvider = new LbKeyProvider(new SelfSignKeyPairConfiguration(
                "src/test/resources/auth/test_ec_private_key.pem",
                "src/test/resources/auth/test_ec_public_key.pem"));
        String ecToken = JWT.create()
                .withHeader(ImmutableMap.of("alg", ecKeyProvider.jwtAlgorithmName()))
                .withIssuer(SessionCookie.SELF_ISSUER_ID)
                .withSubject("test")
                .withAudience("test.com")
                .sign(ecKeyProvider.signingAlgorithm());
        DecodedJWT ecJwt = JWT.decode(ecToken);

        assertThat(ecJwt.getAlgorithm()).isEqualTo("ES256");
        assertThat(LbTokenUtil.validateToken(ecToken, ecKeyProvider.publicKey(), ecJwt.getIssuer(), Optional.empty())).isTrue();
        assertThat(LbTokenUtil.validateToken(ecToken, ecKeyProvider.publicKey(), ecJwt.getIssuer(), Optional.of(ImmutableList.of("test.com")))).isTrue();
        assertThat(LbTokenUtil.validateToken(ecToken, ecKeyProvider.publicKey(), ecJwt.getIssuer(), Optional.of(ImmutableList.of("no_match.com")))).isFalse();
    }
}
