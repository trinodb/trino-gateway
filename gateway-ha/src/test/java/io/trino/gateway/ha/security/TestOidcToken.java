package io.trino.gateway.ha.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.gateway.ha.security.LbOAuthManager.OidcTokens;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestOidcToken {
  private static final Logger log = LoggerFactory.getLogger(TestOidcToken.class);

  @Test
  public void testParseTokenParamsGracefully() {
    // This test is to make sure that we simulate the condition where
    // the OIDC providers send additional parameters in the token
    // 'to_be_ignored' parameter should not cause any parsing exception
    // All the other parameters should have the correct values
    OidcTokens oidcTokens = null;
    try {
      ObjectMapper objectMapper = new ObjectMapper();

      String jsonStr = "{\"id_token\" : \"ABC235234\", "
          + "\"access_token\" : \"AcessABCD123\", "
          + "\"refresh_token\" : \"RefreshTKN\", "
          + "\"token_type\" : \"TOKENType\", "
          + "\"expires_in\" : \"123456\", "
          + "\"to_be_ignored\" : \"XYX123456\", "
          + "\"scope\" : \"global\" "
          + "}";

      oidcTokens = objectMapper.readValue(jsonStr, OidcTokens.class);
    } catch (JsonProcessingException ex) {
      log.error(ex.getMessage());
      assertTrue(false);
    }

    assertTrue(oidcTokens.getIdToken().equals("ABC235234"));
    assertTrue(oidcTokens.getAccessToken().equals("AcessABCD123"));
    assertTrue(oidcTokens.getRefreshToken().equals("RefreshTKN"));
    assertTrue(oidcTokens.getExpiresIn().equals("123456"));
    assertTrue(oidcTokens.getScope().equals("global"));
  }
}
