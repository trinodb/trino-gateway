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
package io.trino.gateway.ha.router;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Execute;
import io.trino.sql.tree.ExecuteImmediate;
import io.trino.sql.tree.ExplainAnalyze;
import io.trino.sql.tree.Statement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

final class TestStatementUtils
{
    private final SqlParser parser = new SqlParser();

    // Statement types that getResourceGroupQueryType handles without a STATEMENT_QUERY_TYPES entry:
    // Execute / ExecuteImmediate are unwrapped to their underlying statement before mapping, and
    // ExplainAnalyze is resolved to the type of its target query.
    private static final Set<Class<? extends Statement>> UNMAPPED_BY_DESIGN =
            ImmutableSet.of(Execute.class, ExecuteImmediate.class, ExplainAnalyze.class);

    private String resourceGroupQueryType(String sql)
    {
        return StatementUtils.getResourceGroupQueryType(parser.createStatement(sql));
    }

    @Test
    void testCommonStatementsMapToResourceGroupQueryType()
    {
        assertThat(resourceGroupQueryType("SELECT 1")).isEqualTo("SELECT");
        assertThat(resourceGroupQueryType("CREATE TABLE t (a integer)")).isEqualTo("DATA_DEFINITION");
    }

    @Test
    void testBranchStatementsMapToResourceGroupQueryType()
    {
        // Iceberg branch DDL must be classified like the rest of Trino's StatementUtils
        // so that queryType-based routing rules (e.g. resourceGroupQueryType == "DATA_DEFINITION") match.
        assertThat(resourceGroupQueryType("SHOW BRANCHES FROM TABLE t")).isEqualTo("DESCRIBE");
        assertThat(resourceGroupQueryType("CREATE BRANCH b IN TABLE t")).isEqualTo("DATA_DEFINITION");
        assertThat(resourceGroupQueryType("DROP BRANCH b IN TABLE t")).isEqualTo("DATA_DEFINITION");
        assertThat(resourceGroupQueryType("ALTER BRANCH fb IN TABLE t FAST FORWARD TO tb")).isEqualTo("DATA_DEFINITION");
    }

    @Test
    void testRefreshViewMapsToResourceGroupQueryType()
    {
        assertThat(resourceGroupQueryType("ALTER VIEW a REFRESH")).isEqualTo("DATA_DEFINITION");
    }

    @Test
    void testColumnDefaultValueStatementsMapToResourceGroupQueryType()
    {
        assertThat(resourceGroupQueryType("ALTER TABLE foo.t ALTER COLUMN a SET DEFAULT 123")).isEqualTo("DATA_DEFINITION");
        assertThat(resourceGroupQueryType("ALTER TABLE foo.t ALTER COLUMN a DROP DEFAULT")).isEqualTo("DATA_DEFINITION");
    }

    // Drift guard: this map is a hand-maintained copy of io.trino.util.StatementUtils and silently
    // returns "UNKNOWN" for any statement it forgot. When dep.trino.version is bumped and Trino adds
    // a new Statement type, this fails instead of requiring a manual diff against upstream.
    @Test
    void testEveryStatementTypeIsClassified()
            throws IOException
    {
        Set<Class<?>> unmapped = ClassPath.from(Statement.class.getClassLoader())
                .getTopLevelClasses("io.trino.sql.tree").stream()
                .map(ClassPath.ClassInfo::load)
                .filter(Statement.class::isAssignableFrom)
                .filter(clazz -> !clazz.equals(Statement.class))
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .filter(clazz -> !StatementUtils.statementsWithKnownQueryType().contains(clazz))
                .filter(clazz -> !UNMAPPED_BY_DESIGN.contains(clazz))
                .collect(Collectors.toSet());

        assertThat(unmapped)
                .as("Statement types missing from StatementUtils.STATEMENT_QUERY_TYPES (add them, or to UNMAPPED_BY_DESIGN if intentionally special-cased)")
                .isEmpty();
    }
}
