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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
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
            // Keep the body as raw bytes: responses such as spooled segment downloads are binary
            // and would be corrupted by a round-trip through a String
            byte[] body = response.getInputStream().readNBytes((int) responseSize.toBytes());
            if (response.getInputStream().read() != -1) {
                // Never forward a truncated body together with the backend's Content-Length
                throw new ProxyException("Response from %s exceeds the configured proxyResponseConfiguration.responseSize of %s".formatted(request.getUri(), responseSize));
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

        /**
         * Returns the body as text when the Content-Type identifies a textual payload,
         * otherwise only its size, so binary bodies such as spooled segments never reach the logs.
         */
        public String bodyForLogging()
        {
            String contentType = headers.get(HeaderName.of(CONTENT_TYPE)).stream().findFirst().orElse("");
            String normalizedContentType = contentType.toLowerCase(Locale.ENGLISH);
            if (normalizedContentType.startsWith("text/") || normalizedContentType.contains("json") || normalizedContentType.contains("xml")) {
                return new String(body, StandardCharsets.UTF_8);
            }
            return "<%s bytes of %s>".formatted(body.length, contentType.isEmpty() ? "unknown content type" : contentType);
        }
    }
}
