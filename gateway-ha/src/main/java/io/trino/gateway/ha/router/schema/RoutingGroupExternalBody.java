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
package io.trino.gateway.ha.router.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.gateway.ha.router.TrinoQueryProperties;
import io.trino.gateway.ha.router.TrinoRequestUser;
import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.Optional;

public record RoutingGroupExternalBody(
        Optional<TrinoQueryProperties> trinoQueryProperties,
        Optional<TrinoRequestUser> trinoRequestUser,
        String contentType,
        String remoteUser,
        String method,
        String requestURI,
        String queryString,
        HttpSession session,
        String remoteAddr,
        String remoteHost,
        Map<String, String[]> parameters)
{
    @JsonCreator
    public RoutingGroupExternalBody(
            @JsonProperty("trinoQueryProperties") Optional<TrinoQueryProperties> trinoQueryProperties,
            @JsonProperty("trinoRequestUser") Optional<TrinoRequestUser> trinoRequestUser,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("remoteUser") String remoteUser,
            @JsonProperty("method") String method,
            @JsonProperty("requestURI") String requestURI,
            @JsonProperty("queryString") String queryString,
            @JsonProperty("session") HttpSession session,
            @JsonProperty("remoteAddr") String remoteAddr,
            @JsonProperty("remoteHost") String remoteHost,
            @JsonProperty("parameters") Map<String, String[]> parameters)
    {
        this.trinoQueryProperties = trinoQueryProperties;
        this.trinoRequestUser = trinoRequestUser;
        this.contentType = contentType;
        this.remoteUser = remoteUser;
        this.method = method;
        this.requestURI = requestURI;
        this.queryString = queryString;
        this.session = session;
        this.remoteAddr = remoteAddr;
        this.remoteHost = remoteHost;
        this.parameters = parameters;
    }

    @Override
    @JsonProperty
    public Optional<TrinoQueryProperties> trinoQueryProperties()
    {
        return trinoQueryProperties;
    }

    @Override
    @JsonProperty
    public Optional<TrinoRequestUser> trinoRequestUser()
    {
        return trinoRequestUser;
    }

    @Override
    @JsonProperty
    public String contentType()
    {
        return contentType;
    }

    @Override
    @JsonProperty
    public String remoteUser()
    {
        return remoteUser;
    }

    @Override
    @JsonProperty
    public String method()
    {
        return method;
    }

    @Override
    @JsonProperty
    public String requestURI()
    {
        return requestURI;
    }

    @Override
    @JsonProperty
    public String queryString()
    {
        return queryString;
    }

    @Override
    @JsonProperty
    public HttpSession session()
    {
        return session;
    }

    @Override
    @JsonProperty
    public String remoteAddr()
    {
        return remoteAddr;
    }

    @Override
    @JsonProperty
    public String remoteHost()
    {
        return remoteHost;
    }

    @Override
    @JsonProperty
    public Map<String, String[]> parameters()
    {
        return parameters;
    }
}
