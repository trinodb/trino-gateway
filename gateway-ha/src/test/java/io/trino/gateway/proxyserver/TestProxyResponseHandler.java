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

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.Request;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.units.DataSize;
import io.trino.gateway.ha.config.ProxyResponseConfiguration;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestProxyResponseHandler
{
    private static final Request REQUEST = prepareGet().setUri(URI.create("http://localhost:8080/v1/spooled/download/segment")).build();

    @Test
    void testBinaryBodyIsNotCorrupted()
    {
        // zstd magic number followed by bytes that are not valid UTF-8
        byte[] body = {(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD, (byte) 0xFF, (byte) 0xFE, (byte) 0x80, (byte) 0xC3};

        ProxyResponse response = createHandler(DataSize.of(32, MEGABYTE))
                .handle(REQUEST, new TestingResponse(OK, ImmutableListMultimap.of(), body));

        assertThat(response.statusCode()).isEqualTo(OK.code());
        assertThat(response.body()).isEqualTo(body);
    }

    @Test
    void testOversizedBodyFailsInsteadOfTruncating()
    {
        byte[] body = "12345".getBytes(UTF_8);

        assertThatThrownBy(() -> createHandler(DataSize.of(4, BYTE))
                .handle(REQUEST, new TestingResponse(OK, ImmutableListMultimap.of(), body)))
                .isInstanceOf(ProxyException.class)
                .hasMessageContaining("responseSize")
                .hasMessageContaining("4B")
                .hasMessageContaining(REQUEST.getUri().toString());
    }

    private static ProxyResponseHandler createHandler(DataSize responseSize)
    {
        ProxyResponseConfiguration configuration = new ProxyResponseConfiguration();
        configuration.setResponseSize(responseSize);
        return new ProxyResponseHandler(configuration);
    }
}
