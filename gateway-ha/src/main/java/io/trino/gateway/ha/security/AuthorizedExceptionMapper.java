package io.trino.gateway.ha.security;

import io.trino.gateway.ha.domain.R;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.internal.LocalizationMessages;

@Provider
public class AuthorizedExceptionMapper implements ExceptionMapper<ForbiddenException> {
  @Override
  public Response toResponse(ForbiddenException exception) {
    if (exception.getMessage().equals(LocalizationMessages.USER_NOT_AUTHORIZED())) {
      return Response.ok(R.fail(Response.Status.UNAUTHORIZED)).build();
    }
    return exception.getResponse();
  }
}