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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RoutingRulesDao
{
    @SqlQuery("SELECT * FROM routing_rules")
    List<RoutingRule> getAll();

    // "conditionExpression" is used as a column name because "condition" is a reserved word in MySQL
    @SqlUpdate("""
            INSERT INTO routing_rules (name, description, priority, conditionExpression, actions, routingRuleEngine)
            VALUES (:name, :description, :priority, :condition, :actions, :routingRuleEngine)
            """)
    void create(String name, String description, Integer priority, String condition, List<String> actions, RoutingRuleEngine routingRuleEngine);

    @SqlUpdate("""
            UPDATE routing_rules
            SET description = :description, priority = :priority, conditionExpression = :condition, actions = :actions, routingRuleEngine = :routingRuleEngine
            WHERE name = :name
            """)
    void update(String name, String description, Integer priority, String condition, List<String> actions, RoutingRuleEngine routingRuleEngine);

    @SqlQuery("SELECT * FROM routing_rules")
    @UseRowMapper(RoutingRulesStringToListMapper.class)
    List<RoutingRule> getAllNoListSupport();

    @SqlUpdate("""
            INSERT INTO routing_rules (name, description, priority, conditionExpression, actions, routingRuleEngine)
            VALUES (:name, :description, :priority, :condition, :actions, :routingRuleEngine)
            """)
    void createNoListSupport(String name, String description, Integer priority, String condition, String actions, RoutingRuleEngine routingRuleEngine);

    @SqlUpdate("""
            UPDATE routing_rules
            SET description = :description, priority = :priority, conditionExpression = :condition, actions = :actions, routingRuleEngine = :routingRuleEngine
            WHERE name = :name
            """)
    void updateNoListSupport(String name, String description, Integer priority, String condition, String actions, RoutingRuleEngine routingRuleEngine);

    @SqlUpdate("""
            DELETE FROM routing_rules
            WHERE name = :name
            """
    )
    void delete(String name);

    class RoutingRulesStringToListMapper
            implements RowMapper<RoutingRule>
    {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<List<String>> actionsTypeReference = new TypeReference<List<String>>() {};

        @Override
        public RoutingRule map(ResultSet rs, StatementContext ctx)
                throws SQLException
        {
            try {
                return new RoutingRule(
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("priority"),
                        rs.getString("conditionExpression"),
                        objectMapper.readValue(rs.getString("actions"), actionsTypeReference),
                        RoutingRuleEngine.valueOf(Optional.ofNullable(rs.getString("routingRuleEngine")).orElse("MVEL")));
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
