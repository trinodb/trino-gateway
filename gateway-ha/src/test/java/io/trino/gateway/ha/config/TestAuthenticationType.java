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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestAuthenticationType
{
    @Test
    void testValueRoundTrips()
    {
        assertThat(AuthenticationType.OAUTH.value()).isEqualTo("oauth");
        assertThat(AuthenticationType.FORM.value()).isEqualTo("form");
        assertThat(AuthenticationType.fromValue("oauth")).isEqualTo(AuthenticationType.OAUTH);
        assertThat(AuthenticationType.fromValue("form")).isEqualTo(AuthenticationType.FORM);
    }

    @Test
    void testFromValueIsCaseInsensitive()
    {
        // Config values should not fail to boot just because of casing.
        assertThat(AuthenticationType.fromValue("OAUTH")).isEqualTo(AuthenticationType.OAUTH);
        assertThat(AuthenticationType.fromValue("Form")).isEqualTo(AuthenticationType.FORM);
    }

    @Test
    void testFromValueRejectsUnknown()
    {
        // Misspelled/unsupported values must fail fast, and the message must list the supported types.
        assertThatThrownBy(() -> AuthenticationType.fromValue("oath"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"oauth\"")
                .hasMessageContaining("\"form\"");
    }
}
