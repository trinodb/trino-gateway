package io.trino.gateway.ha.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.gateway.ha.config.OAuthConfiguration;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class LbOAuthManager {

  /**
   * Cookie key to pass the token.
   */
  private final OAuthConfiguration oauthConfig;

  public LbOAuthManager(OAuthConfiguration configuration) {
    this.oauthConfig = configuration;
  }

  public String getUserIdField() {
    return oauthConfig.getUserIdField();
  }

  /**
   * Exchanges authorization code for access token.
   * Sets it in a cookie and redirects back to a location.
   *
   * @param code             the authorization code obtained from the authorization server
   * @param redirectLocation the application path to redirect back to
   * @return redirect response with a Set-Cookie header
   */
  public Response exchangeCodeForToken(String code, String redirectLocation) {
    String tokenEndpoint = oauthConfig.getTokenEndpoint();
    String clientId = oauthConfig.getClientId();
    String clientSecret = oauthConfig.getClientSecret();
    String redirectUri = oauthConfig.getRedirectUrl();
    Client oauthClient = ClientBuilder.newBuilder().build();

    Form form = new Form().param("grant_type", "authorization_code")
        .param("client_id", clientId)
        .param("client_secret", clientSecret)
        .param("code", code)
        .param("redirect_uri", redirectUri);

    Response tokenResponse = oauthClient
        .target(tokenEndpoint)
        .request()
        .post(Entity.form(form));

    if (tokenResponse.getStatusInfo()
        .getFamily() != Response.Status.Family.SUCCESSFUL) {
      String message = String.format("token response failed with code %d - %s",
          tokenResponse.getStatus(),
          tokenResponse.readEntity(String.class));
      log.error(message);
      return Response.status(500).entity(message).build();
    }

    OidcTokens tokens = tokenResponse.readEntity(OidcTokens.class);

    return Response.status(302)
        .location(URI.create(redirectLocation))
        .cookie(SessionCookie.getTokenCookie(tokens.getIdToken()))
        .build();
  }

  /**
   * Redirects to the authorization provider for the authorization code.
   *
   * @return redirect response to the authorization provider
   */
  public Response getAuthorizationCode() {
    String authorizationEndpoint = oauthConfig.getAuthorizationEndpoint();
    String clientId = oauthConfig.getClientId();
    String redirectUrl = oauthConfig.getRedirectUrl();
    String scopes = String.join("+", oauthConfig.getScopes());
    String url = String.format(
        "%s?client_id=%s&response_type=code&redirect_uri=%s&scope=%s",
        authorizationEndpoint, clientId, redirectUrl, scopes);

    return Response.status(302)
        .location(URI.create(url))
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

      String jwkEndpoint = oauthConfig.getJwkEndpoint();
      JwkProvider provider = new UrlJwkProvider(new URL(jwkEndpoint));
      Jwk jwk = provider.get(jwt.getKeyId());
      RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

      if (LbTokenUtil.validateToken(idToken, publicKey, jwt.getIssuer())) {
        return Optional.of(jwt.getClaims());
      }

    } catch (Exception exc) {
      log.error("Could not validate token or get claims from it.", exc);
    }
    return Optional.empty();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class OidcTokens {

    @JsonProperty
    private final String accessToken;
    @JsonProperty
    private final String idToken;
    @JsonProperty
    private final String scope;
    @JsonProperty
    private final String refreshToken;
    @JsonProperty
    private final String tokenType;
    @JsonProperty
    private final String expiresIn;

    @JsonCreator
    public OidcTokens(@JsonProperty("id_token") String idToken,
                      @JsonProperty("access_token") String accessToken,
                      @JsonProperty("refresh_token") String refreshToken,
                      @JsonProperty("token_type") String tokenType,
                      @JsonProperty("expires_in") String expiresIn,
                      @JsonProperty("scope") String scope) {
      this.accessToken = accessToken;
      this.idToken = idToken;
      this.tokenType = tokenType;
      this.expiresIn = expiresIn;
      this.scope = scope;
      this.refreshToken = refreshToken;
    }
  }

}
