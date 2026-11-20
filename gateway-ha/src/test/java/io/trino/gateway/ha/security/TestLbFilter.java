/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.security;

import com.auth0.jwt.interfaces.Claim;
import io.trino.gateway.ha.config.AuthenticationType;
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestLbFilter
{
    private static final String USER = "username";
    private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
    private static final String ID_TOKEN = "TOKEN";

    private LbOAuthManager oauthManager;
    private AuthorizationManager authorizationManager;
    private ContainerRequestContext requestContext;

    @BeforeEach
    void setup()
            throws Exception
    {
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
                .when(authorizationManager.getPrivileges(USER))
                .thenReturn(MEMBER_OF);

        // Request context for the auth filter
        requestContext = Mockito.mock(ContainerRequestContext.class);
    }

    @Test
    void testSuccessfulCookieAuthentication()
            throws Exception
    {
        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin("NO_MEMBER");
        configuration.setUser(MEMBER_OF.orElseThrow());

        Mockito
                .when(requestContext.getCookies())
                .thenReturn(
                        Map.of(
                                SessionCookie.OAUTH_ID_TOKEN, new Cookie.Builder(SessionCookie.OAUTH_ID_TOKEN).value(ID_TOKEN).build()));
        Mockito
                .when(requestContext.getHeaders())
                .thenReturn(new MultivaluedHashMap());

        LbAuthenticator authenticator = new LbAuthenticator(
                oauthManager,
                authorizationManager);

        LbAuthorizer authorizer = new LbAuthorizer(configuration);
        LbFilter lbFilter = new LbFilter(
                authenticator,
                authorizer,
                "Bearer",
                new LbUnauthorizedHandler(AuthenticationType.FORM));
        ArgumentCaptor<SecurityContext> secContextCaptor = ArgumentCaptor
                .forClass(SecurityContext.class);

        // No exception is thrown when the authentication is successful
        lbFilter.filter(requestContext);

        // SecurityContext must be set with the right authorizer for authenticated user
        Mockito.verify(requestContext, Mockito.times(1))
                .setSecurityContext(secContextCaptor.capture());

        // Checks authorization for authenticated principal
        assertThat(secContextCaptor.getValue().isUserInRole("USER")).isTrue();
        assertThat(secContextCaptor.getValue().isUserInRole("ADMIN")).isFalse();
    }

    @Test
    void testSuccessfulHeaderAuthentication()
            throws Exception
    {
        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin(MEMBER_OF.orElseThrow());
        configuration.setUser(MEMBER_OF.orElseThrow());

        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addFirst(HttpHeaders.AUTHORIZATION, "Bearer " + ID_TOKEN);

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
        LbFilter lbFilter = new LbFilter(
                authenticator,
                authorizer,
                "Bearer",
                new LbUnauthorizedHandler(AuthenticationType.FORM));
        ArgumentCaptor<SecurityContext> secContextCaptor = ArgumentCaptor
                .forClass(SecurityContext.class);

        // No exception is thrown when the authentication is successful
        lbFilter.filter(requestContext);

        // SecurityContext must be set with the right authorizer at authentication
        Mockito.verify(requestContext, Mockito.atLeast(1))
                .setSecurityContext(secContextCaptor.capture());

        // Checks authorization for authenticated principal
        assertThat(secContextCaptor.getValue().isUserInRole("USER")).isTrue();
        assertThat(secContextCaptor.getValue().isUserInRole("ADMIN")).isTrue();
    }

    @Test
    void testHeaderTakesPrecedenceOverCookie()
            throws Exception
    {
        String headerUser = "header_user";
        String headerToken = "HEADER_TOKEN";
        Claim headerClaim = Mockito.mock(Claim.class);
        Mockito
                .when(headerClaim.toString())
                .thenReturn(headerUser);
        Mockito
                .when(oauthManager.getClaimsFromIdToken(headerToken))
                .thenReturn(Optional.of(Map.of("sub", headerClaim)));
        Mockito
                .when(authorizationManager.getPrivileges(headerUser))
                .thenReturn(Optional.of("OTHER_MEMBER"));

        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin(MEMBER_OF.orElseThrow());
        configuration.setUser(MEMBER_OF.orElseThrow());

        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addFirst(HttpHeaders.AUTHORIZATION, "Bearer " + headerToken);

        Mockito
                .when(requestContext.getCookies())
                .thenReturn(
                        Map.of(
                                SessionCookie.OAUTH_ID_TOKEN, new Cookie.Builder(SessionCookie.OAUTH_ID_TOKEN).value(ID_TOKEN).build()));
        Mockito
                .when(requestContext.getHeaders())
                .thenReturn(headers);
        LbFilter lbFilter = new LbFilter(
                new LbAuthenticator(oauthManager, authorizationManager),
                new LbAuthorizer(configuration),
                "Bearer",
                new LbUnauthorizedHandler(AuthenticationType.FORM));
        ArgumentCaptor<SecurityContext> secContextCaptor = ArgumentCaptor
                .forClass(SecurityContext.class);

        lbFilter.filter(requestContext);

        Mockito.verify(requestContext, Mockito.times(1))
                .setSecurityContext(secContextCaptor.capture());

        // The user from the Authorization header is authenticated, not the user from the cookie
        assertThat(secContextCaptor.getValue().getUserPrincipal().getName()).isEqualTo(headerUser);
        assertThat(secContextCaptor.getValue().isUserInRole("ADMIN")).isFalse();
    }

    @Test
    void testInvalidBearerTokenWithValidCookie()
    {
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addFirst(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN");

        // The cookie does not authenticate a request with an invalid token in the Authorization header
        assertThatThrownBy(() -> createFilter().filter(requestWithCookie(headers)))
                .isInstanceOf(WebApplicationException.class);
        Mockito.verify(requestContext, Mockito.never()).setSecurityContext(Mockito.any());
    }

    @Test
    void testOtherAuthorizationSchemeWithValidCookie()
    {
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addFirst(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString("basic_user:password".getBytes(UTF_8)));

        // The cookie does not authenticate the request, so that the next filter in the chain handles the Basic credentials
        assertThatThrownBy(() -> createFilter().filter(requestWithCookie(headers)))
                .isInstanceOf(WebApplicationException.class);
        Mockito.verify(requestContext, Mockito.never()).setSecurityContext(Mockito.any());
        // Without a bearer token, nothing is decoded, so no token error is logged for Basic requests
        Mockito.verify(oauthManager, Mockito.never()).getClaimsFromIdToken(Mockito.any());
    }

    private LbFilter createFilter()
    {
        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin(MEMBER_OF.orElseThrow());
        configuration.setUser(MEMBER_OF.orElseThrow());
        return new LbFilter(
                new LbAuthenticator(oauthManager, authorizationManager),
                new LbAuthorizer(configuration),
                "Bearer",
                new LbUnauthorizedHandler(AuthenticationType.FORM));
    }

    private ContainerRequestContext requestWithCookie(MultivaluedHashMap<String, String> headers)
    {
        Mockito
                .when(requestContext.getCookies())
                .thenReturn(
                        Map.of(
                                SessionCookie.OAUTH_ID_TOKEN, new Cookie.Builder(SessionCookie.OAUTH_ID_TOKEN).value(ID_TOKEN).build()));
        Mockito
                .when(requestContext.getHeaders())
                .thenReturn(headers);
        return requestContext;
    }

    @Test
    void testMissingAuthenticationToken()
            throws WebApplicationException
    {
        assertThatThrownBy(() -> {
            AuthorizationConfiguration configuration = new AuthorizationConfiguration();

            MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();

            Mockito.when(requestContext.getCookies())
                    .thenReturn(Map.of());
            Mockito.when(requestContext.getHeaders())
                    .thenReturn(headers);
            LbAuthenticator authenticator = new LbAuthenticator(
                    oauthManager,
                    authorizationManager);
            LbAuthorizer authorizer = new LbAuthorizer(configuration);
            LbFilter lbFilter = new LbFilter(
                    authenticator,
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(AuthenticationType.FORM));

            // Exception is thrown when the authentication fails
            lbFilter.filter(requestContext);
        }).isInstanceOf(WebApplicationException.class);
        Mockito.verify(oauthManager, Mockito.never()).getClaimsFromIdToken(Mockito.any());
    }
}
