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

import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.trino.sql.tree.AddColumn;
import io.trino.sql.tree.Analyze;
import io.trino.sql.tree.Call;
import io.trino.sql.tree.Comment;
import io.trino.sql.tree.Commit;
import io.trino.sql.tree.CreateCatalog;
import io.trino.sql.tree.CreateFunction;
import io.trino.sql.tree.CreateMaterializedView;
import io.trino.sql.tree.CreateRole;
import io.trino.sql.tree.CreateSchema;
import io.trino.sql.tree.CreateTable;
import io.trino.sql.tree.CreateTableAsSelect;
import io.trino.sql.tree.CreateView;
import io.trino.sql.tree.Deallocate;
import io.trino.sql.tree.Delete;
import io.trino.sql.tree.Deny;
import io.trino.sql.tree.DescribeInput;
import io.trino.sql.tree.DescribeOutput;
import io.trino.sql.tree.DropCatalog;
import io.trino.sql.tree.DropColumn;
import io.trino.sql.tree.DropFunction;
import io.trino.sql.tree.DropMaterializedView;
import io.trino.sql.tree.DropNotNullConstraint;
import io.trino.sql.tree.DropRole;
import io.trino.sql.tree.DropSchema;
import io.trino.sql.tree.DropTable;
import io.trino.sql.tree.DropView;
import io.trino.sql.tree.Explain;
import io.trino.sql.tree.ExplainAnalyze;
import io.trino.sql.tree.Grant;
import io.trino.sql.tree.GrantRoles;
import io.trino.sql.tree.Insert;
import io.trino.sql.tree.Merge;
import io.trino.sql.tree.Prepare;
import io.trino.sql.tree.Query;
import io.trino.sql.tree.RefreshMaterializedView;
import io.trino.sql.tree.RenameColumn;
import io.trino.sql.tree.RenameMaterializedView;
import io.trino.sql.tree.RenameSchema;
import io.trino.sql.tree.RenameTable;
import io.trino.sql.tree.RenameView;
import io.trino.sql.tree.ResetSession;
import io.trino.sql.tree.ResetSessionAuthorization;
import io.trino.sql.tree.Revoke;
import io.trino.sql.tree.RevokeRoles;
import io.trino.sql.tree.Rollback;
import io.trino.sql.tree.SetColumnType;
import io.trino.sql.tree.SetPath;
import io.trino.sql.tree.SetProperties;
import io.trino.sql.tree.SetRole;
import io.trino.sql.tree.SetSchemaAuthorization;
import io.trino.sql.tree.SetSession;
import io.trino.sql.tree.SetSessionAuthorization;
import io.trino.sql.tree.SetTableAuthorization;
import io.trino.sql.tree.SetTimeZone;
import io.trino.sql.tree.SetViewAuthorization;
import io.trino.sql.tree.ShowCatalogs;
import io.trino.sql.tree.ShowColumns;
import io.trino.sql.tree.ShowCreate;
import io.trino.sql.tree.ShowFunctions;
import io.trino.sql.tree.ShowGrants;
import io.trino.sql.tree.ShowRoleGrants;
import io.trino.sql.tree.ShowRoles;
import io.trino.sql.tree.ShowSchemas;
import io.trino.sql.tree.ShowSession;
import io.trino.sql.tree.ShowStats;
import io.trino.sql.tree.ShowTables;
import io.trino.sql.tree.StartTransaction;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.TableExecute;
import io.trino.sql.tree.TruncateTable;
import io.trino.sql.tree.Update;
import io.trino.sql.tree.Use;

import java.util.Map;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.trino.gateway.ha.router.QueryType.ALTER_TABLE_EXECUTE;
import static io.trino.gateway.ha.router.QueryType.ANALYZE;
import static io.trino.gateway.ha.router.QueryType.DATA_DEFINITION;
import static io.trino.gateway.ha.router.QueryType.DELETE;
import static io.trino.gateway.ha.router.QueryType.DESCRIBE;
import static io.trino.gateway.ha.router.QueryType.EXPLAIN;
import static io.trino.gateway.ha.router.QueryType.INSERT;
import static io.trino.gateway.ha.router.QueryType.MERGE;
import static io.trino.gateway.ha.router.QueryType.SELECT;
import static io.trino.gateway.ha.router.QueryType.UPDATE;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

//modified version of io.trino.util.StatementUtils
public final class StatementUtils
{
    private static final Logger log = Logger.get(StatementUtils.class);

    private StatementUtils() {}

