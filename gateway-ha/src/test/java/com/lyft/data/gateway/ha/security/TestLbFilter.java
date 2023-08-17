package com.lyft.data.gateway.ha.security;

import com.auth0.jwt.interfaces.Claim;
import com.lyft.data.gateway.ha.config.AuthorizationConfiguration;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;


@Slf4j
public class TestLbFilter {

  private static final String USER = "username";
  private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
  private static final String ID_TOKEN = "TOKEN";

  private LbOAuthManager oauthManager;
  private AuthorizationManager authorizationManager;
  private ContainerRequestContext requestContext;

  @BeforeClass(alwaysRun = true)
  public void setup() throws Exception {

    // Set authentication manager mock with 'sub' claim
    oauthManager = Mockito.mock(LbOAuthManager.class);
    Claim claim = Mockito.mock(Claim.class);
    Mockito
        .when(claim.toString())
        .thenReturn(USER);
    Mockito
        .when(oauthManager.getClaimsFromIdToken(ID_TOKEN))
        .thenReturn(Optional.of(Map.of("sub", claim)));

    // Set authorization manager with membership
    authorizationManager = Mockito.mock(AuthorizationManager.class);
    Mockito
        .when(authorizationManager.searchMemberOf(USER))
        .thenReturn(MEMBER_OF);

    // Request context for the auth filter
    requestContext = Mockito.mock(ContainerRequestContext.class);
  }


  @Test
  public void testSuccessfulCookieAuthentication() throws Exception {

    AuthorizationConfiguration configuration = new AuthorizationConfiguration();
    configuration.setAdmin("NO_MEMBER");
    configuration.setUser(MEMBER_OF.get());

    Mockito
        .when(requestContext.getCookies())
        .thenReturn(
            Map.of(SessionCookie.OAUTH_ID_TOKEN,
                new Cookie(SessionCookie.OAUTH_ID_TOKEN, ID_TOKEN)));
    Mockito
        .when(requestContext.getHeaders())
        .thenReturn(new MultivaluedHashMap());
    LbAuthenticator authenticator = new LbAuthenticator(
        oauthManager,
        authorizationManager);
    LbAuthorizer authorizer = new LbAuthorizer(configuration);
    LbFilter<LbPrincipal> lbFilter = new LbFilter.Builder<LbPrincipal>()
        .setAuthenticator(authenticator)
        .setAuthorizer(authorizer)
        .setPrefix("Bearer")
        .buildAuthFilter();
    ArgumentCaptor<SecurityContext> secContextCaptor = ArgumentCaptor
        .forClass(SecurityContext.class);

    // No exception is thrown when the authentication is successful
    lbFilter.filter(requestContext);

    // SecurityContext must be set with the right authorizer for authenticated user
    Mockito.verify(requestContext, Mockito.times(1))
        .setSecurityContext(secContextCaptor.capture());

    // Checks authorization for authenticated principal
    Assert.assertTrue(secContextCaptor.getValue().isUserInRole("USER"));
    Assert.assertFalse(secContextCaptor.getValue().isUserInRole("ADMIN"));
  }

  @Test
  public void testSuccessfulHeaderAuthentication() throws Exception {
    AuthorizationConfiguration configuration = new AuthorizationConfiguration();
    configuration.setAdmin(MEMBER_OF.get());
    configuration.setUser(MEMBER_OF.get());

    MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
    headers.addFirst(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", ID_TOKEN));

    Mockito
        .when(requestContext.getCookies())
        .thenReturn(Map.of());
    Mockito
        .when(requestContext.getHeaders())
        .thenReturn(headers);
    LbAuthenticator authenticator = new LbAuthenticator(
        oauthManager,
        authorizationManager);
    LbAuthorizer authorizer = new LbAuthorizer(configuration);
    LbFilter<LbPrincipal> lbFilter = new LbFilter.Builder<LbPrincipal>()
        .setAuthenticator(authenticator)
        .setAuthorizer(authorizer)
        .setPrefix("Bearer")
        .buildAuthFilter();
    ArgumentCaptor<SecurityContext> secContextCaptor = ArgumentCaptor
        .forClass(SecurityContext.class);

    // No exception is thrown when the authentication is successful
    lbFilter.filter(requestContext);

    // SecurityContext must be set with the right authorizer at authentication
    Mockito.verify(requestContext, Mockito.times(1)).setSecurityContext(secContextCaptor.capture());

    // Checks authorization for authenticated principal
    Assert.assertTrue(secContextCaptor.getValue().isUserInRole("USER"));
    Assert.assertTrue(secContextCaptor.getValue().isUserInRole("ADMIN"));

  }

  @Test(expected = WebApplicationException.class)
  public void testMissingAuthenticationToken() throws WebApplicationException {
    AuthorizationConfiguration configuration = new AuthorizationConfiguration();

    MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

    Mockito
        .when(requestContext.getCookies())
        .thenReturn(Map.of());
    Mockito
        .when(requestContext.getHeaders())
        .thenReturn(headers);
    LbAuthenticator authenticator = new LbAuthenticator(
        oauthManager,
        authorizationManager);
    LbAuthorizer authorizer = new LbAuthorizer(configuration);
    LbFilter<LbPrincipal> lbFilter = new LbFilter.Builder<LbPrincipal>()
        .setAuthenticator(authenticator)
        .setAuthorizer(authorizer)
        .setPrefix("Bearer")
        .buildAuthFilter();

    // Exception is thrown when the authentication fails
    lbFilter.filter(requestContext);

  }
}
