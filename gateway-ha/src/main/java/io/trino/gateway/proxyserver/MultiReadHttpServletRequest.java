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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class MultiReadHttpServletRequest
        extends HttpServletRequestWrapper
{
    private final byte[] content;

    public MultiReadHttpServletRequest(HttpServletRequest request, String body)
    {
        super(request);
        content = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream()
            throws IOException
    {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);
        return new ServletInputStream()
        {
            @Override
            public boolean isFinished()
            {
                // Determine if data is available for reading.
                return byteArrayInputStream.available() <= 0;
            }

            @Override
            public boolean isReady()
            {
                // It's an in memory stream, so it's ready to be read
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {}

            @Override
            public int read()
                    throws IOException
            {
                return byteArrayInputStream.read();
            }

            // Add multibyte read versions for efficiency
            @Override
            public int read(byte[] b, int off, int len)
                    throws IOException
            {
                return byteArrayInputStream.read(b, off, len);
            }

            @Override
            public int read(byte[] b)
                    throws IOException
            {
                return byteArrayInputStream.read(b);
            }
        };
    }

    @Override
    public BufferedReader getReader()
            throws IOException
    {
        return new BufferedReader(Reader.of(new String(content, StandardCharsets.UTF_8)));
    }
}
