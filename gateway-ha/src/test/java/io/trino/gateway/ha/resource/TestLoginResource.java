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
package io.trino.gateway.ha.resource;

import io.trino.gateway.ha.config.AuthenticationConfiguration;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.JwtConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.security.LbJwtManager;
import io.trino.gateway.ha.security.LbPrincipal;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TestLoginResource
{
    @Test
    void testJwtPagePermissionsAreReturnedFromUserinfo()
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        AuthenticationConfiguration authentication = new AuthenticationConfiguration();
        authentication.setDefaultType(List.of("jwt"));
        authentication.setJwt(new JwtConfiguration());
        configuration.setAuthentication(authentication);

        LbJwtManager jwtManager = mock(LbJwtManager.class);
        when(jwtManager.processPagePermissions(List.of("USER"))).thenReturn(List.of("dashboard", "history"));

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserPrincipal()).thenReturn(new LbPrincipal("alice", Optional.of("USER")));
        when(securityContext.isUserInRole("USER")).thenReturn(true);

        LoginResource resource = new LoginResource(configuration, null, null, jwtManager);
        try (Response response = resource.restUserinfo(securityContext)) {
            Result<?> result = (Result<?>) response.getEntity();
            assertThat(result.getData())
                    .isInstanceOfSatisfying(Map.class, data -> assertThat(data.get("permissions"))
                            .isEqualTo(List.of("dashboard", "history")));
        }
    }
}
