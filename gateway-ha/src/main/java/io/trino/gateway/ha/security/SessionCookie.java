package io.trino.gateway.ha.security;

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

public class SessionCookie {
  static final String OAUTH_ID_TOKEN = "token";
  static final String SELF_ISSUER_ID = "self";

  public static NewCookie getTokenCookie(String token) {
    return new NewCookie(OAUTH_ID_TOKEN,
        token, "/", "", "",
        60 * 60 * 24, true);
  }

  public static Response logOut() {
    NewCookie cookie = new NewCookie(OAUTH_ID_TOKEN,
        "logout", "/", "", "",
        0, true);
    return Response.ok("You are logged out successfully.")
        .cookie(cookie)
        .build();
  }

}
