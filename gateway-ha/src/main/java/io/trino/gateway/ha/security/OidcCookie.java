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

import com.google.common.base.Splitter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.gateway.ha.resource.LoginResource.CALLBACK_ENDPOINT;
import static jakarta.ws.rs.core.NewCookie.SameSite.LAX;

public class OidcCookie
{
    // prefix according to: https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05#section-4.1.3.1
    public static final String OIDC_COOKIE = "__Secure-Trino-Gateway-OIDC";
    private static final String DELIMITER = "|";
    private static final Splitter SPLITTER = Splitter.on(DELIMITER).limit(2);
    private static final int TOKEN_EXPIRATION_SECOND = 15 * 60;     // 15 minutes

    private OidcCookie() {}

    public static NewCookie create(String state, String nonce)
    {
        checkState(!state.contains(DELIMITER));
        checkState(!nonce.contains(DELIMITER));
        return new NewCookie.Builder(OIDC_COOKIE)
                .value(String.join(DELIMITER, state, nonce))
                .path(CALLBACK_ENDPOINT)
                .maxAge(TOKEN_EXPIRATION_SECOND)
                .sameSite(LAX)
                .secure(true)
                .httpOnly(true)
                .build();
    }

    public static Optional<String> getState(Cookie cookie)
    {
        return getComponents(cookie).map(List::getFirst);
    }

    public static Optional<String> getNonce(Cookie cookie)
    {
        return getComponents(cookie).map(List::getLast);
    }

    private static Optional<List<String>> getComponents(Cookie cookie)
    {
        return Optional.of(cookie)
                .map(Cookie::getValue)
                .map(SPLITTER::splitToList);
    }

    public static NewCookie delete()
    {
        return new NewCookie.Builder(OIDC_COOKIE)
                .value("delete")
                .path(CALLBACK_ENDPOINT)
                .maxAge(0)
                .sameSite(LAX)
                .secure(true)
                .httpOnly(true)
                .build();
    }
}
