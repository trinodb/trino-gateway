package io.trino.gateway.ha.security;

import static io.trino.gateway.ha.security.SessionCookie.OAUTH_ID_TOKEN;
import static io.trino.gateway.ha.security.SessionCookie.logOut;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.dropwizard.auth.basic.BasicCredentials;
import io.trino.gateway.ha.config.FormAuthConfiguration;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class TestLbAuthenticator {

  private static final String USER = "username";
  private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
  private static final String ID_TOKEN = "TOKEN";

  @Test
  public void testAuthenticatorGetsPrincipal() throws Exception {

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

    Assert.assertTrue(lbAuth.authenticate(ID_TOKEN).isPresent());
    Assert.assertEquals(lbAuth.authenticate(ID_TOKEN).get(), principal);
  }

  @Test
  public void testAuthenticatorMissingClaim() throws Exception {

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

    Assert.assertFalse(lbAuth.authenticate(ID_TOKEN).isPresent());
  }

  @Test
  public void testPresetUsers() throws Exception {
    Map presetUsers = new HashMap<String, UserConfiguration>() {
      {
        this.put("user1", new UserConfiguration("priv1, priv2", "pass1"));
        this.put("user2", new UserConfiguration("priv2, priv2", "pass2"));
      }
    };
    LbFormAuthManager authentication = new LbFormAuthManager(null, presetUsers);

    Assert.assertTrue(authentication
        .authenticate(new BasicCredentials("user1", "pass1")));
    Assert.assertFalse(authentication
        .authenticate(new BasicCredentials("user2", "pass1")));
    Assert.assertFalse(authentication
        .authenticate(new BasicCredentials("not-in-map-user", "pass1")));

  }

  @Test
  public void testNoLdapNoPresetUsers() throws Exception {
    LbFormAuthManager authentication = new LbFormAuthManager(null, null);
    Assert.assertFalse(authentication
        .authenticate(new BasicCredentials("user1", "pass1")));
  }

  @Test
  public void testWrongLdapConfig() throws Exception {
    LbFormAuthManager authentication = new LbFormAuthManager(null, null);
    Assert.assertFalse(authentication
        .authenticate(new BasicCredentials("user1", "pass1")));
  }

  @Test
  public void testLogout() throws Exception {
    Response response = logOut();
    NewCookie cookie = response.getCookies().get(OAUTH_ID_TOKEN);
    log.info("value {}", cookie.getValue());
    Assert.assertTrue(cookie.getValue().equals("logout"));
  }

  @Test
  public void testLoginForm() throws Exception {
    SelfSignKeyPairConfiguration keyPair = new SelfSignKeyPairConfiguration();
    keyPair.setPrivateKeyRsa("src/test/resources/auth/test_private_key.pem");
    keyPair.setPublicKeyRsa("src/test/resources/auth/test_public_key.pem");

    FormAuthConfiguration formAuthConfig = new FormAuthConfiguration();
    formAuthConfig.setSelfSignKeyPair(keyPair);

    Map presetUsers = new HashMap<String, UserConfiguration>() {
      {
        this.put("user1", new UserConfiguration("priv1, priv2", "pass1"));
        this.put("user2", new UserConfiguration("priv2, priv2", "pass2"));
      }
    };

    LbFormAuthManager lbFormAuthManager = new LbFormAuthManager(formAuthConfig, presetUsers);
    Response response = lbFormAuthManager.processLoginForm("user1", "pass1");
    NewCookie cookie = response.getCookies().get(OAUTH_ID_TOKEN);
    String value = cookie.getValue();
    Assert.assertTrue(value != null && value.length() > 0);
    log.info(cookie.getValue());
    JWT.decode(value);
  }
}
