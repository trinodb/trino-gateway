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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class TestAuthenticationConfiguration
{
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    void testDeprecatedScalarMapsToList()
    {
        AuthenticationConfiguration config = new AuthenticationConfiguration();
        config.setDefaultType("form");
        assertThat(config.getDefaultTypes()).containsExactly("form");
    }

    @Test
    void testDefaultTypesWinsWhenSetAfterScalar()
    {
        AuthenticationConfiguration config = new AuthenticationConfiguration();
        config.setDefaultType("form");
        config.setDefaultTypes(List.of("oauth", "form"));
        assertThat(config.getDefaultTypes()).containsExactly("oauth", "form");
    }

    @Test
    void testDefaultTypesWinsWhenSetBeforeScalar()
    {
        AuthenticationConfiguration config = new AuthenticationConfiguration();
        config.setDefaultTypes(List.of("oauth", "form"));
        config.setDefaultType("form");
        assertThat(config.getDefaultTypes()).containsExactly("oauth", "form");
    }

    @Test
    void testLegacyScalarDefaultTypeDeserializes()
            throws Exception
    {
        // Existing configs using the deprecated scalar must still bind and boot.
        AuthenticationConfiguration config = YAML_MAPPER.readValue("defaultType: \"form\"\n", AuthenticationConfiguration.class);
        assertThat(config.getDefaultTypes()).containsExactly("form");
    }

    @Test
    void testDefaultTypesListDeserializes()
            throws Exception
    {
        AuthenticationConfiguration config = YAML_MAPPER.readValue("defaultTypes: [\"oauth\", \"form\"]\n", AuthenticationConfiguration.class);
        assertThat(config.getDefaultTypes()).containsExactly("oauth", "form");
    }
}
