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
package io.trino.gateway.ha.util;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.trino.gateway.ha.util.ConfigurationUtils.replaceEnvironmentVariables;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestConfigurationUtils
{
    private static final String config = """
            serverConfig:
                http-server.https.keystore.path: certificate.pem
                http-server.https.keystore.key: ${ENV:KEYSTORE_KEY}
            presetUsers:
              api:
                password: ${ENV:API_PASSWORD}
                privileges: API
            """;
    private static final String expected = """
            serverConfig:
                http-server.https.keystore.path: certificate.pem
                http-server.https.keystore.key: keystore_12345
            presetUsers:
              api:
                password: api_passw0rd
                privileges: API
            """;

    @Test
    void testReplaceEnvironmentVariables()
    {
        Map<String, String> env = ImmutableMap.<String, String>builder()
                .put("KEYSTORE_KEY", "keystore_12345")
                .put("API_PASSWORD", "api_passw0rd")
                .build();
        String result = replaceEnvironmentVariables(config, env);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testMissingEnvironmentVariables()
    {
        assertThatThrownBy(() -> replaceEnvironmentVariables(config, ImmutableMap.of()))
                .hasMessageStartingWith("Configuration references unset environment variable");
    }
}
