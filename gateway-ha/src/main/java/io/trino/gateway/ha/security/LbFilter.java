package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthFilter;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Priority(Priorities.AUTHENTICATION)
public class LbFilter<P extends Principal> extends AuthFilter<String, P> {

  public LbFilter() {
  }

  /**
   * Filters requests by checking for the token cookie and authorization header,
   * and authenticates the user using the filter's authenticator.
   *
   * @param requestContext
   * @throws IOException
   */
  @Override
  public void filter(final ContainerRequestContext requestContext) throws WebApplicationException {

    // Checks for cookie, if not find then search for authorization header
    try {
      String idToken = Optional
          .ofNullable(requestContext.getCookies().get(SessionCookie.OAUTH_ID_TOKEN))
          .map(Cookie::getValue)
          .orElse(getToken(requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));

      if (!authenticate(requestContext, idToken, SecurityContext.BASIC_AUTH)) {
        throw new Exception();
      }

    } catch (Exception e) {
      log.debug("Error while filtering request for authentication", e);
      throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
    }

  }


  /**
   * Parses a value of the `Authorization` header in the form of `Bearer a892bf3e284da9bb40648ab10`.
   *
   * @param header the value of the `Authorization` header
   * @return a token
   */
  @Nullable
  private String getToken(String header) {
    if (header == null) {
      return null;
    }

    final int space = header.indexOf(' ');
    if (space <= 0) {
      return null;
    }

    final String method = header.substring(0, space);
    if (!prefix.equalsIgnoreCase(method)) {
      return null;
    }

    return header.substring(space + 1);
  }

  public static class Builder<P extends Principal>
      extends AuthFilterBuilder<String, P, LbFilter<P>> {

    @Override
    protected LbFilter<P> newInstance() {
      return new LbFilter<>();
    }

  }

}
