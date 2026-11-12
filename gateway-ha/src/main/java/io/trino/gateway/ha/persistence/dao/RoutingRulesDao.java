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

import org.jdbi.v3.sqlobject.customizer.QueryTimeOut;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface RoutingRulesDao
{
    @SqlQuery("SELECT * FROM routing_rules")
    @QueryTimeOut(5)
    List<RoutingRuleRecord> findAll();

    @SqlQuery("SELECT COUNT(*) FROM routing_rules WHERE name = :name")
    int countByName(String name);

    @SqlUpdate(
            """
            INSERT INTO routing_rules (name, description, priority, actions, rule_condition)
            VALUES (:name, :description, :priority, :actions, :condition)
            """)
    void create(String name, String description, int priority, String actions, String condition);

    @SqlUpdate(
            """
            UPDATE routing_rules
            SET description = :description, priority = :priority, actions = :actions, rule_condition = :condition
            WHERE name = :name
            """)
    int update(String name, String description, int priority, String actions, String condition);

    @SqlUpdate("DELETE FROM routing_rules WHERE name = :name")
    int delete(String name);
}
