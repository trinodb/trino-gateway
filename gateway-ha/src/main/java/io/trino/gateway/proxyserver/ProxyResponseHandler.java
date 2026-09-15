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

import com.google.common.collect.ListMultimap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.units.DataSize;
import io.trino.gateway.ha.config.ProxyResponseConfiguration;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ProxyResponseHandler
        implements ResponseHandler<ProxyResponse, RuntimeException>
{
    private final DataSize responseSize;

    public ProxyResponseHandler(ProxyResponseConfiguration proxyResponseConfiguration)
    {
        this.responseSize = requireNonNull(proxyResponseConfiguration.getResponseSize(), "responseSize is null");
    }

    @Override
    public ProxyResponse handleException(Request request, Exception exception)
    {
        throw new ProxyException("Request to remote Trino server failed", exception);
    }

    @Override
    public ProxyResponse handle(Request request, Response response)
    {
        try {
            InputStream inputStream = response.getInputStream();
            byte[] body = inputStream.readNBytes((int) responseSize.toBytes());
            if (inputStream.read() != -1) {
                // Forwarding a truncated body together with the backend's status would silently hand the client corrupt data
                throw new ProxyException(
                        "Response from remote Trino server %s exceeds the configured proxyResponseConfiguration.responseSize of %s. Increase it to at least the largest response the backend can return, such as protocol.spooling.max-segment-size for spooled results"
                                .formatted(request.getUri(), responseSize));
            }
            return new ProxyResponse(response.getStatusCode(), response.getHeaders(), body);
        }
        catch (IOException e) {
            throw new ProxyException("Failed reading response from remote Trino server", e);
        }
    }

    public record ProxyResponse(
            int statusCode,
            ListMultimap<HeaderName, String> headers,
            byte[] body)
    {
        public ProxyResponse
        {
            requireNonNull(headers, "headers is null");
            requireNonNull(body, "body is null");
        }

        public String bodyAsString()
        {
            return new String(body, UTF_8);
        }
    }
}
