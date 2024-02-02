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
package io.trino.gateway.ha.persistence.dao;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import static java.util.Objects.requireNonNull;

public record GatewayBackend(String name, String routingGroup, String backendUrl, String externalUrl, boolean active)
{
    @JdbiConstructor
    public GatewayBackend(
            @ColumnName("name") String name,
            @ColumnName("routing_group") String routingGroup,
            @ColumnName("backend_url") String backendUrl,
            @ColumnName("external_url") String externalUrl,
            @ColumnName("active") boolean active)
    {
        this.name = requireNonNull(name, "name is null");
        this.routingGroup = requireNonNull(routingGroup, "routingGroup is null");
        this.backendUrl = requireNonNull(backendUrl, "backendUrl is null");
        this.externalUrl = requireNonNull(externalUrl, "externalUrl is null");
        this.active = active;
    }
}
