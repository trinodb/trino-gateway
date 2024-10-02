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
package io.trino.gateway.ha.config;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.gateway.ha.util.Constants.V1_STATEMENT_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHaGatewayConfiguration
{
    @Test
    void testDefaultStatementPaths()
    {
        HaGatewayConfiguration haGatewayConfiguration = new HaGatewayConfiguration();
        assertThat(haGatewayConfiguration.getStatementPaths()).containsOnly(V1_STATEMENT_PATH);
    }

    @Test
    void testAdditionalPath()
    {
        HaGatewayConfiguration haGatewayConfiguration = new HaGatewayConfiguration();
        List<String> additionalPaths = ImmutableList.of("/ui/api/insights/ide/statement/", "/v2/statement");
        haGatewayConfiguration.setAdditionalStatementPaths(additionalPaths);
        assertThat(haGatewayConfiguration.getStatementPaths())
                .containsAll(ImmutableList.of(V1_STATEMENT_PATH, "/ui/api/insights/ide/statement", "/v2/statement"));
    }

    @Test
    void testValidation()
    {
        HaGatewayConfiguration haGatewayConfiguration = new HaGatewayConfiguration();
        assertThatThrownBy(() -> haGatewayConfiguration.setAdditionalStatementPaths(ImmutableList.of("relative/path/")))
                .isInstanceOf(HaGatewayConfiguration.HaGatewayConfigurationException.class)
                .hasMessage("Statement paths must be absolute");

        assertThatThrownBy(() -> haGatewayConfiguration.setAdditionalStatementPaths(ImmutableList.of("/v1/statement/additional")))
                .isInstanceOf(HaGatewayConfiguration.HaGatewayConfigurationException.class)
                .hasMessage("Statement paths cannot be prefixes of other statement paths");

        assertThatThrownBy(() -> haGatewayConfiguration.setAdditionalStatementPaths(ImmutableList.of("/api/v2", "/api/v2/statement")))
                .isInstanceOf(HaGatewayConfiguration.HaGatewayConfigurationException.class)
                .hasMessage("Statement paths cannot be prefixes of other statement paths");
    }
}
