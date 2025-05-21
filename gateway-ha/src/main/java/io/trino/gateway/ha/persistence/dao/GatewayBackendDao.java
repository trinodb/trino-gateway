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

import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface GatewayBackendDao
{
    @SqlQuery("SELECT * FROM gateway_backend")
    List<GatewayBackend> findAll();

    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true
            """)
    List<GatewayBackend> findActiveBackend();

    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true AND routing_group = :defaultRoutingGroup
            """)
    List<GatewayBackend> findActiveAdhocBackend(String defaultRoutingGroup);

    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true AND routing_group = :routingGroup
            """)
    List<GatewayBackend> findActiveBackendByRoutingGroup(String routingGroup);

    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE name = :name
            """)
    List<GatewayBackend> findByName(String name);

    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE name = :name
            LIMIT 1
            """)
    GatewayBackend findFirstByName(String name);

    @SqlUpdate("""
            INSERT INTO gateway_backend (name, routing_group, backend_url, external_url, active)
            VALUES (:name, :routingGroup, :backendUrl, :externalUrl, :active)
            """)
    void create(String name, String routingGroup, String backendUrl, String externalUrl, boolean active);

    @SqlUpdate("""
            UPDATE gateway_backend
            SET routing_group = :routingGroup, backend_url = :backendUrl, external_url = :externalUrl, active = :active
            WHERE name = :name
            """)
    void update(String name, String routingGroup, String backendUrl, String externalUrl, boolean active);

    @SqlUpdate("""
            UPDATE gateway_backend
            SET active = false
            WHERE name = :name
            """)
    void deactivate(String name);

    @SqlUpdate("""
            UPDATE gateway_backend
            SET active = true
            WHERE name = :name
            """)
    void activate(String name);

    @SqlUpdate("""
            DELETE FROM gateway_backend
            WHERE name = :name
            """)
    void deleteByName(String name);
}
