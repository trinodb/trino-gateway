package io.trino.gateway.ha.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth0.jwt.interfaces.Claim;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.SecurityContext;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestLbFilter {

  private static final String USER = "username";
  private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
  private static final String ID_TOKEN = "TOKEN";

  private LbOAuthManager oauthManager;
  private AuthorizationManager authorizationManager;
  private ContainerRequestContext requestContext;

  @BeforeAll
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
    Mockito.when(oauthManager.getUserIdField()).thenReturn("sub");

    // Set authorization manager with membership
    authorizationManager = Mockito.mock(AuthorizationManager.class);
    Mockito
        .when(authorizationManager.searchMemberOf(USER))
        .thenReturn(MEMBER_OF);
    Mockito
            .when(authorizationManager.getPrivileges(USER))
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
    assertTrue(secContextCaptor.getValue().isUserInRole("USER"));
    assertFalse(secContextCaptor.getValue().isUserInRole("ADMIN"));
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
    Mockito.verify(requestContext, Mockito.atLeast(1))
            .setSecurityContext(secContextCaptor.capture());

    // Checks authorization for authenticated principal
    assertTrue(secContextCaptor.getValue().isUserInRole("USER"));
    assertTrue(secContextCaptor.getValue().isUserInRole("ADMIN"));

  }

  @Test
  public void testMissingAuthenticationToken() throws WebApplicationException {
    assertThrows(WebApplicationException.class, () -> {

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
    });
  }
}
