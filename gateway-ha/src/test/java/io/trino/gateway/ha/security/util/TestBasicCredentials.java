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
package io.trino.gateway.ha.security.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.trino.gateway.ha.security.util.BasicCredentials.extractBasicAuthCredentials;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestBasicCredentials
{
    @Test
    public void testValidCredentials()
            throws AuthenticationException
    {
        BasicCredentials basicCredentials = extractBasicAuthCredentials(encodeToBasicAuthCredentials("user:pass123"));
        assertThat(basicCredentials.username()).isEqualTo("user");
        assertThat(basicCredentials.password()).isEqualTo("pass123");

        BasicCredentials emptyUsername = extractBasicAuthCredentials(encodeToBasicAuthCredentials(":pass123"));
        assertThat(emptyUsername.username()).isEqualTo("");
        assertThat(emptyUsername.password()).isEqualTo("pass123");

        BasicCredentials emptyPassword = extractBasicAuthCredentials(encodeToBasicAuthCredentials("user:"));
        assertThat(emptyPassword.username()).isEqualTo("user");
        assertThat(emptyPassword.password()).isEqualTo("");

        BasicCredentials emptyUsernameAndPassword = extractBasicAuthCredentials(encodeToBasicAuthCredentials(":"));
        assertThat(emptyUsernameAndPassword.username()).isEqualTo("");
        assertThat(emptyUsernameAndPassword.password()).isEqualTo("");

        BasicCredentials specialCharInPassword = extractBasicAuthCredentials(encodeToBasicAuthCredentials("username:passwordWith:char"));
        assertThat(specialCharInPassword.username()).isEqualTo("username");
        assertThat(specialCharInPassword.password()).isEqualTo("passwordWith:char");
    }

    @Test
    public void testInvalidCredentials()
            throws AuthenticationException
    {
        assertThatThrownBy(() -> extractBasicAuthCredentials("InvalidBasic " + encodeBase64("username:pass123")))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> extractBasicAuthCredentials(encodeToBasicAuthCredentials("userpass123")))
                .isInstanceOf(AuthenticationException.class);
    }

    private static String encodeToBasicAuthCredentials(String credentials)
    {
        return "Basic " + encodeBase64(credentials);
    }

    private static String encodeBase64(String credentials)
    {
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1));
    }
}
