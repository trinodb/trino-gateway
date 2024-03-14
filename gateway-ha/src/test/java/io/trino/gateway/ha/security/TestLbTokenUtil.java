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
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
public class TestLbTokenUtil
{
    private String idToken;
    private final String rsaPrivateKey = "auth/test_private_key.pem";
    private final String rsaPublicKey = "auth/test_public_key.pem";
    private LbKeyProvider lbKeyProvider;
    private DecodedJWT jwt;

    @BeforeAll
    public void setup()
    {
        lbKeyProvider = new LbKeyProvider(new SelfSignKeyPairConfiguration(
                requireNonNull(getClass().getClassLoader().getResource(rsaPrivateKey)).getFile(),
                requireNonNull(getClass().getClassLoader().getResource(rsaPublicKey)).getFile()));
        Map<String, Object> headers = java.util.Map.of("alg", "RS256");
        Algorithm algorithm = Algorithm.RSA256(lbKeyProvider.getRsaPublicKey(),
                lbKeyProvider.getRsaPrivateKey());
        idToken = JWT.create()
                .withHeader(headers)
                .withIssuer(SessionCookie.SELF_ISSUER_ID)
                .withSubject("test")
                .withAudience("test.com")
                .sign(algorithm);
        jwt = JWT.decode(idToken);
    }

    @Test
    public void testAudiencesShouldPassIfNoAudiencesAreRequired()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.empty())).isTrue();
    }

    @Test
    public void testAudiencesShouldPassIfAnAudienceIsRequired()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("test.com")))).isTrue();
    }

    @Test
    public void testAudiencesShouldFailIfAudienceDoesNotMatch()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("no_match.com")))).isFalse();
    }

    @Test
    public void testAudiencesShouldPassIfAnyAudienceIsMatched()
    {
        assertThat(LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer(), Optional.of(List.of("test.com", "test1.com")))).isTrue();
    }
}
