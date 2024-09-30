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
{}
