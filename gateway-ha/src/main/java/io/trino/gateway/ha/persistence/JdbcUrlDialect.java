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
package io.trino.gateway.ha.persistence;

import jakarta.annotation.Nullable;

/**
 * The SQL dialect implied by a JDBC URL's prefix. Shared by {@link FlywayMigration} (choosing a
 * migration script location) and {@link JdbcConnectionManager} (choosing dialect-specific
 * batched-delete SQL for the OAuth2 routing sweep) so the three supported prefixes -- and the
 * failure on an unrecognized one -- are defined in exactly one place.
 */
enum JdbcUrlDialect
{
    MYSQL, POSTGRESQL, ORACLE;

    static JdbcUrlDialect forJdbcUrl(@Nullable String jdbcUrl)
    {
        // A null URL is only ever seen here via JdbcConnectionManager's test-only constructor, which
        // (unlike the real startup path) may not have a real DataStoreConfiguration behind it;
        // defaulting to MYSQL keeps that constructor usable without a JDBC URL rather than forcing
        // every such test to supply one it does not otherwise care about.
        if (jdbcUrl == null) {
            return MYSQL;
        }
        if (jdbcUrl.startsWith("jdbc:postgresql")) {
            return POSTGRESQL;
        }
        if (jdbcUrl.startsWith("jdbc:mysql")) {
            return MYSQL;
        }
        if (jdbcUrl.startsWith("jdbc:oracle")) {
            return ORACLE;
        }
        throw new IllegalArgumentException(
                "Invalid JDBC URL: %s. Only PostgreSQL, MySQL, and Oracle are supported.".formatted(jdbcUrl));
    }
}
