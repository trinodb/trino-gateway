package com.lyft.data.gateway.ha.security;

import io.dropwizard.auth.UnauthorizedHandler;
import java.net.URI;
import javax.ws.rs.core.Response;

public class LbUnauthorizedHandler implements UnauthorizedHandler {

  private final String redirectPath;

  public LbUnauthorizedHandler(String authenticationType) {
    if (authenticationType.equals("oauth")) {
      this.redirectPath = "/sso";
    } else {
      this.redirectPath = "/login";
    }
  }

  @Override
  public Response buildResponse(String prefix, String realm) {
    return Response.status(302).location(URI.create(redirectPath)).build();
  }
}