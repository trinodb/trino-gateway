package io.trino.gateway.ha.security;

import io.dropwizard.auth.AuthFilter;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.security.Principal;

@Priority(Priorities.AUTHENTICATION)
public class NoopFilter<P extends Principal>
        extends AuthFilter<String, P>
{
    public NoopFilter()
    {
    }

    @Override
    public void filter(final ContainerRequestContext requestContext)
            throws IOException
    {
        try {
            if (!authenticate(requestContext, "", SecurityContext.BASIC_AUTH)) {
                throw new Exception();
            }
        }
        catch (Exception e) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    public static class Builder<P extends Principal>
            extends AuthFilterBuilder<String, P, NoopFilter<P>>
    {
        @Override
        protected NoopFilter<P> newInstance()
        {
            return new NoopFilter<>();
        }
    }
}
