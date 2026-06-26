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

import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.UIConfiguration;
import io.trino.gateway.ha.domain.TableData;
import io.trino.gateway.ha.domain.request.QueryHistoryRequest;
import io.trino.gateway.ha.router.BackendStateManager;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingRulesManager;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class TestGatewayWebAppResource
{
    @ParameterizedTest
    @CsvSource({
            "false, false, alice",
            "false, true, bob",
            "true, false, bob",
            "true, true, bob",
    })
    void testUserFilterForQueryHistory(boolean isAdmin, boolean allowNonAdminToViewAllQueryHistory, String expectedUser)
    {
        QueryHistoryManager queryHistoryManager = mock(QueryHistoryManager.class);
        when(queryHistoryManager.findQueryHistory(any())).thenReturn(new TableData<>(List.of(), 0));

        SecurityContext securityContext = createSecurityContext(isAdmin, "alice");
        GatewayWebAppResource resource = createResource(allowNonAdminToViewAllQueryHistory, queryHistoryManager);
        QueryHistoryRequest request = new QueryHistoryRequest(2, 20, "bob", "ext", "query-id", "source-a");

        assertThat(resource.findQueryHistory(request, securityContext).getStatus()).isEqualTo(200);

        QueryHistoryRequest queryRequest = captureFindQueryHistoryRequest(queryHistoryManager);
        assertThat(queryRequest.page()).isEqualTo(2);
        assertThat(queryRequest.size()).isEqualTo(20);
        assertThat(queryRequest.user()).isEqualTo(expectedUser);
        assertThat(queryRequest.externalUrl()).isEqualTo("ext");
        assertThat(queryRequest.queryId()).isEqualTo("query-id");
        assertThat(queryRequest.source()).isEqualTo("source-a");
    }

    private static QueryHistoryRequest captureFindQueryHistoryRequest(QueryHistoryManager queryHistoryManager)
    {
        ArgumentCaptor<QueryHistoryRequest> requestCaptor = ArgumentCaptor.forClass(QueryHistoryRequest.class);
        verify(queryHistoryManager).findQueryHistory(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private static GatewayWebAppResource createResource(boolean isAllowNonAdminToViewAllQueryHistory, QueryHistoryManager queryHistoryManager)
    {
        HaGatewayConfiguration configuration = new HaGatewayConfiguration();
        UIConfiguration uiConfiguration = new UIConfiguration();
        uiConfiguration.setAllowNonAdminToViewAllQueryHistory(isAllowNonAdminToViewAllQueryHistory);
        configuration.setUiConfiguration(uiConfiguration);

        return new GatewayWebAppResource(
                mock(GatewayBackendManager.class),
                queryHistoryManager,
                mock(BackendStateManager.class),
                mock(RoutingRulesManager.class),
                configuration);
    }

    private static SecurityContext createSecurityContext(boolean isAdmin, String username)
    {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.isUserInRole("ADMIN")).thenReturn(isAdmin);
        Principal principal = () -> username;
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        return securityContext;
    }
}
