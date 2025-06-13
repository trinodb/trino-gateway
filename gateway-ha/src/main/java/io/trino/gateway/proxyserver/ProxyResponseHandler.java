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

import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.units.DataSize;
import io.trino.gateway.ha.config.ProxyResponseConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
            return new ProxyResponse(response.getStatusCode(), response.getHeaders(), new String(response.getInputStream().readNBytes((int) responseSize.toBytes()), StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            throw new ProxyException("Failed reading response from remote Trino server", e);
        }
    }
}
