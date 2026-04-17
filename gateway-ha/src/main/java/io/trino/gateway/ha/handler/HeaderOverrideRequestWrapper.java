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
package io.trino.gateway.ha.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Stream;

public class HeaderOverrideRequestWrapper
        extends HttpServletRequestWrapper
{
    private final Map<String, String> customHeaders;

    public HeaderOverrideRequestWrapper(HttpServletRequest request, Map<String, String> customHeaders)
    {
        super(request);
        this.customHeaders = customHeaders;
    }

    @Override
    public String getHeader(String name)
    {
        if (customHeaders.containsKey(name)) {
            return customHeaders.get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        if (customHeaders.containsKey(name)) {
            return Collections.enumeration(Collections.singletonList(customHeaders.get(name)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return Collections.enumeration(
                Stream.concat(Collections.list(super.getHeaderNames()).stream(), customHeaders.keySet().stream())
                        .distinct()
                        .toList());
    }
}
