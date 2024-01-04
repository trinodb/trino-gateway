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

import io.dropwizard.auth.UnauthorizedHandler;
import jakarta.ws.rs.core.Response;

import java.net.URI;

public class LbUnauthorizedHandler
        implements UnauthorizedHandler
{
    private final String redirectPath;

    public LbUnauthorizedHandler(String authenticationType)
    {
        if (authenticationType.equals("oauth")) {
            this.redirectPath = "/sso";
        }
        else {
            this.redirectPath = "/login";
        }
    }

    @Override
    public Response buildResponse(String prefix, String realm)
    {
        return Response.status(302).location(URI.create(redirectPath)).build();
    }
}
