package io.trino.gateway.ha.security;

import io.dropwizard.auth.Authorizer;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LbAuthorizer implements Authorizer<LbPrincipal> {

  private final AuthorizationConfiguration configuration;

  public LbAuthorizer(AuthorizationConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public boolean authorize(LbPrincipal principal,
                           String role,
                           @Nullable ContainerRequestContext ctx) {
    switch (role) {
      case "ADMIN":
        log.info("User {} identified as ADMIN", principal.getName());
        return principal.getMemberOf()
            .filter(m -> m.contains(configuration.getAdmin()))
            .isPresent();
      case "USER":
        log.info("User {} identified as USER", principal.getName());
        return principal.getMemberOf()
            .filter(m -> m.contains(configuration.getUser()))
            .isPresent();
      case "API":
        log.info("User {} identified as USER", principal.getName());
        return principal.getMemberOf()
            .filter(m -> m.contains(configuration.getApi()))
            .isPresent();
      default:
        log.warn("User {} is neither member of {} or of {} based on ldap search",
            principal.getName(),
            configuration.getAdmin(),
            configuration.getUser());
        return false;

    }

  }
}