    private static final Map<Class<? extends Statement>, StatementTypeInfo<? extends Statement>> STATEMENT_QUERY_TYPES = ImmutableList.<StatementTypeInfo<?>>builder()
            // SELECT
            .add(basicStatement(Query.class, SELECT))
            // EXPLAIN
            .add(basicStatement(Explain.class, EXPLAIN))
            // DESCRIBE
            .add(basicStatement(DescribeInput.class, DESCRIBE))
            .add(basicStatement(DescribeOutput.class, DESCRIBE))
            .add(basicStatement(ShowCatalogs.class, DESCRIBE))
            .add(basicStatement(ShowColumns.class, DESCRIBE))
            .add(basicStatement(ShowCreate.class, DESCRIBE))
            .add(basicStatement(ShowFunctions.class, DESCRIBE))
            .add(basicStatement(ShowGrants.class, DESCRIBE))
            .add(basicStatement(ShowRoleGrants.class, DESCRIBE))
            .add(basicStatement(ShowRoles.class, DESCRIBE))
            .add(basicStatement(ShowSchemas.class, DESCRIBE))
            .add(basicStatement(ShowSession.class, DESCRIBE))
            .add(basicStatement(ShowStats.class, DESCRIBE))
            .add(basicStatement(ShowTables.class, DESCRIBE))
            // Table Procedure
            .add(basicStatement(TableExecute.class, ALTER_TABLE_EXECUTE))
            // DML
            .add(basicStatement(CreateTableAsSelect.class, INSERT))
            .add(basicStatement(RefreshMaterializedView.class, INSERT))
            .add(basicStatement(Insert.class, INSERT))
            .add(basicStatement(Update.class, UPDATE))
            .add(basicStatement(Delete.class, DELETE))
            .add(basicStatement(Merge.class, MERGE))
            .add(basicStatement(Analyze.class, ANALYZE))
            // DDL
            .add(basicStatement(AddColumn.class, DATA_DEFINITION))
            .add(basicStatement(Call.class, DATA_DEFINITION))
            .add(basicStatement(Comment.class, DATA_DEFINITION))
            .add(basicStatement(Commit.class, DATA_DEFINITION))
            .add(basicStatement(CreateMaterializedView.class, DATA_DEFINITION))
            .add(basicStatement(CreateCatalog.class, DATA_DEFINITION))
            .add(basicStatement(CreateFunction.class, DATA_DEFINITION))
            .add(basicStatement(CreateRole.class, DATA_DEFINITION))
            .add(basicStatement(CreateSchema.class, DATA_DEFINITION))
            .add(basicStatement(CreateTable.class, DATA_DEFINITION))
            .add(basicStatement(CreateView.class, DATA_DEFINITION))
            .add(basicStatement(Deallocate.class, DATA_DEFINITION))
            .add(basicStatement(Deny.class, DATA_DEFINITION))
            .add(basicStatement(DropCatalog.class, DATA_DEFINITION))
            .add(basicStatement(DropColumn.class, DATA_DEFINITION))
            .add(basicStatement(DropFunction.class, DATA_DEFINITION))
            .add(basicStatement(DropMaterializedView.class, DATA_DEFINITION))
            .add(basicStatement(DropRole.class, DATA_DEFINITION))
            .add(basicStatement(DropSchema.class, DATA_DEFINITION))
            .add(basicStatement(DropTable.class, DATA_DEFINITION))
            .add(basicStatement(DropView.class, DATA_DEFINITION))
            .add(basicStatement(TruncateTable.class, DATA_DEFINITION))
            .add(basicStatement(Grant.class, DATA_DEFINITION))
            .add(basicStatement(GrantRoles.class, DATA_DEFINITION))
            .add(basicStatement(Prepare.class, DATA_DEFINITION))
            .add(basicStatement(RenameColumn.class, DATA_DEFINITION))
            .add(basicStatement(RenameMaterializedView.class, DATA_DEFINITION))
            .add(basicStatement(RenameSchema.class, DATA_DEFINITION))
            .add(basicStatement(RenameTable.class, DATA_DEFINITION))
            .add(basicStatement(RenameView.class, DATA_DEFINITION))
            .add(basicStatement(ResetSession.class, DATA_DEFINITION))
            .add(basicStatement(ResetSessionAuthorization.class, DATA_DEFINITION))
            .add(basicStatement(Revoke.class, DATA_DEFINITION))
            .add(basicStatement(RevokeRoles.class, DATA_DEFINITION))
            .add(basicStatement(Rollback.class, DATA_DEFINITION))
            .add(basicStatement(SetColumnType.class, DATA_DEFINITION))
            .add(basicStatement(DropNotNullConstraint.class, DATA_DEFINITION))
            .add(basicStatement(SetPath.class, DATA_DEFINITION))
            .add(basicStatement(SetRole.class, DATA_DEFINITION))
            .add(basicStatement(SetSchemaAuthorization.class, DATA_DEFINITION))
            .add(basicStatement(SetSession.class, DATA_DEFINITION))
            .add(basicStatement(SetSessionAuthorization.class, DATA_DEFINITION))
            .add(basicStatement(SetProperties.class, DATA_DEFINITION))
            .add(basicStatement(SetTableAuthorization.class, DATA_DEFINITION))
            .add(basicStatement(SetTimeZone.class, DATA_DEFINITION))
            .add(basicStatement(SetViewAuthorization.class, DATA_DEFINITION))
            .add(basicStatement(StartTransaction.class, DATA_DEFINITION))
            .add(basicStatement(Use.class, DATA_DEFINITION))
            .build().stream()
            .collect(toImmutableMap(StatementTypeInfo::getStatementType, identity()));

    public static String getResourceGroupQueryType(Statement statement)
    {
        if (statement instanceof ExplainAnalyze explainAnalyze) {
            return getResourceGroupQueryType(explainAnalyze.getStatement());
        }
        StatementTypeInfo<? extends Statement> statementTypeInfo = STATEMENT_QUERY_TYPES.get(statement.getClass());
        if (statementTypeInfo != null) {
            return statementTypeInfo.getQueryType().toString();
        }
        log.warn("Unsupported statement type: %s", statement.getClass());
        return "UNKNOWN";
    }

    private static <T extends Statement> StatementTypeInfo<T> basicStatement(Class<T> statementType, QueryType queryType)
    {
        return new StatementTypeInfo<>(statementType, queryType);
    }

    private static class StatementTypeInfo<T extends Statement>
    {
        private final Class<T> statementType;
        private final QueryType queryType;

        private StatementTypeInfo(Class<T> statementType,
                QueryType queryType)
        {
            this.statementType = requireNonNull(statementType, "statementType is null");
            this.queryType = requireNonNull(queryType, "queryType is null");
        }

        public Class<T> getStatementType()
        {
            return statementType;
        }

        public QueryType getQueryType()
        {
            return queryType;
        }
    }
}
