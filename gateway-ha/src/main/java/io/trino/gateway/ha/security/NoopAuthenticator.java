package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import java.util.Optional;

public class NoopAuthenticator implements Authenticator<String,
    LbPrincipal> {
  @Override
  public Optional<LbPrincipal> authenticate(String credentials) throws AuthenticationException {
    return Optional.of(new LbPrincipal("user", Optional.empty()));
  }
}
