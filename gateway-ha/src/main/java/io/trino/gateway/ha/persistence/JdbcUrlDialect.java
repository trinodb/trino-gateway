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

/**
 * Database type implied by a JDBC URL, shared by {@link FlywayMigration} and {@link JdbcConnectionManager}.
 */
enum JdbcUrlDialect
{
    MYSQL, POSTGRESQL, ORACLE;

    static JdbcUrlDialect forJdbcUrl(String jdbcUrl)
    {
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
