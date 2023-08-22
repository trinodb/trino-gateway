package io.trino.gateway.ha.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.dropwizard.auth.basic.BasicCredentials;
import io.trino.gateway.ha.config.FormAuthConfiguration;
import io.trino.gateway.ha.config.LdapConfiguration;
import io.trino.gateway.ha.config.UserConfiguration;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LbFormAuthManager {

  /**
   * Cookie key to pass the token.
   */
  private final FormAuthConfiguration configuration;
  private final LbKeyProvider lbKeyProvider;
  Map<String, UserConfiguration> presetUsers;
  private LdapConfiguration ldapConfiguration;
  private LbLdapClient lbLdapClient;

  public LbFormAuthManager(FormAuthConfiguration configuration,
                           Map<String, UserConfiguration> presetUsers) {
    this.configuration = configuration;
    this.presetUsers = presetUsers;

    if (configuration != null) {
      this.lbKeyProvider = new LbKeyProvider(configuration
          .getSelfSignKeyPair());
    } else {
      this.lbKeyProvider = null;
    }

    if (configuration != null && configuration.getLdapConfigPath() != null) {
      lbLdapClient = new LbLdapClient(LdapConfiguration.load(configuration.getLdapConfigPath()));
    } else {
      lbLdapClient = null;
    }
  }

  public String getUserIdField() {
    return "sub";
  }

  public Response processLoginForm(String username, String password) {
    if (authenticate(new BasicCredentials(username, password))) {
      String token = getSelfSignedToken(username);
      return Response.status(302).location(URI.create("/"))
          .cookie(SessionCookie.getTokenCookie(token))
          .build();
    }

    return Response.status(302).location(URI.create("/"))
        .build();
  }


  /**
   * Verifies if the id token is valid. If valid, it returns a map with the claims,
   * else an empty optional. idToken docs: https://www.oauth
   * .com/oauth2-servers/openid-connect/id-tokens/
   *
   * @param idToken the access token provided back by the authorization server.
   * @return a map with the token claims
   * @throws Exception is thrown if the access token is invalid
   */
  public Optional<Map<String, Claim>> getClaimsFromIdToken(String idToken) {
    try {
      DecodedJWT jwt = JWT.decode(idToken);

      if (LbTokenUtil.validateToken(idToken, lbKeyProvider.getRsaPublicKey(), jwt.getIssuer())) {
        return Optional.of(jwt.getClaims());
      }

    } catch (Exception exc) {
      log.error("Could not validate token or get claims from it.", exc);
    }
    return Optional.empty();
  }


  private String getSelfSignedToken(String username) {
    String token = "";

    try {
      Algorithm algorithm = Algorithm.RSA256(lbKeyProvider.getRsaPublicKey(),
          lbKeyProvider.getRsaPrivateKey());

      Map<String, Object> headers = Map.of("alg", "RS256");

      token = JWT.create()
          .withHeader(headers)
          .withIssuer(SessionCookie.SELF_ISSUER_ID)
          .withSubject(username)
          .sign(algorithm);
    } catch (JWTCreationException exception) {
      // Invalid Signing configuration / Couldn't convert Claims.
      log.error("Error while creating the selfsigned token JWT");
      throw exception;
    }
    return token;
  }

  public boolean authenticate(BasicCredentials credentials) {
    if (lbLdapClient != null
        && lbLdapClient.authenticate(credentials.getUsername(),
        credentials.getPassword())) {
      return true;
    }

    if (presetUsers != null) {
      UserConfiguration user = presetUsers.get(credentials.getUsername());
      if (user != null
          && user.getPassword().equals(credentials.getPassword())) {
        return true;
      }
    }

    return false;
  }
}
