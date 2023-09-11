package io.trino.gateway.ha.security;

import io.dropwizard.auth.Authorizer;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LbAuthorizer implements Authorizer<LbPrincipal> {

  private final AuthorizationConfiguration configuration;

  public LbAuthorizer(AuthorizationConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public boolean authorize(LbPrincipal principal, String role) {
    switch (role) {
      case "ADMIN":
        log.info("User '{}' with memberOf({}) was identified as ADMIN({})",
                principal.getName(), principal.getMemberOf(), configuration.getAdmin());
        return principal.getMemberOf()
            .filter(m -> m.matches(configuration.getAdmin()))
            .isPresent();
      case "USER":
        log.info("User '{}' with memberOf({}) identified as USER({})",
                principal.getName(), principal.getMemberOf(), configuration.getUser());
        return principal.getMemberOf()
            .filter(m -> m.matches(configuration.getUser()))
            .isPresent();
      case "API":
        log.info("User '{}' with memberOf({}) identified as API({})",
                principal.getName(), principal.getMemberOf(), configuration.getApi());
        return principal.getMemberOf()
            .filter(m -> m.matches(configuration.getApi()))
            .isPresent();
      default:
        log.warn("User '{}' with role {} has no regex match based on ldap search",
            principal.getName(), role);
        return false;

    }

  }
}
