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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.sql.parser.ParsingException;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.AddColumn;
import io.trino.sql.tree.Analyze;
import io.trino.sql.tree.CreateCatalog;
import io.trino.sql.tree.CreateMaterializedView;
import io.trino.sql.tree.CreateSchema;
import io.trino.sql.tree.CreateTable;
import io.trino.sql.tree.CreateTableAsSelect;
import io.trino.sql.tree.CreateView;
import io.trino.sql.tree.DropCatalog;
import io.trino.sql.tree.DropSchema;
import io.trino.sql.tree.DropTable;
import io.trino.sql.tree.Execute;
import io.trino.sql.tree.ExecuteImmediate;
import io.trino.sql.tree.Identifier;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.NodeLocation;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.RenameMaterializedView;
import io.trino.sql.tree.RenameSchema;
import io.trino.sql.tree.RenameTable;
import io.trino.sql.tree.RenameView;
import io.trino.sql.tree.SetProperties;
import io.trino.sql.tree.SetSchemaAuthorization;
import io.trino.sql.tree.SetTableAuthorization;
import io.trino.sql.tree.SetViewAuthorization;
import io.trino.sql.tree.ShowColumns;
import io.trino.sql.tree.ShowCreate;
import io.trino.sql.tree.ShowSchemas;
import io.trino.sql.tree.ShowTables;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.TableFunctionInvocation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.HttpMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.io.BaseEncoding.base64Url;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class TrinoQueryProperties
{
    private final Logger log = Logger.get(TrinoQueryProperties.class);
    private final boolean isClientsUseV2Format;
    private String body = "";
    private String queryType = "";
    private String resourceGroupQueryType = "";
    private Set<QualifiedName> tables = ImmutableSet.of();
    private final Optional<String> defaultCatalog;
    private final Optional<String> defaultSchema;
    private Set<String> catalogs = ImmutableSet.of();
    private Set<String> schemas = ImmutableSet.of();
    private Set<String> catalogSchemas = ImmutableSet.of();
    private boolean isNewQuerySubmission;
    private boolean isQueryParsingSuccessful;

    public static final String TRINO_CATALOG_HEADER_NAME = "X-Trino-Catalog";
    public static final String TRINO_SCHEMA_HEADER_NAME = "X-Trino-Schema";
    public static final String TRINO_PREPARED_STATEMENT_HEADER_NAME = "X-Trino-Prepared-Statement";

    @JsonCreator
    public TrinoQueryProperties(
            @JsonProperty("body") String body,
            @JsonProperty("queryType") String queryType,
            @JsonProperty("resourceGroupQueryType") String resourceGroupQueryType,
            @JsonProperty("tables") String[] tables,
            @JsonProperty("defaultCatalog") Optional<String> defaultCatalog,
            @JsonProperty("defaultSchema") Optional<String> defaultSchema,
            @JsonProperty("catalogs") Set<String> catalogs,
            @JsonProperty("schemas") Set<String> schemas,
            @JsonProperty("catalogSchemas") Set<String> catalogSchemas,
            @JsonProperty("isNewQuerySubmission") boolean isNewQuerySubmission,
            @JsonProperty("isQueryParsingSuccessful") boolean isQueryParsingSuccessful)
    {
        this.body = requireNonNullElse(body, "");
        this.queryType = requireNonNullElse(queryType, "");
        this.resourceGroupQueryType = resourceGroupQueryType;
        this.tables = Arrays.stream(requireNonNullElse(tables, new String[] {})).map(this::parseIdentifierStringToQualifiedName).collect(Collectors.toSet());
        this.defaultCatalog = requireNonNullElse(defaultCatalog, Optional.empty());
        this.defaultSchema = requireNonNullElse(defaultSchema, Optional.empty());
        this.catalogs = requireNonNullElse(catalogs, ImmutableSet.of());
        this.schemas = requireNonNullElse(schemas, ImmutableSet.of());
        this.catalogSchemas = requireNonNullElse(catalogSchemas, ImmutableSet.of());
        this.isNewQuerySubmission = isNewQuerySubmission;
        this.isQueryParsingSuccessful = isQueryParsingSuccessful;
        isClientsUseV2Format = false;
    }

    public TrinoQueryProperties(HttpServletRequest request, RequestAnalyzerConfig config)
    {
        isClientsUseV2Format = config.isClientsUseV2Format();

        defaultCatalog = Optional.ofNullable(request.getHeader(TRINO_CATALOG_HEADER_NAME));
        defaultSchema = Optional.ofNullable(request.getHeader(TRINO_SCHEMA_HEADER_NAME));
        if (request.getMethod().equals(HttpMethod.POST)) {
            isNewQuerySubmission = true;
            processRequestBody(request, config);
        }
    }

    private void processRequestBody(HttpServletRequest request, RequestAnalyzerConfig config)
    {
        try (BufferedReader reader = request.getReader()) {
            if (reader == null) {
                log.warn("HTTP request returned null reader");
                body = "";
                return;
            }

            Map<String, String> preparedStatements = getPreparedStatements(request);
            SqlParser parser = new SqlParser();
            reader.mark(config.getMaxBodySize());
            char[] buffer = new char[config.getMaxBodySize()];
            int nChars = reader.read(buffer, 0, config.getMaxBodySize());
            reader.reset();
            if (nChars == config.getMaxBodySize()) {
                log.warn("Query length greater or equal to requestAnalyzerConfig.maxBodySize detected");
                return;
                //The body is truncated - there is a chance that it could still be syntactically valid SQL, for example if truncated on
                //whitespace preceding a UNION. Exit out of caution
            }
            body = String.valueOf(buffer, 0, nChars);

            if (isClientsUseV2Format) {
                try {
                    AlternateStatementRequestBodyFormat requestBody = AlternateStatementRequestBodyFormat.ALTERNATE_STATEMENT_FORMAT_CODEC.fromJson(body);
                    body = requestBody.getQuery();
                    preparedStatements = requestBody.getPreparedStatements();
                }
                catch (IllegalArgumentException e) {
                    // Do nothing, request is using standard format
                }
            }

            Statement statement = parser.createStatement(body);
            if (statement.getClass() == Execute.class) {
                String statementName = ((Execute) statement).getName().getValue();
                if (!preparedStatements.containsKey(statementName)) {
                    log.error("No prepared statement matching execute: %s", body);
                    queryType = "Execute";
                    return;
                }
                body = preparedStatements.get(statementName);
                statement = parser.createStatement(body);
            }
            else if (statement instanceof ExecuteImmediate executeImmediate) {
                body = executeImmediate.getStatement().getValue();
                statement = parser.createStatement(body);
            }

            queryType = statement.getClass().getSimpleName();
            resourceGroupQueryType = StatementUtils.getQueryType(statement).toString();
            ImmutableSet.Builder<QualifiedName> tableBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> catalogBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> schemaBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> catalogSchemaBuilder = ImmutableSet.builder();

            getNames(statement, tableBuilder, catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            tables = tableBuilder.build();
            catalogBuilder.addAll(tables.stream().map(q -> q.getParts().getFirst()).iterator());
            catalogs = catalogBuilder.build();
            schemaBuilder.addAll(tables.stream().map(q -> q.getParts().get(1)).iterator());
            schemas = schemaBuilder.build();
            catalogSchemaBuilder.addAll(
                    tables.stream().map(qualifiedName -> format("%s.%s", qualifiedName.getParts().getFirst(), qualifiedName.getParts().get(1))).iterator());
            catalogSchemas = catalogSchemaBuilder.build();
            isQueryParsingSuccessful = true;
        }
        catch (IOException e) {
            log.warn("Error extracting request body for rules processing: %s", e.getMessage());
            isQueryParsingSuccessful = false;
        }
        catch (ParsingException e) {
            log.info("Could not parse request body as SQL: %s; Message: %s", body, e.getMessage());
            isQueryParsingSuccessful = false;
        }
        catch (RequestParsingException e) {
            log.warn(e, "Error parsing request for rules");
            isQueryParsingSuccessful = false;
        }
    }

    private Map<String, String> getPreparedStatements(HttpServletRequest request)
            throws RequestParsingException
    {
        ImmutableMap.Builder<String, String> preparedStatementsMapBuilder = ImmutableMap.builder();
        Enumeration<String> headers = request.getHeaders(TRINO_PREPARED_STATEMENT_HEADER_NAME);
        if (headers == null) {
            return preparedStatementsMapBuilder.build();
        }
        while (headers.hasMoreElements()) {
            String[] preparedStatementsArray = headers.nextElement().split(",");
            for (String preparedStatement : preparedStatementsArray) {
                String[] nameValue = preparedStatement.split("=");
                if (nameValue.length != 2) {
                    throw new RequestParsingException(format("preparedStatement must be formatted as name=value, but is %s", preparedStatement));
                }
                preparedStatementsMapBuilder.put(URLDecoder.decode(nameValue[0], UTF_8), URLDecoder.decode(decodePreparedStatementFromHeader(nameValue[1]), UTF_8));
            }
        }
        return preparedStatementsMapBuilder.build();
    }

    private String decodePreparedStatementFromHeader(String headerValue)
    {
        // From io.trino.server.protocol.PreparedStatementEncoder
        String prefix = "$zstd:";
        if (!headerValue.startsWith(prefix)) {
            return headerValue;
        }

        String encoded = headerValue.substring(prefix.length());
        byte[] compressed = base64Url().decode(encoded);

        byte[] preparedStatement = new byte[toIntExact(ZstdDecompressor.getDecompressedSize(compressed, 0, compressed.length))];
        new ZstdDecompressor().decompress(compressed, 0, compressed.length, preparedStatement, 0, preparedStatement.length);
        return new String(preparedStatement, UTF_8);
    }

    private void getNames(Node node, ImmutableSet.Builder<QualifiedName> tableBuilder,
            ImmutableSet.Builder<String> catalogBuilder,
            ImmutableSet.Builder<String> schemaBuilder,
            ImmutableSet.Builder<String> catalogSchemaBuilder)
            throws RequestParsingException
    {
        switch (node) {
            case AddColumn s -> tableBuilder.add(qualifyName(s.getName()));
            case Analyze s -> tableBuilder.add(qualifyName(s.getTableName()));
            case CreateCatalog s -> catalogBuilder.add(s.getCatalogName().getValue());
            case CreateMaterializedView s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateSchema s -> setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSchemaName()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case CreateTable s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateView s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateTableAsSelect s -> tableBuilder.add(qualifyName(s.getName()));
            case DropCatalog s -> catalogBuilder.add(s.getCatalogName().getValue());
            case DropSchema s -> setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSchemaName()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case DropTable s -> tableBuilder.add(qualifyName(s.getTableName()));
            case RenameMaterializedView s -> {
                tableBuilder.add(qualifyName(s.getSource()));
                tableBuilder.add(qualifyName(s.getTarget()));
            }
            case RenameSchema s -> {
                setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSource()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
                QualifiedName targetSchema;
                if (s.getSource().getParts().size() == 1) {
                    if (defaultCatalog.isPresent()) {
                        targetSchema = QualifiedName.of(defaultCatalog.orElseThrow(), s.getTarget().getValue());
                    }
                    else {
                        isQueryParsingSuccessful = false;
                        return;
                    }
                }
                else {
                    targetSchema = QualifiedName.of(s.getSource().getParts().getFirst(), s.getTarget().getValue());
                }
                setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(targetSchema), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            }
            case RenameTable s -> {
                QualifiedName qualifiedSource = qualifyName(s.getSource());
                tableBuilder.add(qualifiedSource);
                QualifiedName target = s.getTarget();
                if (target.getParts().size() == 1) {
                    tableBuilder.add(QualifiedName.of(qualifiedSource.getParts().getFirst(), qualifiedSource.getParts().get(1), target.getParts().getFirst()));
                }
                else {
                    tableBuilder.add(QualifiedName.of(qualifiedSource.getParts().getFirst(), target.getParts().getFirst(), target.getParts().get(1)));
                }
            }
            case RenameView s -> {
                QualifiedName qualifiedSource = qualifyName(s.getSource());
                tableBuilder.add(qualifiedSource);
                QualifiedName target = s.getTarget();
                if (target.getParts().size() == 1) {
                    tableBuilder.add(QualifiedName.of(qualifiedSource.getParts().getFirst(), qualifiedSource.getParts().get(1), target.getParts().getFirst()));
                }
                else {
                    tableBuilder.add(QualifiedName.of(qualifiedSource.getParts().getFirst(), target.getParts().getFirst(), target.getParts().get(1)));
                }
            }
            case SetProperties s -> tableBuilder.add(qualifyName(s.getName()));
            case ShowColumns s -> tableBuilder.add(qualifyName(s.getTable()));
            case ShowCreate s -> {
                if (s.getType() == ShowCreate.Type.SCHEMA) {
                    setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getName()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
                }
                else {
                    tableBuilder.add(qualifyName(s.getName()));
                }
            }
            case ShowSchemas s -> catalogBuilder.add(s.getCatalog().map(Identifier::getValue).or(() -> defaultCatalog).orElseThrow(this::unsetDefaultExceptionSupplier));
            case ShowTables s -> setCatalogAndSchemaNameFromSchemaQualifiedName(s.getSchema(), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case SetSchemaAuthorization s -> setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSource()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case SetTableAuthorization s -> tableBuilder.add(qualifyName(s.getSource()));
            case SetViewAuthorization s -> tableBuilder.add(qualifyName(s.getSource()));
            case Table s -> tableBuilder.add(qualifyName(s.getName()));
            case TableFunctionInvocation s -> tableBuilder.add(qualifyName(s.getName()));
            default -> {}
        }

        for (Node child : node.getChildren()) {
            getNames(child, tableBuilder, catalogBuilder, schemaBuilder, catalogSchemaBuilder);
        }
    }

    private void setCatalogAndSchemaNameFromSchemaQualifiedName(
            Optional<QualifiedName> schemaOptional,
            ImmutableSet.Builder<String> catalogBuilder,
            ImmutableSet.Builder<String> schemaBuilder,
            ImmutableSet.Builder<String> catalogSchemaBuilder)
            throws RequestParsingException
    {
        if (schemaOptional.isEmpty()) {
            schemaBuilder.add(defaultSchema.orElseThrow(this::unsetDefaultExceptionSupplier));
            catalogBuilder.add(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier));
            catalogSchemaBuilder.add(format("%s.%s", defaultCatalog, defaultSchema));
        }
        else {
            QualifiedName schema = schemaOptional.orElseThrow();
            switch (schema.getParts().size()) {
                case 1 -> {
                    schemaBuilder.add(schema.getParts().getFirst());
                    catalogBuilder.add(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier));
                    catalogSchemaBuilder.add(format("%s.%s", defaultCatalog, schema.getParts().getFirst()));
                }
                case 2 -> {
                    schemaBuilder.add(schema.getParts().get(1));
                    catalogBuilder.add(schema.getParts().getFirst());
                    catalogSchemaBuilder.add(format("%s.%s", schema.getParts().getFirst(), schema.getParts().getLast()));
                }
                default -> log.error("Schema has >2 parts: %s", schema);
            }
        }
    }

    private RequestParsingException unsetDefaultExceptionSupplier()
    {
        isQueryParsingSuccessful = false;
        return new RequestParsingException("Name not fully qualified");
    }

    private QualifiedName qualifyName(QualifiedName table)
            throws RequestParsingException
    {
        List<String> tableParts = table.getParts();
        return switch (tableParts.size()) {
            case 1 -> QualifiedName.of(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier), defaultSchema.orElseThrow(this::unsetDefaultExceptionSupplier), tableParts.getFirst());
            case 2 -> QualifiedName.of(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier), tableParts.getFirst(), tableParts.get(1));
            case 3 -> QualifiedName.of(tableParts.getFirst(), tableParts.get(1), tableParts.get(2));
            default -> throw new RequestParsingException("Unexpected table name: " + table.getParts());
        };
    }

    @JsonProperty
    public String getBody()
    {
        return body;
    }

    @JsonProperty
    public String getQueryType()
    {
        return queryType;
    }

    @JsonProperty
    public String getResourceGroupQueryType()
    {
        return resourceGroupQueryType;
    }

    @JsonProperty
    public Optional<String> getDefaultSchema()
    {
        return defaultSchema;
    }

    @JsonSerialize(using = QualifiedNameJsonSerializer.class)
    public Set<QualifiedName> getTables()
    {
        return tables;
    }

    private QualifiedName parseIdentifierStringToQualifiedName(String name)
    {
        char dot = '.';
        char quote = '"';
        List<Identifier> parts = new ArrayList<>();
        int start = 0;
        boolean inQuotes = false;
        boolean partQuoted = false;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == quote) {
                if (!inQuotes) {
                    if (i != start) {
                        log.error("Illegal position for first quote character in table name: %s", name);
                        throw new ParsingException(format("Illegal position for first quote character in table name: %s", name), new NodeLocation(1, i));
                    }
                    start = start + 1;
                    partQuoted = true;
                }
                if (inQuotes && name.charAt(i - 1) == '\\') {
                    continue;
                }

                inQuotes = !inQuotes;
                continue;
            }
            if (name.charAt(i) == dot && !inQuotes) {
                if (partQuoted) {
                    parts.add(new Identifier(name.substring(start, i - 1)));
                }
                else {
                    parts.add(new Identifier(name.substring(start, i)));
                }
                start = i + 1;
                partQuoted = false;
            }
        }
        if (partQuoted) {
            parts.add(new Identifier(name.substring(start, name.length() - 1)));
        }
        else {
            parts.add(new Identifier(name.substring(start, name.length())));
        }
        return QualifiedName.of(parts);
    }

    public boolean tablesContains(String testName)
    {
        try {
            return tables.contains(parseIdentifierStringToQualifiedName(testName));
        }
        catch (ParsingException e) {
            return false;
        }
    }

    @JsonProperty
    public Optional<String> getDefaultCatalog()
    {
        return defaultCatalog;
    }

    @JsonProperty
    public Set<String> getCatalogs()
    {
        return catalogs;
    }

    @JsonProperty
    public Set<String> getSchemas()
    {
        return schemas;
    }

    @JsonProperty
    public Set<String> getCatalogSchemas()
    {
        return catalogSchemas;
    }

    @JsonProperty("isNewQuerySubmission")
    public boolean isNewQuerySubmission()
    {
        return isNewQuerySubmission;
    }

    @JsonProperty("isQueryParsingSuccessful")
    public boolean isQueryParsingSuccessful()
    {
        return isQueryParsingSuccessful;
    }

    public static class AlternateStatementRequestBodyFormat
    {
        // Based on https://github.com/trinodb/trino/wiki/trino-v2-client-protocol, without session
        // This is known to be used by some commercial extensions of Trino, but is not implemented in Trinodb Trino
        private static final JsonCodec<AlternateStatementRequestBodyFormat> ALTERNATE_STATEMENT_FORMAT_CODEC = jsonCodec(AlternateStatementRequestBodyFormat.class);

        private String query;
        private Map<String, String> preparedStatements;

        @JsonCreator
        public AlternateStatementRequestBodyFormat(
                @JsonProperty("query") String query,
                @JsonProperty("preparedStatements") Map<String, String> preparedStatements)
        {
            this.query = requireNonNull(query, "query is null");
            this.preparedStatements = ImmutableMap.copyOf(requireNonNull(preparedStatements, "preparedStatements is null"));
        }

        public String getQuery()
        {
            return query;
        }

        public void setQuery(String query)
        {
            this.query = query;
        }

        public Map<String, String> getPreparedStatements()
        {
            return preparedStatements;
        }

        public void setPreparedStatements(Map<String, String> preparedStatements)
        {
            this.preparedStatements = preparedStatements;
        }
    }

    public static class RequestParsingException
            extends Exception
    {
        public RequestParsingException(String message)
        {
            super(message);
        }
    }

    public static class QualifiedNameJsonSerializer
            extends StdSerializer<Set<QualifiedName>>
    {
        public QualifiedNameJsonSerializer()
        {
            this(null);
        }

        public QualifiedNameJsonSerializer(Class<Set<QualifiedName>> t)
        {
            super(t);
        }

        @Override
        public void serialize(Set<QualifiedName> qualifiedNames, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException
        {
            jsonGenerator.writeArray(qualifiedNames.stream().map(QualifiedName::toString).toList().toArray(new String[0]), 0, qualifiedNames.size());
        }
    }
}
