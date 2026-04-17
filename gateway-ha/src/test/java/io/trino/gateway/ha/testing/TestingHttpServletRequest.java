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
package io.trino.gateway.ha.testing;

import com.google.common.collect.ImmutableList;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class TestingHttpServletRequest
        extends HttpServletRequestWrapper
{
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private final String requestUri;

    public TestingHttpServletRequest(String requestUri)
    {
        super(emptyRequest());
        this.requestUri = requestUri;
    }

    public void addHeader(String name, String value)
    {
        headers.put(name, value);
    }

    @Override
    public Object getAttribute(String name)
    {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object object)
    {
        attributes.put(name, object);
    }

    @Override
    public String getHeader(String name)
    {
        return headers.get(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        String value = headers.get(name);
        if (value == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(ImmutableList.of(value));
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public String getRequestURI()
    {
        return requestUri;
    }

    @Override
    public String getProtocol()
    {
        return "HTTP/1.1";
    }

    private static HttpServletRequest emptyRequest()
    {
        return (HttpServletRequest) Proxy.newProxyInstance(
                TestingHttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unexpected primitive type: " + type);
    }
}
