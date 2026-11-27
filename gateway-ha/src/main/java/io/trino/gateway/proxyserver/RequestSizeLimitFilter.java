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
package io.trino.gateway.proxyserver;

import com.google.inject.Inject;
import io.airlift.units.DataSize;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.security.util.GatewayFilterPriorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static jakarta.ws.rs.core.Response.Status.REQUEST_ENTITY_TOO_LARGE;
import static java.lang.Math.toIntExact;

/// Rejects requests with a body larger than the configured request size. The limit applies to
/// the body after decompression. The filter runs before all other filters, so that nothing reads
/// the body before the limit applies.
@PreMatching
@Priority(GatewayFilterPriorities.REQUEST_SIZE_LIMIT)
public class RequestSizeLimitFilter
        implements ContainerRequestFilter
{
    private final DataSize requestSize;
    private final int maxBytes;

    @Inject
    public RequestSizeLimitFilter(HaGatewayConfiguration configuration)
    {
        this.requestSize = configuration.getProxyRequestConfiguration().getRequestSize();
        long bytes = requestSize.toBytes();
        checkArgument(bytes > 0 && bytes < Integer.MAX_VALUE, "requestSize must be greater than 0 and less than 2GB: %s", requestSize);
        this.maxBytes = toIntExact(bytes);
    }

    @Override
    public void filter(ContainerRequestContext request)
            throws IOException
    {
        if (!request.hasEntity()) {
            return;
        }
        if (request.getLength() > maxBytes) {
            abort(request);
            return;
        }
        byte[] body;
        try (InputStream entity = request.getEntityStream()) {
            body = entity.readNBytes(maxBytes + 1);
        }
        if (body.length > maxBytes) {
            abort(request);
            return;
        }
        request.setEntityStream(new ByteArrayInputStream(body));
    }

    private void abort(ContainerRequestContext request)
    {
        request.abortWith(Response.status(REQUEST_ENTITY_TOO_LARGE)
                .type(TEXT_PLAIN_TYPE)
                .entity("Request body exceeds the maximum size of " + requestSize)
                .build());
    }
}
