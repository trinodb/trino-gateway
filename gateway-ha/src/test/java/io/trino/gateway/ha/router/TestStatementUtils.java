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
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import static io.trino.gateway.ha.router.StatementUtils.getResourceGroupQueryType;
import static org.assertj.core.api.Assertions.assertThat;

final class TestStatementUtils
{
    private final SqlParser parser = new SqlParser();

    // Excluded on purpose: ExplainAnalyze is resolved to its target query's type by
    // getResourceGroupQueryType; Execute/ExecuteImmediate are unwrapped by the caller
    // (TrinoQueryProperties) and, like upstream getQueryType, have no mapping of their own.
    private static final Set<Class<? extends Statement>> UNMAPPED_BY_DESIGN =
            ImmutableSet.<Class<? extends Statement>>builder()
                    .add(Execute.class)
                    .add(ExecuteImmediate.class)
                    .add(ExplainAnalyze.class)
                    .build();

    @Test
    void testCommonStatementsMapToResourceGroupQueryType()
    {
        assertResourceGroupQueryType("SELECT 1", "SELECT");
        assertResourceGroupQueryType("CREATE TABLE t (a integer)", "DATA_DEFINITION");
    }

    @Test
    void testBranchStatementsMapToResourceGroupQueryType()
    {
        // Branch DDL must be classified like the rest of Trino's StatementUtils
        // so that queryType-based routing rules (e.g. resourceGroupQueryType == "DATA_DEFINITION") match.
        assertResourceGroupQueryType("SHOW BRANCHES FROM TABLE t", "DESCRIBE");
        assertResourceGroupQueryType("CREATE BRANCH b IN TABLE t", "DATA_DEFINITION");
        assertResourceGroupQueryType("DROP BRANCH b IN TABLE t", "DATA_DEFINITION");
        assertResourceGroupQueryType("ALTER BRANCH fb IN TABLE t FAST FORWARD TO tb", "DATA_DEFINITION");
    }

    @Test
    void testRefreshViewMapsToResourceGroupQueryType()
    {
        assertResourceGroupQueryType("ALTER VIEW a REFRESH", "DATA_DEFINITION");
    }

    @Test
    void testColumnDefaultValueStatementsMapToResourceGroupQueryType()
    {
        assertResourceGroupQueryType("ALTER TABLE foo.t ALTER COLUMN a SET DEFAULT 123", "DATA_DEFINITION");
        assertResourceGroupQueryType("ALTER TABLE foo.t ALTER COLUMN a DROP DEFAULT", "DATA_DEFINITION");
    }

    // Drift guard: this map is a hand-maintained copy of io.trino.util.StatementUtils and silently
    // returns "UNKNOWN" for any statement it forgot. When dep.trino.version is bumped and Trino adds
    // a new Statement type, this fails instead of requiring a manual diff against upstream.
    @Test
    void testEveryStatementTypeIsClassified()
            throws IOException
    {
        Set<Class<?>> discovered = ClassPath.from(Statement.class.getClassLoader())
                .getTopLevelClasses("io.trino.sql.tree").stream()
                .map(ClassPath.ClassInfo::load)
                .filter(Statement.class::isAssignableFrom)
                .filter(clazz -> !clazz.equals(Statement.class))
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .collect(Collectors.toSet());

        // Guard against a fail-open scan: Guava ClassPath only enumerates file:// / URLClassLoader
        // entries, so under a Surefire manifest-jar or module-path run it can return an empty/partial
        // set, which would make the drift guard below pass vacuously. Requiring the scan to rediscover
        // every already-mapped type proves it actually enumerated the package (and needs no magic number).
        assertThat(discovered)
                .as("classpath scan did not rediscover all mapped Statement types in io.trino.sql.tree — "
                        + "the scan likely could not enumerate the classloader, so the drift guard would pass vacuously")
                .containsAll(StatementUtils.statementsWithKnownQueryType());

        Set<Class<?>> unmapped = discovered.stream()
                .filter(clazz -> !StatementUtils.statementsWithKnownQueryType().contains(clazz))
                .filter(clazz -> !UNMAPPED_BY_DESIGN.contains(clazz))
                .collect(Collectors.toSet());

        assertThat(unmapped)
                .as("Statement types missing from StatementUtils.STATEMENT_QUERY_TYPES (add them, or to UNMAPPED_BY_DESIGN if intentionally special-cased)")
                .isEmpty();
    }

    private void assertResourceGroupQueryType(@Language("sql") String sql, String expectedQueryType)
    {
        assertThat(getResourceGroupQueryType(parser.createStatement(sql))).isEqualTo(expectedQueryType);
    }
}
