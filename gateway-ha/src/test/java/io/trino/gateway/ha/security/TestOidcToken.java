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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.security.LbOAuthManager.OidcTokens;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestOidcToken
{
    @Test
    public void testParseTokenParamsGracefully()
            throws Exception
    {
        // This test is to make sure that we simulate the condition where
        // the OIDC providers send additional parameters in the token
        // 'to_be_ignored' parameter should not cause any parsing exception
        // All the other parameters should have the correct values
        @Language("JSON")
        String jsonStr = """
                {
                  "id_token": "ABC235234",
                  "access_token": "AcessABCD123",
                  "refresh_token": "RefreshTKN",
                  "token_type": "TOKENType",
                  "expires_in": "123456",
                  "to_be_ignored": "XYX123456",
                  "scope": "global"
                }
                """;
        OidcTokens oidcTokens = new ObjectMapper().readValue(jsonStr, OidcTokens.class);

        assertThat(oidcTokens.getIdToken()).isEqualTo("ABC235234");
        assertThat(oidcTokens.getAccessToken()).isEqualTo("AcessABCD123");
        assertThat(oidcTokens.getRefreshToken()).isEqualTo("RefreshTKN");
        assertThat(oidcTokens.getExpiresIn()).isEqualTo("123456");
        assertThat(oidcTokens.getScope()).isEqualTo("global");
    }
}
