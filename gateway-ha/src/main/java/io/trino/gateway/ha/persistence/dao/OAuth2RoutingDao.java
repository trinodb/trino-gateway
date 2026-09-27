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

public interface OAuth2RoutingDao
{
    @SqlQuery(
            """
            SELECT backend_url FROM oauth2_routing
            WHERE pin_key = :pinKey
            """)
    String findBackendByPinKey(String pinKey);

    @SqlUpdate(
            """
            INSERT INTO oauth2_routing (pin_key, backend_url, created)
            VALUES (:pinKey, :backendUrl, :created)
            """)
    void insert(String pinKey, String backendUrl, long created);

    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE pin_key = :pinKey
            """)
    void delete(String pinKey);

    /**
     * Deletes up to {@code batchSize} expired pins and returns the number of rows deleted.
     */
    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE created < :created
            ORDER BY created
            LIMIT :batchSize
            """)
    int deleteOldPinsBatchMysql(long created, int batchSize);

    /**
     * PostgreSQL has no {@code LIMIT} on {@code DELETE}, so the keys come from a sub-select.
     * {@code SKIP LOCKED} lets sweeps on several gateway instances take different batches instead of
     * waiting on each other. {@code = ANY(ARRAY(...))} runs the sub-select once and deletes through the
     * primary key, where {@code IN (...)} can be planned as a join over a full scan of the table.
     */
    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE pin_key = ANY(ARRAY(
                SELECT pin_key FROM oauth2_routing
                WHERE created < :created
                ORDER BY created
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            ))
            """)
    int deleteOldPinsBatchPostgres(long created, int batchSize);

    /**
     * Oracle has no {@code LIMIT} on {@code DELETE} and no {@code FOR UPDATE} in a sub-select, so
     * {@code ROWNUM} over an inline view ordered by {@code created} takes the oldest rows, read from the
     * {@code created} index without a sort.
     */
    @SqlUpdate(
            """
            DELETE FROM oauth2_routing
            WHERE ROWID IN (
                SELECT rid FROM (
                    SELECT ROWID AS rid FROM oauth2_routing
                    WHERE created < :created
                    ORDER BY created
                )
                WHERE ROWNUM <= :batchSize
            )
            """)
    int deleteOldPinsBatchOracle(long created, int batchSize);
}
