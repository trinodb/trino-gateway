package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.dropwizard.views.common.View;
import io.trino.gateway.ha.domain.R;
import io.trino.gateway.ha.domain.request.RestLoginRequest;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbPrincipal;
import io.trino.gateway.ha.security.SessionCookie;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.Map;


@Slf4j
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class LoginResource {

  @Inject
  @Nullable
  private LbOAuthManager oauthManager;
  @Inject
  @Nullable
  private LbFormAuthManager formAuthManager;

  @Path("sso")
  @GET
  public Response login() {
    if (oauthManager == null) {
      throw new WebApplicationException("OAuth configuration is not setup");
    }
    return oauthManager.getAuthorizationCode();
  }

  @Path("oidc/callback")
  @GET
  public Response callback(@QueryParam("code") String code) {
    if (oauthManager == null) {
      throw new WebApplicationException("OAuth configuration is not setup");
    }
    return oauthManager.exchangeCodeForToken(code, "/");
  }

  @POST
  @Path("/rest/login")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response processRESTLogin(RestLoginRequest loginForm) {
    if (formAuthManager == null) {
      throw new WebApplicationException("Form authentication is not setup");
    }
    R<?> r = formAuthManager.processRESTLogin(loginForm);
    return Response.ok(r).build();
  }

  @POST
  @RolesAllowed({"USER"})
  @Path("/rest/userinfo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response restUserinfo(@Context SecurityContext securityContext) {
    if (formAuthManager == null) {
      throw new WebApplicationException("Form authentication is not setup");
    }
    LbPrincipal principal = (LbPrincipal) securityContext.getUserPrincipal();
    String[] roles = principal.getMemberOf().orElse("").split("_");
    String[] pagePermissions = formAuthManager.processPagePermissions(roles);
    Map<String, Object> resMap = Map.of(
            "roles", roles,
            "permissions", pagePermissions,
            "userId", principal.getName(),
            "userName", principal.getName()
    );
    return Response.ok(R.ok(resMap)).build();
  }


}
