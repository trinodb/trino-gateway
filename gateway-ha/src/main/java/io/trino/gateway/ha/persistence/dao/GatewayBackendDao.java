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

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import static java.util.Locale.ENGLISH;

public interface GatewayBackendDao
{
    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("SELECT * FROM gateway_backend")
    List<GatewayBackend> findAll();

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true
            """)
    List<GatewayBackend> findActiveBackend();

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = 1
            """)
    List<GatewayBackend> findActiveBackendNoBoolean();

    default List<GatewayBackend> findActiveBackend(boolean isSupportsBooleanColumn)
    {
        if (isSupportsBooleanColumn) {
            return findActiveBackend();
        }

        return findActiveBackendNoBoolean();
    }

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true AND routing_group = 'adhoc'
            """)
    List<GatewayBackend> findActiveAdhocBackend();

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = 1 AND routing_group = 'adhoc'
            """)
    List<GatewayBackend> findActiveAdhocBackendNoBoolean();

    default List<GatewayBackend> findActiveAdhocBackend(boolean isSupportsBooleanColumn)
    {
        if (isSupportsBooleanColumn) {
            return findActiveAdhocBackend();
        }

        return findActiveAdhocBackendNoBoolean();
    }

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = true AND routing_group = :routingGroup
            """)
    List<GatewayBackend> findActiveBackendByRoutingGroup(String routingGroup);

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE active = 1 AND routing_group = :routingGroup
            """)
    List<GatewayBackend> findActiveBackendByRoutingGroupNoBoolean(String routingGroup);

    default List<GatewayBackend> findActiveBackendByRoutingGroup(String routingGroup, boolean isSupportsBooleanColumn)
    {
        if (isSupportsBooleanColumn) {
            return findActiveBackendByRoutingGroup(routingGroup);
        }

        return findActiveBackendByRoutingGroupNoBoolean(routingGroup);
    }

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE name = :name
            """)
    List<GatewayBackend> findByName(String name);

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE name = :name
            LIMIT 1
            """)
    GatewayBackend findFirstByName(String name);

    @UseRowMapper(GatewayBackendIntToBooleanMapper.class)
    @SqlQuery("""
            SELECT * FROM gateway_backend
            WHERE name = :name
            FETCH FIRST 1 ROWS ONLY
            """)
    GatewayBackend findFirstByNameWithFetch(String name);

    default GatewayBackend findFirstByName(String name, boolean isLimitUnsupported){
        if (isLimitUnsupported) {
            return findFirstByNameWithFetch(name);
        }

        return findFirstByName(name);
    }

    @SqlUpdate("""
            INSERT INTO gateway_backend (name, routing_group, backend_url, external_url, active)
            VALUES (:name, :routingGroup, :backendUrl, :externalUrl, :active)
            """)
    void create(String name, String routingGroup, String backendUrl, String externalUrl, boolean active);

    @SqlUpdate("""
            INSERT INTO gateway_backend (name, routing_group, backend_url, external_url, active)
            VALUES (:name, :routingGroup, :backendUrl, :externalUrl, :active)
            """)
    void createNoBoolean(String name, String routingGroup, String backendUrl, String externalUrl, int active);

    default void create(String name, String routingGroup, String backendUrl, String externalUrl, boolean active, boolean isSupportsBooleanColumn)
    {
        if (isSupportsBooleanColumn) {
            create(name, routingGroup, backendUrl, externalUrl, active);
            return;
        }
        createNoBoolean(name, routingGroup, backendUrl, externalUrl, active ? 1 : 0);
    }

    @SqlUpdate("""
            UPDATE gateway_backend
            SET routing_group = :routingGroup, backend_url = :backendUrl, external_url = :externalUrl, active = :active
            WHERE name = :name
            """)
    void update(String name, String routingGroup, String backendUrl, String externalUrl, boolean active);

    @SqlUpdate("""
            UPDATE gateway_backend
            SET routing_group = :routingGroup, backend_url = :backendUrl, external_url = :externalUrl, active = :active
            WHERE name = :name
            """)
    void updateNoBoolean(String name, String routingGroup, String backendUrl, String externalUrl, int active);

    default void update(String name, String routingGroup, String backendUrl, String externalUrl, boolean active, boolean isSupportsBooleanColumn)
    {
        if (isSupportsBooleanColumn) {
            update(name, routingGroup, backendUrl, externalUrl, active);
            return;
        }
        updateNoBoolean(name, routingGroup, backendUrl, externalUrl, active ? 1 : 0);
    }

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

    class GatewayBackendIntToBooleanMapper
            implements RowMapper<GatewayBackend>
    {
        @Override
        public GatewayBackend map(ResultSet resultSet, StatementContext ctx)
                throws SQLException
        {
            boolean active;
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            if (resultSetMetaData.getColumnClassName(5).toLowerCase(ENGLISH).startsWith("int")) {
                active = resultSet.getInt(5) != 0;
            }
            else {
                active = resultSet.getBoolean(5);
            }
            return new GatewayBackend(
                    resultSet.getString("name"),
                    resultSet.getString("routing_group"),
                    resultSet.getString("backend_url"),
                    resultSet.getString("external_url"),
                    active);
        }
    }
}
