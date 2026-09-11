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

import static org.assertj.core.api.Assertions.assertThat;

final class TestAuthenticationConfiguration
{
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    void testScalarDefaultTypeDeserializes()
            throws Exception
    {
        // The single-value (scalar) form (defaultType: "form") must still bind, mapped to a
        // one-element list so existing single-value configurations keep booting unchanged.
        AuthenticationConfiguration config = YAML_MAPPER.readValue("defaultType: \"form\"\n", AuthenticationConfiguration.class);
        assertThat(config.getDefaultType()).containsExactly("form");
    }

    @Test
    void testListDefaultTypeDeserializes()
            throws Exception
    {
        AuthenticationConfiguration config = YAML_MAPPER.readValue("defaultType: [\"oauth\", \"form\"]\n", AuthenticationConfiguration.class);
        assertThat(config.getDefaultType()).containsExactly("oauth", "form");
    }

    @Test
    void testShowFirstTypeOnlyDefaultsFalse()
    {
        AuthenticationConfiguration config = new AuthenticationConfiguration();
        assertThat(config.isShowFirstTypeOnly()).isFalse();
    }

    @Test
    void testShowFirstTypeOnlyDeserializes()
            throws Exception
    {
        AuthenticationConfiguration config = YAML_MAPPER.readValue("defaultType: [\"oauth\", \"form\"]\nshowFirstTypeOnly: true\n", AuthenticationConfiguration.class);
        assertThat(config.getDefaultType()).containsExactly("oauth", "form");
        assertThat(config.isShowFirstTypeOnly()).isTrue();
    }
}
