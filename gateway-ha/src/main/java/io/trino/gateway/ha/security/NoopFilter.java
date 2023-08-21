package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthFilter;
import java.io.IOException;
import java.security.Principal;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.SecurityContext;

@Priority(Priorities.AUTHENTICATION)
public class NoopFilter<P extends Principal> extends AuthFilter<String, P> {


  public NoopFilter() {
  }

  @Override
  public void filter(final ContainerRequestContext requestContext) throws IOException {
    try {
      if (!authenticate(requestContext, "", SecurityContext.BASIC_AUTH)) {
        throw new Exception();
      }
    } catch (Exception e) {
      throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
    }
  }


  public static class Builder<P extends Principal>
      extends AuthFilterBuilder<String, P, NoopFilter<P>> {

    @Override
    protected NoopFilter<P> newInstance() {
      return new NoopFilter<>();
    }

  }

}