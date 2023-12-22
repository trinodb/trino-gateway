package io.trino.gateway.ha.security;

import io.dropwizard.auth.UnauthorizedHandler;
import jakarta.ws.rs.core.Response;

import java.net.URI;

public class LbUnauthorizedHandler
        implements UnauthorizedHandler
{
    private final String redirectPath;

    public LbUnauthorizedHandler(String authenticationType)
    {
        if (authenticationType.equals("oauth")) {
            this.redirectPath = "/sso";
        }
        else {
            this.redirectPath = "/login";
        }
    }

    @Override
    public Response buildResponse(String prefix, String realm)
    {
        return Response.status(302).location(URI.create(redirectPath)).build();
    }
}
