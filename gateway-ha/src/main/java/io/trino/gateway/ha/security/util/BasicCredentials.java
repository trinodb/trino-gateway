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

import com.google.common.base.Splitter;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.Base64;
import java.util.List;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Objects.requireNonNull;

public record BasicCredentials(String username, String password)
{
    public BasicCredentials
    {
        requireNonNull(username, "username is null");
        requireNonNull(password, "password is null");
    }

    public static BasicCredentials extractBasicAuthCredentials(ContainerRequestContext request)
            throws AuthenticationException
    {
        requireNonNull(request, "request is null");

        // This handles HTTP basic auth per RFC 7617. The header contains the
        // case-insensitive "Basic" scheme followed by a Base64 encoded "username:password".
        String header = nullToEmpty(request.getHeaders().getFirst(AUTHORIZATION));

        return extractBasicAuthCredentials(header);
    }

    public static BasicCredentials extractBasicAuthCredentials(String header)
            throws AuthenticationException
    {
        requireNonNull(header, "header is null");

        int space = header.indexOf(' ');
        if ((space < 0) || !header.substring(0, space).equalsIgnoreCase("basic")) {
            throw new AuthenticationException("Invalid credentials");
        }
        String credentials = decodeCredentials(header.substring(space + 1).trim());

        List<String> parts = Splitter.on(':').limit(2).splitToList(credentials);
        if (parts.size() != 2) {
            throw new AuthenticationException("Invalid credentials");
        }
        String username = parts.get(0);
        String password = parts.get(1);
        return new BasicCredentials(username, password);
    }

    private static String decodeCredentials(String credentials)
            throws AuthenticationException
    {
        // The original basic auth RFC 2617 did not specify a character set.
        // Many clients, including the Trino CLI and JDBC driver, use ISO-8859-1.
        // RFC 7617 allows the server to specify UTF-8 as the character set during
        // the challenge, but this doesn't help as most clients pre-authenticate.
        try {
            return new String(Base64.getDecoder().decode(credentials), ISO_8859_1);
        }
        catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid base64 encoded credentials");
        }
    }
}
