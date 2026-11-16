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
import io.trino.gateway.ha.config.OAuthConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TestLbOAuthManager
{
    @Test
    void testGetClaimsFromEcIdToken()
            throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                            """
                            {"keys": [{"kty": "EC", "kid": "test-key", "crv": "P-256", "x": "%s", "y": "%s"}]}
                            """.formatted(coordinate(publicKey.getW().getAffineX().toByteArray()), coordinate(publicKey.getW().getAffineY().toByteArray()))));
            OAuthConfiguration config = new OAuthConfiguration();
            config.setJwkEndpoint(server.url("/jwks").toString());
            LbOAuthManager manager = new LbOAuthManager(config, Map.of());

            String idToken = JWT.create()
                    .withKeyId("test-key")
                    .withIssuer("test-issuer")
                    .sign(Algorithm.ECDSA256(publicKey, (ECPrivateKey) keyPair.getPrivate()));

            assertThat(manager.getClaimsFromIdToken(idToken)).isPresent();
        }
    }

    private static String coordinate(byte[] bytes)
    {
        byte[] padded = new byte[32];
        byte[] trimmed = Arrays.copyOfRange(bytes, Math.max(0, bytes.length - 32), bytes.length);
        System.arraycopy(trimmed, 0, padded, 32 - trimmed.length, trimmed.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padded);
    }
}
