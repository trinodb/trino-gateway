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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestLbFilter
{
    private static final String USER = "username";
    private static final Optional<String> MEMBER_OF = Optional.of("PVFX_DATA_31");
    private static final String ID_TOKEN = "TOKEN";

    private LbOAuthManager oauthManager;
    private AuthorizationManager authorizationManager;
    private ContainerRequestContext requestContext;

    @BeforeAll
    public void setup()
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
    public void testSuccessfulCookieAuthentication()
            throws Exception
    {
        AuthorizationConfiguration configuration = new AuthorizationConfiguration();
        configuration.setAdmin("NO_MEMBER");
        configuration.setUser(MEMBER_OF.orElseThrow());

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
        assertThat(secContextCaptor.getValue().isUserInRole("USER")).isTrue();
        assertThat(secContextCaptor.getValue().isUserInRole("ADMIN")).isFalse();
    }

    @Test
    public void testSuccessfulHeaderAuthentication()
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
        assertThat(secContextCaptor.getValue().isUserInRole("USER")).isTrue();
        assertThat(secContextCaptor.getValue().isUserInRole("ADMIN")).isTrue();
    }

    @Test
    public void testMissingAuthenticationToken()
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
            LbFilter<LbPrincipal> lbFilter = new LbFilter.Builder<LbPrincipal>()
                    .setAuthenticator(authenticator)
                    .setAuthorizer(authorizer)
                    .setPrefix("Bearer")
                    .buildAuthFilter();

            // Exception is thrown when the authentication fails
            lbFilter.filter(requestContext);
        }).isInstanceOf(WebApplicationException.class);
    }
}
