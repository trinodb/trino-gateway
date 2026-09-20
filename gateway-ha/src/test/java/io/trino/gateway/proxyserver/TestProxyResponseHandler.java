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
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.units.DataSize;
import io.trino.gateway.ha.config.ProxyResponseConfiguration;
import io.trino.gateway.proxyserver.ProxyResponseHandler.ProxyResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestProxyResponseHandler
{
    private static final Request REQUEST = prepareGet().setUri(URI.create("http://localhost/v1/spooled/download/token")).build();

    @Test
    void testBinaryBodyIsNotCorrupted()
    {
        // zstd frame magic followed by byte sequences that are not valid UTF-8; a String
        // round-trip would replace them with U+FFFD
        byte[] binaryBody = {(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD, (byte) 0x00, (byte) 0xFF, (byte) 0x80, (byte) 0xC3, (byte) 0x28};
        TestingResponse response = new TestingResponse(HttpStatus.OK, ImmutableListMultimap.of(), binaryBody);

        ProxyResponse proxyResponse = new ProxyResponseHandler(new ProxyResponseConfiguration()).handle(REQUEST, response);

        assertThat(proxyResponse.body()).isEqualTo(binaryBody);
    }

    @Test
    void testBodyOfExactlyResponseSizeIsAccepted()
    {
        ProxyResponseConfiguration configuration = new ProxyResponseConfiguration();
        configuration.setResponseSize(DataSize.of(4, BYTE));
        byte[] body = {1, 2, 3, 4};
        TestingResponse response = new TestingResponse(HttpStatus.OK, ImmutableListMultimap.of(), body);

        ProxyResponse proxyResponse = new ProxyResponseHandler(configuration).handle(REQUEST, response);

        assertThat(proxyResponse.body()).isEqualTo(body);
    }

    @Test
    void testTextualBodyIsLoggedAsText()
    {
        String json = "{\"id\":\"20240101_000000_00000_abcde\"}";
        ProxyResponse response = new ProxyResponse(200, ImmutableListMultimap.of(HeaderName.of(CONTENT_TYPE), "application/json; charset=utf-8"), json.getBytes(UTF_8));

        assertThat(response.bodyForLogging()).isEqualTo(json);
    }

    @Test
    void testBinaryBodyIsLoggedAsLength()
    {
        ProxyResponse response = new ProxyResponse(200, ImmutableListMultimap.of(HeaderName.of(CONTENT_TYPE), "application/octet-stream"), new byte[] {(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD});

        assertThat(response.bodyForLogging()).isEqualTo("<4 bytes of application/octet-stream>");
    }

    @Test
    void testBodyWithoutContentTypeIsLoggedAsLength()
    {
        ProxyResponse response = new ProxyResponse(200, ImmutableListMultimap.of(), new byte[] {1, 2, 3});

        assertThat(response.bodyForLogging()).isEqualTo("<3 bytes of unknown content type>");
    }

    @Test
    void testOversizedBodyFailsInsteadOfTruncating()
    {
        ProxyResponseConfiguration configuration = new ProxyResponseConfiguration();
        configuration.setResponseSize(DataSize.of(4, BYTE));
        TestingResponse response = new TestingResponse(HttpStatus.OK, ImmutableListMultimap.of(), new byte[] {1, 2, 3, 4, 5});

        assertThatThrownBy(() -> new ProxyResponseHandler(configuration).handle(REQUEST, response))
                .isInstanceOf(ProxyException.class)
                .hasMessageContaining("proxyResponseConfiguration.responseSize");
    }
}
