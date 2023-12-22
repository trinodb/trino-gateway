package io.trino.gateway.ha.security;

import io.dropwizard.auth.Authorizer;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LbAuthorizer
        implements Authorizer<LbPrincipal>
{
    private static final Logger log = LoggerFactory.getLogger(LbAuthorizer.class);
    private final AuthorizationConfiguration configuration;

    public LbAuthorizer(AuthorizationConfiguration configuration)
    {
        this.configuration = configuration;
    }

    @Override
    public boolean authorize(LbPrincipal principal,
            String role,
            @Nullable ContainerRequestContext ctx)
    {
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
