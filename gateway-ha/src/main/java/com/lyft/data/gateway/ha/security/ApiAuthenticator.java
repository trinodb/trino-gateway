package com.lyft.data.gateway.ha.security;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiAuthenticator implements Authenticator<BasicCredentials, LbPrincipal> {
  private final LbFormAuthManager formAuthManager;
  private final AuthorizationManager authorizationManager;

  public ApiAuthenticator(LbFormAuthManager formAuthManager,
                          AuthorizationManager authorizationManager) {
    this.formAuthManager = formAuthManager;
    this.authorizationManager = authorizationManager;
  }

  @Override
  public Optional<LbPrincipal> authenticate(BasicCredentials credentials)
      throws AuthenticationException {
    if (formAuthManager.authenticate(credentials)) {
      return Optional.of(new LbPrincipal(credentials.getUsername(),
          authorizationManager.getPrivileges(credentials.getUsername())));
    }
    return Optional.empty();
  }
}