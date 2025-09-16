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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.compress.v3.zstd.ZstdDecompressor;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.trino.sql.parser.ParsingException;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.AddColumn;
import io.trino.sql.tree.Analyze;
import io.trino.sql.tree.Call;
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
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.Identifier;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.NodeLocation;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Query;
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
import io.trino.sql.tree.StringLiteral;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.TableFunctionInvocation;
import io.trino.sql.tree.WithQuery;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.BaseEncoding.base64Url;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class TrinoQueryProperties
{
    public static final String TRINO_CATALOG_HEADER_NAME = "X-Trino-Catalog";
    public static final String TRINO_SCHEMA_HEADER_NAME = "X-Trino-Schema";
    public static final String TRINO_PREPARED_STATEMENT_HEADER_NAME = "X-Trino-Prepared-Statement";

    private final Logger log = Logger.get(TrinoQueryProperties.class);
    private final boolean isClientsUseV2Format;
    private final int maxBodySize;
    private final Optional<String> defaultCatalog;
    private final Optional<String> defaultSchema;
    private final ZstdDecompressor decompressor = ZstdDecompressor.create();

    private String body = "";
    private String queryType = "";
    private String resourceGroupQueryType = "";
    private Set<QualifiedName> tables = ImmutableSet.of();
    private Set<String> catalogs = ImmutableSet.of();
    private Set<String> schemas = ImmutableSet.of();
    private Set<String> catalogSchemas = ImmutableSet.of();
    private boolean isNewQuerySubmission;
    private Optional<String> errorMessage = Optional.empty();
    private Optional<String> queryId = Optional.empty();

    @JsonCreator
    public TrinoQueryProperties(
            @JsonProperty("body") String body,
            @JsonProperty("queryType") String queryType,
            @JsonProperty("resourceGroupQueryType") String resourceGroupQueryType,
            @JsonProperty("tables") List<String> tables,
            @JsonProperty("defaultCatalog") Optional<String> defaultCatalog,
            @JsonProperty("defaultSchema") Optional<String> defaultSchema,
            @JsonProperty("catalogs") Set<String> catalogs,
            @JsonProperty("schemas") Set<String> schemas,
            @JsonProperty("catalogSchemas") Set<String> catalogSchemas,
            @JsonProperty("isNewQuerySubmission") boolean isNewQuerySubmission,
            @JsonProperty("errorMessage") Optional<String> errorMessage)
    {
        this.body = requireNonNullElse(body, "");
        this.queryType = requireNonNullElse(queryType, "");
        this.resourceGroupQueryType = resourceGroupQueryType;
        List<String> defaultTables = ImmutableList.of();
        this.tables = requireNonNullElse(tables, defaultTables).stream().map(this::parseIdentifierStringToQualifiedName).collect(Collectors.toSet());
        this.defaultCatalog = requireNonNullElse(defaultCatalog, Optional.empty());
        this.defaultSchema = requireNonNullElse(defaultSchema, Optional.empty());
        this.catalogs = requireNonNullElse(catalogs, ImmutableSet.of());
        this.schemas = requireNonNullElse(schemas, ImmutableSet.of());
        this.catalogSchemas = requireNonNullElse(catalogSchemas, ImmutableSet.of());
        this.isNewQuerySubmission = isNewQuerySubmission;
        this.errorMessage = requireNonNullElse(errorMessage, Optional.empty());
        isClientsUseV2Format = false;
        maxBodySize = -1;
    }

    public TrinoQueryProperties()
    {
        this("", "", "", ImmutableList.of(), Optional.empty(), Optional.empty(),
                ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), false, Optional.empty());
    }

    public TrinoQueryProperties(ContainerRequestContext requestContext, boolean isClientsUseV2Format, int maxBodySize)
    {
        requireNonNull(requestContext, "requestContext is null");
        this.isClientsUseV2Format = isClientsUseV2Format;
        this.maxBodySize = maxBodySize;

        defaultCatalog = Optional.ofNullable(requestContext.getHeaderString(TRINO_CATALOG_HEADER_NAME));
        defaultSchema = Optional.ofNullable(requestContext.getHeaderString(TRINO_SCHEMA_HEADER_NAME));
        if (requestContext.getMethod().equals(HttpMethod.POST)) {
            isNewQuerySubmission = true;
            processRequestBody(requestContext);
        }
    }

    private void processRequestBody(BufferedReader reader, Map<String, String> preparedStatements)
    {
        try (reader) {
            if (reader == null) {
                log.warn("HTTP request returned null reader");
                body = "";
                return;
            }

            SqlParser parser = new SqlParser();
            reader.mark(maxBodySize);
            char[] buffer = new char[maxBodySize];
            int nChars = reader.read(buffer, 0, maxBodySize);
            reader.reset();
            if (nChars <= 0) {
                log.warn("query text is empty");
                return;
            }
            if (nChars == maxBodySize) {
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
            resourceGroupQueryType = StatementUtils.getResourceGroupQueryType(statement);
            ImmutableSet.Builder<QualifiedName> tableBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> catalogBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> schemaBuilder = ImmutableSet.builder();
            ImmutableSet.Builder<String> catalogSchemaBuilder = ImmutableSet.builder();
            Set<QualifiedName> temporaryTables = new HashSet<>();

            visitNode(statement, tableBuilder, catalogBuilder, schemaBuilder, catalogSchemaBuilder, temporaryTables);
            tables = tableBuilder.build();
            catalogBuilder.addAll(tables.stream().map(q -> q.getParts().getFirst()).iterator());
            catalogs = catalogBuilder.build();
            schemaBuilder.addAll(tables.stream().map(q -> q.getParts().get(1)).iterator());
            schemas = schemaBuilder.build();
            catalogSchemaBuilder.addAll(
                    tables.stream().map(qualifiedName -> format("%s.%s", qualifiedName.getParts().getFirst(), qualifiedName.getParts().get(1))).iterator());
            catalogSchemas = catalogSchemaBuilder.build();
        }
        catch (IOException e) {
            log.warn("Error extracting request body for rules processing: %s", e.getMessage());
            errorMessage = Optional.of(e.getMessage());
        }
        catch (ParsingException e) {
            log.info("Could not parse request body as SQL: %s; Message: %s", body, e.getMessage());
            errorMessage = Optional.of(e.getMessage());
        }
        catch (RequestParsingException e) {
            log.warn(e, "Error parsing request for rules");
            errorMessage = Optional.of(e.getMessage());
        }
    }

    private void processRequestBody(ContainerRequestContext requestContext)
    {
        if (!requestContext.hasEntity()) {
            return;
        }

        MediaType mediaType = requestContext.getMediaType();
        if (mediaType == null) {
            return;
        }

        String charset = mediaType.getParameters().get("charset");
        if (charset == null) {
            log.debug("charset is not set in the request");
            return;
        }

        if (!UTF_8.name().equalsIgnoreCase(charset)) {
            log.debug("Request charset is not UTF-8 (%s), skipping query parsing", charset);
            return;
        }

        InputStream entityStream = requestContext.getEntityStream();
        try (InputStreamReader entityReader = new InputStreamReader(entityStream, UTF_8);
                BufferedReader reader = new BufferedReader(entityReader)) {
            processRequestBody(reader, getPreparedStatements(requestContext));
        }
        catch (IOException e) {
            log.warn("Error extracting request body for rules processing: %s", e.getMessage());
            errorMessage = Optional.of(e.getMessage());
        }
        catch (ParsingException e) {
            log.info("Could not parse request body as SQL: %s; Message: %s", body, e.getMessage());
            errorMessage = Optional.of(e.getMessage());
        }
        catch (RequestParsingException e) {
            log.warn(e, "Error parsing request for rules");
            errorMessage = Optional.of(e.getMessage());
        }
    }

    private Map<String, String> getPreparedStatements(Enumeration<String> headers)
            throws RequestParsingException
    {
        ImmutableMap.Builder<String, String> preparedStatementsMapBuilder = ImmutableMap.builder();
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

    private Map<String, String> getPreparedStatements(ContainerRequestContext requestContext)
            throws RequestParsingException
    {
        if (requestContext.getHeaders() == null) {
            return ImmutableMap.of();
        }
        List<String> headers = requestContext.getHeaders().get(TRINO_PREPARED_STATEMENT_HEADER_NAME);
        if (headers == null || headers.isEmpty()) {
            return ImmutableMap.of();
        }
        return getPreparedStatements(Collections.enumeration(headers));
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

        byte[] preparedStatement = new byte[toIntExact(decompressor.getDecompressedSize(compressed, 0, compressed.length))];
        decompressor.decompress(compressed, 0, compressed.length, preparedStatement, 0, preparedStatement.length);
        return new String(preparedStatement, UTF_8);
    }

    private void visitNode(Node node, ImmutableSet.Builder<QualifiedName> tableBuilder,
            ImmutableSet.Builder<String> catalogBuilder,
            ImmutableSet.Builder<String> schemaBuilder,
            ImmutableSet.Builder<String> catalogSchemaBuilder,
            Set<QualifiedName> temporaryTables)
            throws RequestParsingException
    {
        switch (node) {
            case AddColumn s -> tableBuilder.add(qualifyName(s.getName()));
            case Analyze s -> tableBuilder.add(qualifyName(s.getTableName()));
            case Call call -> queryId = extractQueryIdFromCall(call);
            case CreateCatalog s -> catalogBuilder.add(s.getCatalogName().getValue());
            case CreateMaterializedView s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateSchema s -> setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSchemaName()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case CreateTable s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateView s -> tableBuilder.add(qualifyName(s.getName()));
            case CreateTableAsSelect s -> tableBuilder.add(qualifyName(s.getName()));
            case DropCatalog s -> catalogBuilder.add(s.getCatalogName().getValue());
            case DropSchema s -> setCatalogAndSchemaNameFromSchemaQualifiedName(Optional.of(s.getSchemaName()), catalogBuilder, schemaBuilder, catalogSchemaBuilder);
            case DropTable s -> tableBuilder.add(qualifyName(s.getTableName()));
            case Query q -> q.getWith().ifPresent(with -> temporaryTables.addAll(with.getQueries().stream().map(WithQuery::getName).map(Identifier::getValue).map(QualifiedName::of).toList()));
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
                        errorMessage = Optional.of("defaultCatalog is not present");
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
            case Table s -> {
                // ignore temporary tables as they can have various table parts
                if (!temporaryTables.contains(s.getName())) {
                    tableBuilder.add(qualifyName(s.getName()));
                }
            }
            case TableFunctionInvocation s -> tableBuilder.add(qualifyName(s.getName()));
            default -> {}
        }

        for (Node child : node.getChildren()) {
            visitNode(child, tableBuilder, catalogBuilder, schemaBuilder, catalogSchemaBuilder, temporaryTables);
        }
    }

    private Optional<String> extractQueryIdFromCall(Call call)
            throws RequestParsingException
    {
        QualifiedName callName = qualifyName(call.getName());
        if (callName.equals(QualifiedName.of("system", "runtime", "kill_query"))) {
            Expression argument = call.getArguments().getFirst().getValue();
            checkArgument(argument instanceof StringLiteral, "Unable to route kill_query procedures where the first argument is not a String Literal");
            return Optional.of(((StringLiteral) argument).getValue());
        }
        return Optional.empty();
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
        return new RequestParsingException("Name not fully qualified");
    }

    private QualifiedName qualifyName(QualifiedName name)
            throws RequestParsingException
    {
        List<String> nameParts = name.getParts();
        return switch (nameParts.size()) {
            case 1 -> QualifiedName.of(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier), defaultSchema.orElseThrow(this::unsetDefaultExceptionSupplier), nameParts.getFirst());
            case 2 -> QualifiedName.of(defaultCatalog.orElseThrow(this::unsetDefaultExceptionSupplier), nameParts.getFirst(), nameParts.get(1));
            case 3 -> QualifiedName.of(nameParts.getFirst(), nameParts.get(1), nameParts.get(2));
            default -> throw new RequestParsingException("Unexpected qualified name: " + name.getParts());
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
        return errorMessage.isEmpty();
    }

    @JsonProperty
    public Optional<String> getErrorMessage()
    {
        return errorMessage;
    }

    @JsonIgnore
    public Optional<String> getQueryId()
    {
        return queryId;
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
