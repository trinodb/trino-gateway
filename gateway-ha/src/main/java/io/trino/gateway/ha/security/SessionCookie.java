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

import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

public final class SessionCookie
{
    static final String OAUTH_ID_TOKEN = "token";
    static final String SELF_ISSUER_ID = "self";

    private SessionCookie() {}

    public static NewCookie getTokenCookie(String token)
    {
        return new NewCookie.Builder(OAUTH_ID_TOKEN)
                .value(token)
                .path("/")
                .domain("")
                .comment("")
                .maxAge(60 * 15) // 15 minutes session timeout
                .secure(true)
                .build();
    }

    public static Response logOut()
    {
        NewCookie cookie = new NewCookie.Builder(OAUTH_ID_TOKEN)
                .value("logout")
                .path("/")
                .domain("")
                .comment("")
                .maxAge(0)
                .secure(true)
                .build();
        return Response.ok("You are logged out successfully.")
                .cookie(cookie)
                .build();
    }
}
