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

import io.trino.gateway.ha.domain.Result;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.internal.LocalizationMessages;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;

@Provider
public class AuthorizedExceptionMapper
        implements ExceptionMapper<ForbiddenException>
{
    @Override
    public Response toResponse(ForbiddenException exception)
    {
        if (exception.getMessage().equals(LocalizationMessages.USER_NOT_AUTHORIZED())) {
            // The user is authenticated, but lacks the role that the resource requires
            return Response.status(FORBIDDEN)
                    .entity(Result.fail(FORBIDDEN))
                    .type(APPLICATION_JSON_TYPE)
                    .build();
        }
        return exception.getResponse();
    }
}
