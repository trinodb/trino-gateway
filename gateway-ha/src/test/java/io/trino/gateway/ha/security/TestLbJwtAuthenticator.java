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
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.security.util.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
final class TestLbJwtAuthenticator
{
    private static final String TEST_USER = "test-user";
    private static final String TEST_TOKEN = "valid-jwt-token";
    private static final String PRINCIPAL_FIELD = "sub";

    @Mock
    private LbJwtManager jwtManager;

    @Mock
    private AuthorizationManager authorizationManager;

    @Mock
    private Claim principalClaim;

    private LbJwtAuthenticator authenticator;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);

        // Setup default user mapping behavior (no transformation)
        when(jwtManager.getUserMappingPattern()).thenReturn(Optional.empty());
        when(jwtManager.getUserMappingFile()).thenReturn(Optional.empty());

        authenticator = new LbJwtAuthenticator(jwtManager, authorizationManager);

        // Default behavior for principal field
        when(jwtManager.getPrincipalField()).thenReturn(PRINCIPAL_FIELD);
        when(principalClaim.asString()).thenReturn(TEST_USER);
    }

    @Test
    void testAuthenticateWithValidTokenAndAuthorizationManager()
            throws AuthenticationException
    {
        // Setup JWT claims without roles (since roles are no longer extracted)
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(authorizationManager.getPrivileges(TEST_USER)).thenReturn(Optional.of("admin_user"));

        Optional<LbPrincipal> result = authenticator.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_USER);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("admin_user"));
    }

    @Test
    void testAuthenticateWithValidTokenAndAuthorizationManagerDifferentPrivileges()
            throws AuthenticationException
    {
        // Setup JWT claims without roles (since roles are no longer extracted)
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(authorizationManager.getPrivileges(TEST_USER)).thenReturn(Optional.of("developers_qa"));

        Optional<LbPrincipal> result = authenticator.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_USER);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("developers_qa"));
    }

    @Test
    void testAuthenticateWithValidTokenAndAuthorizationManagerPermissions()
            throws AuthenticationException
    {
        // Setup JWT claims without roles (since roles are no longer extracted)
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(authorizationManager.getPrivileges(TEST_USER)).thenReturn(Optional.of("READ_PERMISSION_WRITE_PERMISSION"));

        Optional<LbPrincipal> result = authenticator.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_USER);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("READ_PERMISSION_WRITE_PERMISSION"));
    }

    @Test
    void testAuthenticateWithValidTokenNoRoles()
            throws AuthenticationException
    {
        // Setup JWT claims without roles
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(authorizationManager.getPrivileges(TEST_USER)).thenReturn(Optional.of("db_privileges"));

        Optional<LbPrincipal> result = authenticator.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_USER);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("db_privileges"));
    }

    @Test
    void testAuthenticateWithValidTokenNoRolesNoPrivileges()
            throws AuthenticationException
    {
        // Setup JWT claims without roles and no privileges from authorization manager
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(authorizationManager.getPrivileges(TEST_USER)).thenReturn(Optional.empty());

        Optional<LbPrincipal> result = authenticator.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(TEST_USER);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.empty());
    }

    @Test
    void testAuthenticateWithInvalidToken()
    {
        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(TEST_TOKEN))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("JWT token verification failed");
    }

    @Test
    void testAuthenticateWithMissingPrincipalField()
    {
        // Setup JWT claims without the required principal field
        Map<String, Claim> claims = ImmutableMap.of("other_field", principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));

        assertThatThrownBy(() -> authenticator.authenticate(TEST_TOKEN))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Principal field does not exist in JWT token");
    }

    @Test
    void testAuthenticateWithNullPrincipalClaim()
    {
        // Setup JWT claims with null principal field value
        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(principalClaim.asString()).thenReturn(null);

        assertThatThrownBy(() -> authenticator.authenticate(TEST_TOKEN))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAuthenticateWithUserMappingPattern()
            throws AuthenticationException
    {
        // Test with custom user mapping pattern to remove domain
        String originalUser = "john.doe@company.com";
        String expectedMappedUser = "john.doe";

        // Create authenticator with user mapping pattern
        when(jwtManager.getUserMappingPattern()).thenReturn(Optional.of("(.*)@.*"));
        when(jwtManager.getUserMappingFile()).thenReturn(Optional.empty());
        LbJwtAuthenticator authenticatorWithMapping = new LbJwtAuthenticator(jwtManager, authorizationManager);

        Map<String, Claim> claims = ImmutableMap.of(PRINCIPAL_FIELD, principalClaim);

        when(jwtManager.getClaimsFromToken(TEST_TOKEN)).thenReturn(Optional.of(claims));
        when(principalClaim.asString()).thenReturn(originalUser);
        when(authorizationManager.getPrivileges(expectedMappedUser)).thenReturn(Optional.of("user_privileges"));

        Optional<LbPrincipal> result = authenticatorWithMapping.authenticate(TEST_TOKEN);

        assertThat(result).isPresent();
        LbPrincipal principal = result.orElseThrow();
        assertThat(principal.getName()).isEqualTo(expectedMappedUser);
        assertThat(principal.getMemberOf()).isEqualTo(Optional.of("user_privileges"));
    }
}
