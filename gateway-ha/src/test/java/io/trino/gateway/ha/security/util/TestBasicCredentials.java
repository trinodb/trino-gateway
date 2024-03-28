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

import java.util.Base64;
import java.util.Optional;

import static io.trino.gateway.ha.security.util.BasicCredentials.extractBasicAuthCredentials;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestBasicCredentials
{
    @Test
    public void testExtractBasicAuthCredentials()
            throws AuthenticationException
    {
        String header = "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        Optional<BasicCredentials> basicCredentials = extractBasicAuthCredentials(header);
        assertThat(basicCredentials).isPresent();
        assertThat(basicCredentials.orElseThrow().username()).isEqualTo("Aladdin");
        assertThat(basicCredentials.orElseThrow().password()).isEqualTo("open sesame");

        assertThatThrownBy(() -> extractBasicAuthCredentials(""))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> extractBasicAuthCredentials("random_stuff"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> extractBasicAuthCredentials("Basic random_stuff"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> extractBasicAuthCredentials("Basic random:stuff"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> extractBasicAuthCredentials("Basic Invalid_Base64Y^%*&^(*"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    public void testExtractBasicAuthCredentialsEmptyUsernameOrPassword()
            throws AuthenticationException
    {
        String emptyPassword = toBasicAuthCredential("user", "");    // "user:"
        Optional<BasicCredentials> emptyPasswordCredentials = extractBasicAuthCredentials(emptyPassword);
        assertThat(emptyPasswordCredentials).isPresent();
        assertThat(emptyPasswordCredentials.orElseThrow().username()).isEqualTo("user");
        assertThat(emptyPasswordCredentials.orElseThrow().password()).isEqualTo("");

        String emptyUsername = toBasicAuthCredential("", "password");    // ":password"
        Optional<BasicCredentials> emptyUsernameCredentials = extractBasicAuthCredentials(emptyUsername);
        assertThat(emptyUsernameCredentials).isPresent();
        assertThat(emptyUsernameCredentials.orElseThrow().username()).isEqualTo("");
        assertThat(emptyUsernameCredentials.orElseThrow().password()).isEqualTo("password");

        String emptyUserAndPassword = toBasicAuthCredential("", "");    // ":"
        Optional<BasicCredentials> emptyUserAndPasswordCredentials = extractBasicAuthCredentials(emptyUserAndPassword);
        assertThat(emptyUserAndPasswordCredentials).isPresent();
        assertThat(emptyUserAndPasswordCredentials.orElseThrow().username()).isEqualTo("");
        assertThat(emptyUserAndPasswordCredentials.orElseThrow().password()).isEqualTo("");
    }

    private String toBasicAuthCredential(String username, String password)
    {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(ISO_8859_1));
    }
}
