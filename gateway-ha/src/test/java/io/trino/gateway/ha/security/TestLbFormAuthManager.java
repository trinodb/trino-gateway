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
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.FormAuthConfiguration;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.request.RestLoginRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static io.trino.gateway.ha.security.SessionCookie.SELF_ISSUER_ID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

final class TestLbFormAuthManager
{
    private static final SelfSignKeyPairConfiguration KEY_PAIR = new SelfSignKeyPairConfiguration(
            "src/test/resources/auth/test_private_key.pem",
            "src/test/resources/auth/test_public_key.pem");

    @Test
    void testLoginTokenExpiresAfterSessionTimeout()
    {
        FormAuthConfiguration configuration = new FormAuthConfiguration();
        configuration.setSelfSignKeyPair(KEY_PAIR);
        configuration.setSessionTimeout(new Duration(5, MINUTES));
        LbFormAuthManager manager = new LbFormAuthManager(configuration, ImmutableMap.of("user1", new UserConfiguration("priv1", "pass1")), ImmutableMap.of());

        Instant before = Instant.now();
        Result<?> result = manager.processRESTLogin(new RestLoginRequest("user1", "pass1"));
        assertThat(Result.isSuccess(result)).isTrue();
        String token = (String) ((Map<?, ?>) result.getData()).get("token");

        DecodedJWT jwt = JWT.decode(token);
        assertThat(jwt.getExpiresAt()).isAfterOrEqualTo(Date.from(before.plusSeconds(300).minusSeconds(1)));
        assertThat(jwt.getExpiresAt()).isBeforeOrEqualTo(Date.from(Instant.now().plusSeconds(300).plusSeconds(1)));
        assertThat(manager.getClaimsFromIdToken(token)).isPresent();
    }

    @Test
    void testExpiredTokenIsRejected()
    {
        LbFormAuthManager manager = manager();
        // Expired well beyond the verifier clock skew leeway
        String token = selfSignedToken().withExpiresAt(Date.from(Instant.now().minusSeconds(300))).sign(new LbKeyProvider(KEY_PAIR).signingAlgorithm());
        assertThat(manager.getClaimsFromIdToken(token)).isEmpty();
    }

    @Test
    void testTokenExpiredWithinLeewayIsAccepted()
    {
        LbFormAuthManager manager = manager();
        // Expired recently enough to fall within the verifier clock skew leeway
        String token = selfSignedToken().withExpiresAt(Date.from(Instant.now().minusSeconds(30))).sign(new LbKeyProvider(KEY_PAIR).signingAlgorithm());
        assertThat(manager.getClaimsFromIdToken(token)).isPresent();
    }

    @Test
    void testTokenWithoutExpirationIsRejected()
    {
        LbFormAuthManager manager = manager();
        String token = selfSignedToken().sign(new LbKeyProvider(KEY_PAIR).signingAlgorithm());
        assertThat(manager.getClaimsFromIdToken(token)).isEmpty();
    }

    private static LbFormAuthManager manager()
    {
        FormAuthConfiguration configuration = new FormAuthConfiguration();
        configuration.setSelfSignKeyPair(KEY_PAIR);
        return new LbFormAuthManager(configuration, ImmutableMap.of(), ImmutableMap.of());
    }

    private static JWTCreator.Builder selfSignedToken()
    {
        return JWT.create().withIssuer(SELF_ISSUER_ID).withSubject("user1");
    }
}
