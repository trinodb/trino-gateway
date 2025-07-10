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
import io.trino.gateway.ha.config.AuthorizationConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class TestLbFilter
{
    private static final String USER = "username";
    private static final String MEMBER_OF = "PVFX_DATA_31";
    private static final String ID_TOKEN = "TOKEN";

    private LbOAuthManager oauthManager;
    private ContainerRequestContext requestContext;
    private LbAuthorizer authorizer;

    @BeforeAll
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

        // Request context for the auth filter
        requestContext = Mockito.mock(ContainerRequestContext.class);
        authorizer = new LbAuthorizer();
    }

    @Test
    void testSuccessfulCookieAuthentication()
            throws Exception
    {
        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin("NO_MEMBER");
        configuration.setUser(MEMBER_OF);

        Mockito
                .when(requestContext.getCookies())
                .thenReturn(
                        Map.of(SessionCookie.OAUTH_ID_TOKEN,
                                new Cookie.Builder(SessionCookie.OAUTH_ID_TOKEN).value(ID_TOKEN).build()));
        Mockito
                .when(requestContext.getHeaders())
                .thenReturn(new MultivaluedHashMap());

        AuthorizationManager authorizationManager = getAuthorizationManager(configuration);
        LbAuthenticator authenticator = new LbAuthenticator(
                oauthManager,
                authorizationManager);

        LbFilter lbFilter = new LbFilter(
                authenticator,
                authorizer,
                "Bearer",
                new LbUnauthorizedHandler(""));
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
        configuration.setAdmin(MEMBER_OF);
        configuration.setUser(MEMBER_OF);

        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.addFirst(HttpHeaders.AUTHORIZATION, "Bearer " + ID_TOKEN);

        Mockito
                .when(requestContext.getCookies())
                .thenReturn(Map.of());
        Mockito
                .when(requestContext.getHeaders())
                .thenReturn(headers);

        AuthorizationManager authorizationManager = getAuthorizationManager(configuration);
        LbAuthenticator authenticator = new LbAuthenticator(
                oauthManager,
                authorizationManager);
        LbFilter lbFilter = new LbFilter(
                authenticator,
                authorizer,
                "Bearer",
                new LbUnauthorizedHandler(""));
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

            AuthorizationManager authorizationManager = getAuthorizationManager(configuration);
            LbAuthenticator authenticator = new LbAuthenticator(
                    oauthManager,
                    authorizationManager);
            LbFilter lbFilter = new LbFilter(
                    authenticator,
                    authorizer,
                    "Bearer",
                    new LbUnauthorizedHandler(""));

            // Exception is thrown when the authentication fails
            lbFilter.filter(requestContext);
        }).isInstanceOf(WebApplicationException.class);
    }

    private AuthorizationManager getAuthorizationManager(AuthorizationConfiguration configuration)
    {
        LbLdapClient lbLdapClient = Mockito.mock(LbLdapClient.class);
        Mockito.when(lbLdapClient.getMemberOf(USER)).thenReturn(MEMBER_OF);

        return new AuthorizationManager(new HashMap<>(), lbLdapClient, configuration);
    }
}
