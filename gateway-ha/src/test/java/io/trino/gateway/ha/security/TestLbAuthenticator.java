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
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.dropwizard.auth.basic.BasicCredentials;
import io.trino.gateway.ha.config.FormAuthConfiguration;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.trino.gateway.ha.security.SessionCookie.OAUTH_ID_TOKEN;
import static io.trino.gateway.ha.security.SessionCookie.logOut;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class TestLbAuthenticator
{
    private static final Logger log = LoggerFactory.getLogger(TestLbAuthenticator.class);

    private static final String USER = "username";
    private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
    private static final String ID_TOKEN = "TOKEN";

    @Test
    public void testAuthenticatorGetsPrincipal()
            throws Exception
    {
        Claim claim = Mockito.mock(Claim.class);
        Mockito
                .when(claim.toString())
                .thenReturn(USER);
        AuthorizationManager authorization = Mockito.mock(AuthorizationManager.class);

        Mockito
                .when(authorization.getPrivileges(USER))
                .thenReturn(MEMBER_OF);
        LbOAuthManager authentication = Mockito.mock(LbOAuthManager.class);

        Mockito
                .when(authentication.getClaimsFromIdToken(ID_TOKEN))
                .thenReturn(Optional.of(Map.of("sub", claim)));

        Mockito
                .when(authentication.getUserIdField())
                .thenReturn("sub");

        DecodedJWT jwt = Mockito.mock(DecodedJWT.class);
        Mockito
                .when(jwt.getIssuer())
                .thenReturn("not-self-issuer");

        LbPrincipal principal = new LbPrincipal(USER, MEMBER_OF);

        LbAuthenticator lbAuth = new LbAuthenticator(authentication, authorization);

        assertTrue(lbAuth.authenticate(ID_TOKEN).isPresent());
        assertEquals(principal, lbAuth.authenticate(ID_TOKEN).orElseThrow());
    }

    @Test
    public void testAuthenticatorMissingClaim()
            throws Exception
    {
        Claim claim = Mockito.mock(Claim.class);
        AuthorizationManager authorization = Mockito.mock(AuthorizationManager.class);
        LbOAuthManager authentication = Mockito.mock(LbOAuthManager.class);
        Mockito
                .when(authentication.getClaimsFromIdToken(ID_TOKEN))
                .thenReturn(Optional.of(Map.of("no-sub", claim)));
        Mockito
                .when(authentication.getUserIdField())
                .thenReturn("sub");

        LbAuthenticator lbAuth = new LbAuthenticator(authentication, authorization);

        assertFalse(lbAuth.authenticate(ID_TOKEN).isPresent());
    }

    @Test
    public void testPresetUsers()
    {
        Map presetUsers = new HashMap<String, UserConfiguration>()
        {
            {
                this.put("user1", new UserConfiguration("priv1, priv2", "pass1"));
                this.put("user2", new UserConfiguration("priv2, priv2", "pass2"));
            }
        };
        LbFormAuthManager authentication = new LbFormAuthManager(null, presetUsers);

        assertTrue(authentication
                .authenticate(new BasicCredentials("user1", "pass1")));
        assertFalse(authentication
                .authenticate(new BasicCredentials("user2", "pass1")));
        assertFalse(authentication
                .authenticate(new BasicCredentials("not-in-map-user", "pass1")));
    }

    @Test
    public void testNoLdapNoPresetUsers()
    {
        LbFormAuthManager authentication = new LbFormAuthManager(null, null);
        assertFalse(authentication
                .authenticate(new BasicCredentials("user1", "pass1")));
    }

    @Test
    public void testWrongLdapConfig()
    {
        LbFormAuthManager authentication = new LbFormAuthManager(null, null);
        assertFalse(authentication
                .authenticate(new BasicCredentials("user1", "pass1")));
    }

    @Test
    public void testLogout()
    {
        Response response = logOut();
        NewCookie cookie = response.getCookies().get(OAUTH_ID_TOKEN);
        log.info("value {}", cookie.getValue());
        assertEquals("logout", cookie.getValue());
    }

    @Test
    public void testLoginForm()
    {
        SelfSignKeyPairConfiguration keyPair = new SelfSignKeyPairConfiguration();
        keyPair.setPrivateKeyRsa("src/test/resources/auth/test_private_key.pem");
        keyPair.setPublicKeyRsa("src/test/resources/auth/test_public_key.pem");

        FormAuthConfiguration formAuthConfig = new FormAuthConfiguration();
        formAuthConfig.setSelfSignKeyPair(keyPair);

        Map presetUsers = new HashMap<String, UserConfiguration>()
        {
            {
                this.put("user1", new UserConfiguration("priv1, priv2", "pass1"));
                this.put("user2", new UserConfiguration("priv2, priv2", "pass2"));
            }
        };

        LbFormAuthManager lbFormAuthManager = new LbFormAuthManager(formAuthConfig, presetUsers);
        Response response = lbFormAuthManager.processLoginForm("user1", "pass1");
        NewCookie cookie = response.getCookies().get(OAUTH_ID_TOKEN);
        String value = cookie.getValue();
        assertTrue(value != null && value.length() > 0);
        log.info(cookie.getValue());
        JWT.decode(value);
    }
}
