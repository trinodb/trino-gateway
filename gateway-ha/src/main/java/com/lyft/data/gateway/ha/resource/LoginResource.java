package com.lyft.data.gateway.ha.resource;

import com.google.inject.Inject;
import com.lyft.data.gateway.ha.security.LbFormAuthManager;
import com.lyft.data.gateway.ha.security.LbOAuthManager;
import com.lyft.data.gateway.ha.security.SessionCookie;
import io.dropwizard.views.View;
import io.trino.jdbc.$internal.javax.annotation.Nullable;
import java.nio.charset.Charset;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;


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
  @Path("login_form")
  public Response processLoginForm(@FormParam("username") String userName,
                                   @FormParam("password") String password) {
    if (formAuthManager == null) {
      throw new WebApplicationException("Form authentication is not setup");
    }
    return formAuthManager.processLoginForm(userName, password);
  }

  @GET
  @Path("login")
  @Produces(MediaType.TEXT_HTML)
  public LoginResource.LoginForm loginFormUi() {
    if (formAuthManager == null) {
      throw new WebApplicationException("Form authentication is not setup");
    }

    return new LoginResource.LoginForm("/template/login-form.ftl");
  }

  @Path("logout")
  @GET
  public Response logOut() {
    return SessionCookie.logOut();
  }

  public static class LoginForm extends View {
    protected LoginForm(String templateName) {
      super(templateName, Charset.defaultCharset());
    }
  }


}
