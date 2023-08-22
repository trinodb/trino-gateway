package io.trino.gateway.ha.security;

import io.dropwizard.auth.Authorizer;

public class NoopAuthorizer implements Authorizer<LbPrincipal> {
  @Override
  public boolean authorize(LbPrincipal principal, String role) {
    return true;
  }


}
