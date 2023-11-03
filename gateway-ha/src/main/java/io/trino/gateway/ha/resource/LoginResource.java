package io.trino.gateway.ha.resource;

import com.google.inject.Inject;
import io.dropwizard.views.common.View;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.SessionCookie;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
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
