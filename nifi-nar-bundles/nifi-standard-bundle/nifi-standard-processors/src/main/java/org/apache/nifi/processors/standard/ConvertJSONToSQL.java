/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.standard;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;

@SideEffectFree
@SupportsBatching
@SeeAlso(PutSQL.class)
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"json", "sql", "database", "rdbms", "insert", "update", "relational", "flat"})
@CapabilityDescription("Converts a JSON-formatted FlowFile into an UPDATE or INSERT SQL statement. The incoming FlowFile is expected to be "
        + "\"flat\" JSON message, meaning that it consists of a single JSON element and each field maps to a simple type. If a field maps to "
        + "a JSON object, that JSON object will be interpreted as Text. If the input is an array of JSON elements, each element in the array is "
        + "output as a separate FlowFile to the 'sql' relationship. Upon successful conversion, the original FlowFile is routed to the 'original' "
        + "relationship and the SQL is routed to the 'sql' relationship.")
@WritesAttributes({
        @WritesAttribute(attribute="mime.type", description="Sets mime.type of FlowFile that is routed to 'sql' to 'text/plain'."),
        @WritesAttribute(attribute="sql.table", description="Sets the sql.table attribute of FlowFile that is routed to 'sql' to the name of the table that is updated by the SQL statement."),
        @WritesAttribute(attribute="sql.catalog", description="If the Catalog name is set for this database, specifies the name of the catalog that the SQL statement will update. "
                + "If no catalog is used, this attribute will not be added."),
        @WritesAttribute(attribute="fragment.identifier", description="All FlowFiles routed to the 'sql' relationship for the same incoming FlowFile (multiple will be output for the same incoming "
                + "FlowFile if the incoming FlowFile is a JSON Array) will have the same value for the fragment.identifier attribute. This can then be used to correlate the results."),
        @WritesAttribute(attribute="fragment.count", description="The number of SQL FlowFiles that were produced for same incoming FlowFile. This can be used in conjunction with the "
                + "fragment.identifier attribute in order to know how many FlowFiles belonged to the same incoming FlowFile."),
        @WritesAttribute(attribute="fragment.index", description="The position of this FlowFile in the list of outgoing FlowFiles that were all derived from the same incoming FlowFile. This can be "
                + "used in conjunction with the fragment.identifier and fragment.count attributes to know which FlowFiles originated from the same incoming FlowFile and in what order the SQL "
                + "FlowFiles were produced"),
        @WritesAttribute(attribute="sql.args.N.type", description="The output SQL statements are parameterized in order to avoid SQL Injection Attacks. The types of the Parameters "
                + "to use are stored in attributes named sql.args.1.type, sql.args.2.type, sql.args.3.type, and so on. The type is a number representing a JDBC Type constant. "
                + "Generally, this is useful only for software to read and interpret but is added so that a processor such as PutSQL can understand how to interpret the values."),
        @WritesAttribute(attribute="sql.args.N.value", description="The output SQL statements are parameterized in order to avoid SQL Injection Attacks. The values of the Parameters "
                + "to use are stored in the attributes named sql.args.1.value, sql.args.2.value, sql.args.3.value, and so on. Each of these attributes has a corresponding "
                + "sql.args.N.type attribute that indicates how the value should be interpreted when inserting it into the database.")
})
public class ConvertJSONToSQL extends AbstractProcessor {
    private static final String UPDATE_TYPE = "UPDATE";
    private static final String INSERT_TYPE = "INSERT";

    static final AllowableValue IGNORE_UNMATCHED_FIELD = new AllowableValue("Ignore Unmatched Fields", "Ignore Unmatched Fields",
            "Any field in the JSON document that cannot be mapped to a column in the database is ignored");
    static final AllowableValue FAIL_UNMATCHED_FIELD = new AllowableValue("Fail", "Fail",
        "If the JSON document has any field that cannot be mapped to a column in the database, the FlowFile will be routed to the failure relationship");
    static final AllowableValue IGNORE_UNMATCHED_COLUMN = new AllowableValue("Ignore Unmatched Columns",
            "Ignore Unmatched Columns",
            "Any column in the database that does not have a field in the JSON document will be assumed to not be required.  No notification will be logged");
    static final AllowableValue WARNING_UNMATCHED_COLUMN = new AllowableValue("Warn on Unmatched Columns",
            "Warn on Unmatched Columns",
            "Any column in the database that does not have a field in the JSON document will be assumed to not be required.  A warning will be logged");
    static final AllowableValue FAIL_UNMATCHED_COLUMN = new AllowableValue("Fail on Unmatched Columns",
            "Fail on Unmatched Columns",
            "A flow will fail if any column in the database that does not have a field in the JSON document.  An error will be logged");

    static final PropertyDescriptor CONNECTION_POOL = new PropertyDescriptor.Builder()
            .name("JDBC Connection Pool")
            .description("Specifies the JDBC Connection Pool to use in order to convert the JSON message to a SQL statement. "
                    + "The Connection Pool is necessary in order to determine the appropriate database column types.")
            .identifiesControllerService(DBCPService.class)
            .required(true)
            .build();
    static final PropertyDescriptor STATEMENT_TYPE = new PropertyDescriptor.Builder()
            .name("Statement Type")
            .description("Specifies the type of SQL Statement to generate")
            .required(true)
            .allowableValues(UPDATE_TYPE, INSERT_TYPE)
            .build();
    static final PropertyDescriptor TABLE_NAME = new PropertyDescriptor.Builder()
            .name("Table Name")
            .description("The name of the table that the statement should update")
            .required(true)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor CATALOG_NAME = new PropertyDescriptor.Builder()
            .name("Catalog Name")
            .description("The name of the catalog that the statement should update. This may not apply for the database that you are updating. In this case, leave the field empty")
            .required(false)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor SCHEMA_NAME = new PropertyDescriptor.Builder()
            .name("Schema Name")
            .description("The name of the schema that the table belongs to. This may not apply for the database that you are updating. In this case, leave the field empty")
            .required(false)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor TRANSLATE_FIELD_NAMES = new PropertyDescriptor.Builder()
            .name("Translate Field Names")
            .description("If true, the Processor will attempt to translate JSON field names into the appropriate column names for the table specified. "
                    + "If false, the JSON field names must match the column names exactly, or the column will not be updated")
            .allowableValues("true", "false")
            .defaultValue("true")
            .build();
    static final PropertyDescriptor UNMATCHED_FIELD_BEHAVIOR = new PropertyDescriptor.Builder()
            .name("Unmatched Field Behavior")
            .description("If an incoming JSON element has a field that does not map to any of the database table's columns, this property specifies how to handle the situation")
            .allowableValues(IGNORE_UNMATCHED_FIELD, FAIL_UNMATCHED_FIELD)
            .defaultValue(IGNORE_UNMATCHED_FIELD.getValue())
            .build();
    static final PropertyDescriptor UNMATCHED_COLUMN_BEHAVIOR = new PropertyDescriptor.Builder()
            .name("Unmatched Column Behavior")
            .description("If an incoming JSON element does not have a field mapping for all of the database table's columns, this property specifies how to handle the situation")
            .allowableValues(IGNORE_UNMATCHED_COLUMN, WARNING_UNMATCHED_COLUMN ,FAIL_UNMATCHED_COLUMN)
            .defaultValue(FAIL_UNMATCHED_COLUMN.getValue())
            .build();
    static final PropertyDescriptor UPDATE_KEY = new PropertyDescriptor.Builder()
            .name("Update Keys")
            .description("A comma-separated list of column names that uniquely identifies a row in the database for UPDATE statements. "
                    + "If the Statement Type is UPDATE and this property is not set, the table's Primary Keys are used. "
                    + "In this case, if no Primary Key exists, the conversion to SQL will fail if Unmatched Column Behaviour is set to FAIL. "
                    + "This property is ignored if the Statement Type is INSERT")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .required(false)
            .expressionLanguageSupported(true)
            .build();


    static final Relationship REL_ORIGINAL = new Relationship.Builder()
            .name("original")
            .description("When a FlowFile is converted to SQL, the original JSON FlowFile is routed to this relationship")
            .build();
    static final Relationship REL_SQL = new Relationship.Builder()
            .name("sql")
            .description("A FlowFile is routed to this relationship when its contents have successfully been converted into a SQL statement")
            .build();
    static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("A FlowFile is routed to this relationship if it cannot be converted into a SQL statement. Common causes include invalid JSON "
                    + "content or the JSON content missing a required field (if using an INSERT statement type).")
            .build();

    private final Map<SchemaKey, TableSchema> schemaCache = new LinkedHashMap<SchemaKey, TableSchema>(100) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<SchemaKey,TableSchema> eldest) {
            return size() >= 100;
        }
    };

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(CONNECTION_POOL);
        properties.add(STATEMENT_TYPE);
        properties.add(TABLE_NAME);
        properties.add(CATALOG_NAME);
        properties.add(SCHEMA_NAME);
        properties.add(TRANSLATE_FIELD_NAMES);
        properties.add(UNMATCHED_FIELD_BEHAVIOR);
        properties.add(UNMATCHED_COLUMN_BEHAVIOR);
        properties.add(UPDATE_KEY);
        return properties;
    }


    @Override
    public Set<Relationship> getRelationships() {
        final Set<Relationship> rels = new HashSet<>();
        rels.add(REL_ORIGINAL);
        rels.add(REL_SQL);
        rels.add(REL_FAILURE);
        return rels;
    }


    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        synchronized (this) {
            schemaCache.clear();
        }
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final boolean translateFieldNames = context.getProperty(TRANSLATE_FIELD_NAMES).asBoolean();
        final boolean ignoreUnmappedFields = IGNORE_UNMATCHED_FIELD.getValue().equalsIgnoreCase(context.getProperty(UNMATCHED_FIELD_BEHAVIOR).getValue());
        final String statementType = context.getProperty(STATEMENT_TYPE).getValue();
        final String updateKeys = context.getProperty(UPDATE_KEY).evaluateAttributeExpressions(flowFile).getValue();

        final String catalog = context.getProperty(CATALOG_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String schemaName = context.getProperty(SCHEMA_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final String tableName = context.getProperty(TABLE_NAME).evaluateAttributeExpressions(flowFile).getValue();
        final SchemaKey schemaKey = new SchemaKey(catalog, tableName);
        final boolean includePrimaryKeys = UPDATE_TYPE.equals(statementType) && updateKeys == null;

        // Is the unmatched column behaviour fail or warning?
        final boolean failUnmappedColumns = FAIL_UNMATCHED_COLUMN.getValue().equalsIgnoreCase(context.getProperty(UNMATCHED_COLUMN_BEHAVIOR).getValue());
        final boolean warningUnmappedColumns = WARNING_UNMATCHED_COLUMN.getValue().equalsIgnoreCase(context.getProperty(UNMATCHED_COLUMN_BEHAVIOR).getValue());

        // get the database schema from the cache, if one exists. We do this in a synchronized block, rather than
        // using a ConcurrentMap because the Map that we are using is a LinkedHashMap with a capacity such that if
        // the Map grows beyond this capacity, old elements are evicted. We do this in order to avoid filling the
        // Java Heap if there are a lot of different SQL statements being generated that reference different tables.
        TableSchema schema;
        synchronized (this) {
            schema = schemaCache.get(schemaKey);
            if (schema == null) {
                // No schema exists for this table yet. Query the database to determine the schema and put it into the cache.
                final DBCPService dbcpService = context.getProperty(CONNECTION_POOL).asControllerService(DBCPService.class);
                try (final Connection conn = dbcpService.getConnection()) {
                    schema = TableSchema.from(conn, catalog, schemaName, tableName, translateFieldNames, includePrimaryKeys);
                    schemaCache.put(schemaKey, schema);
                } catch (final SQLException e) {
                    getLogger().error("Failed to convert {} into a SQL statement due to {}; routing to failure", new Object[] {flowFile, e.toString()}, e);
                    session.transfer(flowFile, REL_FAILURE);
                    return;
                }
            }
        }

        // Parse the JSON document
        final ObjectMapper mapper = new ObjectMapper();
        final AtomicReference<JsonNode> rootNodeRef = new AtomicReference<>(null);
        try {
            session.read(flowFile, new InputStreamCallback() {
                @Override
                public void process(final InputStream in) throws IOException {
                    try (final InputStream bufferedIn = new BufferedInputStream(in)) {
                        rootNodeRef.set(mapper.readTree(bufferedIn));
                    }
                }
            });
        } catch (final ProcessException pe) {
            getLogger().error("Failed to parse {} as JSON due to {}; routing to failure", new Object[] {flowFile, pe.toString()}, pe);
            session.transfer(flowFile, REL_FAILURE);
            return;
        }

        final JsonNode rootNode = rootNodeRef.get();

        // The node may or may not be a Json Array. If it isn't, we will create an
        // ArrayNode and add just the root node to it. We do this so that we can easily iterate
        // over the array node, rather than duplicating the logic or creating another function that takes many variables
        // in order to implement the logic.
        final ArrayNode arrayNode;
        if (rootNode.isArray()) {
            arrayNode = (ArrayNode) rootNode;
        } else {
            final JsonNodeFactory nodeFactory = JsonNodeFactory.instance;
            arrayNode = new ArrayNode(nodeFactory);
            arrayNode.add(rootNode);
        }

        final String fragmentIdentifier = UUID.randomUUID().toString();

        final Set<FlowFile> created = new HashSet<>();
        for (int i=0; i < arrayNode.size(); i++) {
            final JsonNode jsonNode = arrayNode.get(i);

            final String sql;
            final Map<String, String> attributes = new HashMap<>();

            try {
                // build the fully qualified table name
                final StringBuilder tableNameBuilder = new StringBuilder();
                if (catalog != null) {
                    tableNameBuilder.append(catalog).append(".");
                }
                if (schemaName != null) {
                    tableNameBuilder.append(schemaName).append(".");
                }
                tableNameBuilder.append(tableName);
                final String fqTableName = tableNameBuilder.toString();

                if (INSERT_TYPE.equals(statementType)) {
                    sql = generateInsert(jsonNode, attributes, fqTableName, schema, translateFieldNames, ignoreUnmappedFields, failUnmappedColumns, warningUnmappedColumns);
                } else {
                    sql = generateUpdate(jsonNode, attributes, fqTableName, updateKeys, schema, translateFieldNames, ignoreUnmappedFields, failUnmappedColumns, warningUnmappedColumns);
                }
            } catch (final ProcessException pe) {
                getLogger().error("Failed to convert {} to a SQL {} statement due to {}; routing to failure",
                        new Object[] { flowFile, statementType, pe.toString() }, pe);
                session.remove(created);
                session.transfer(flowFile, REL_FAILURE);
                return;
            }

            FlowFile sqlFlowFile = session.create(flowFile);
            created.add(sqlFlowFile);

            sqlFlowFile = session.write(sqlFlowFile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream out) throws IOException {
                    out.write(sql.getBytes(StandardCharsets.UTF_8));
                }
            });

            attributes.put(CoreAttributes.MIME_TYPE.key(), "text/plain");
            attributes.put("sql.table", tableName);
            attributes.put("fragment.identifier", fragmentIdentifier);
            attributes.put("fragment.count", String.valueOf(arrayNode.size()));
            attributes.put("fragment.index", String.valueOf(i));

            if (catalog != null) {
                attributes.put("sql.catalog", catalog);
            }

            sqlFlowFile = session.putAllAttributes(sqlFlowFile, attributes);
            session.transfer(sqlFlowFile, REL_SQL);
        }

        session.transfer(flowFile, REL_ORIGINAL);
    }

    private Set<String> getNormalizedColumnNames(final JsonNode node, final boolean translateFieldNames) {
        final Set<String> normalizedFieldNames = new HashSet<>();
        final Iterator<String> fieldNameItr = node.getFieldNames();
        while (fieldNameItr.hasNext()) {
            normalizedFieldNames.add(normalizeColumnName(fieldNameItr.next(), translateFieldNames));
        }

        return normalizedFieldNames;
    }

    private String generateInsert(final JsonNode rootNode, final Map<String, String> attributes, final String tableName,
        final TableSchema schema, final boolean translateFieldNames, final boolean ignoreUnmappedFields, final boolean failUnmappedColumns,
        final boolean warningUnmappedColumns) {

        final Set<String> normalizedFieldNames = getNormalizedColumnNames(rootNode, translateFieldNames);
        for (final String requiredColName : schema.getRequiredColumnNames()) {
            final String normalizedColName = normalizeColumnName(requiredColName, translateFieldNames);
            if (!normalizedFieldNames.contains(normalizedColName)) {
                String missingColMessage = "JSON does not have a value for the Required column '" + requiredColName + "'";
                if (failUnmappedColumns) {
                    getLogger().error(missingColMessage);
                    throw new ProcessException(missingColMessage);
                } else if (warningUnmappedColumns) {
                    getLogger().warn(missingColMessage);
                }
            }
        }

        final StringBuilder sqlBuilder = new StringBuilder();
        int fieldCount = 0;
        sqlBuilder.append("INSERT INTO ").append(tableName).append(" (");

        // iterate over all of the elements in the JSON, building the SQL statement by adding the column names, as well as
        // adding the column value to a "sql.args.N.value" attribute and the type of a "sql.args.N.type" attribute add the
        // columns that we are inserting into
        final Iterator<String> fieldNames = rootNode.getFieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();

            final ColumnDescription desc = schema.getColumns().get(normalizeColumnName(fieldName, translateFieldNames));
            if (desc == null && !ignoreUnmappedFields) {
                throw new ProcessException("Cannot map JSON field '" + fieldName + "' to any column in the database");
            }

            if (desc != null) {
                if (fieldCount++ > 0) {
                    sqlBuilder.append(", ");
                }

                sqlBuilder.append(desc.getColumnName());

                final int sqlType = desc.getDataType();
                attributes.put("sql.args." + fieldCount + ".type", String.valueOf(sqlType));

                final Integer colSize = desc.getColumnSize();
                final JsonNode fieldNode = rootNode.get(fieldName);
                if (!fieldNode.isNull()) {
                    String fieldValue = fieldNode.asText();
                    if (colSize != null && fieldValue.length() > colSize) {
                        fieldValue = fieldValue.substring(0, colSize);
                    }
                    attributes.put("sql.args." + fieldCount + ".value", fieldValue);
                }
            }
        }

        // complete the SQL statements by adding ?'s for all of the values to be escaped.
        sqlBuilder.append(") VALUES (");
        for (int i=0; i < fieldCount; i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }

            sqlBuilder.append("?");
        }
        sqlBuilder.append(")");

        if (fieldCount == 0) {
            throw new ProcessException("None of the fields in the JSON map to the columns defined by the " + tableName + " table");
        }

        return sqlBuilder.toString();
    }

    private String generateUpdate(final JsonNode rootNode, final Map<String, String> attributes, final String tableName, final String updateKeys,
        final TableSchema schema, final boolean translateFieldNames, final boolean ignoreUnmappedFields, final boolean failUnmappedColumns,
        final boolean warningUnmappedColumns) {

        final Set<String> updateKeyNames;
        if (updateKeys == null) {
            updateKeyNames = schema.getPrimaryKeyColumnNames();
        } else {
            updateKeyNames = new HashSet<>();
            for (final String updateKey : updateKeys.split(",")) {
                updateKeyNames.add(updateKey.trim());
            }
        }

        if (updateKeyNames.isEmpty()) {
            throw new ProcessException("Table '" + tableName + "' does not have a Primary Key and no Update Keys were specified");
        }

        final StringBuilder sqlBuilder = new StringBuilder();
        int fieldCount = 0;
        sqlBuilder.append("UPDATE ").append(tableName).append(" SET ");


        // Create a Set of all normalized Update Key names, and ensure that there is a field in the JSON
        // for each of the Update Key fields.
        final Set<String> normalizedFieldNames = getNormalizedColumnNames(rootNode, translateFieldNames);
        final Set<String> normalizedUpdateNames = new HashSet<>();
        for (final String uk : updateKeyNames) {
            final String normalizedUK = normalizeColumnName(uk, translateFieldNames);
            normalizedUpdateNames.add(normalizedUK);

            if (!normalizedFieldNames.contains(normalizedUK)) {
                String missingColMessage = "JSON does not have a value for the " + (updateKeys == null ? "Primary" : "Update") + "Key column '" + uk + "'";
                if (failUnmappedColumns) {
                    getLogger().error(missingColMessage);
                    throw new ProcessException(missingColMessage);
                } else if (warningUnmappedColumns) {
                    getLogger().warn(missingColMessage);
                }
            }
        }

        // iterate over all of the elements in the JSON, building the SQL statement by adding the column names, as well as
        // adding the column value to a "sql.args.N.value" attribute and the type of a "sql.args.N.type" attribute add the
        // columns that we are inserting into
        Iterator<String> fieldNames = rootNode.getFieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();

            final String normalizedColName = normalizeColumnName(fieldName, translateFieldNames);
            final ColumnDescription desc = schema.getColumns().get(normalizedColName);
            if (desc == null) {
                if (!ignoreUnmappedFields) {
                    throw new ProcessException("Cannot map JSON field '" + fieldName + "' to any column in the database");
                } else {
                    continue;
                }
            }

            // Check if this column is an Update Key. If so, skip it for now. We will come
            // back to it after we finish the SET clause
            if (normalizedUpdateNames.contains(normalizedColName)) {
                continue;
            }

            if (fieldCount++ > 0) {
                sqlBuilder.append(", ");
            }

            sqlBuilder.append(desc.getColumnName()).append(" = ?");
            final int sqlType = desc.getDataType();
            attributes.put("sql.args." + fieldCount + ".type", String.valueOf(sqlType));

            final Integer colSize = desc.getColumnSize();

            final JsonNode fieldNode = rootNode.get(fieldName);
            if (!fieldNode.isNull()) {
                String fieldValue = rootNode.get(fieldName).asText();
                if (colSize != null && fieldValue.length() > colSize) {
                    fieldValue = fieldValue.substring(0, colSize);
                }
                attributes.put("sql.args." + fieldCount + ".value", fieldValue);
            }
        }

        // Set the WHERE clause based on the Update Key values
        sqlBuilder.append(" WHERE ");

        fieldNames = rootNode.getFieldNames();
        int whereFieldCount = 0;
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();

            final String normalizedColName = normalizeColumnName(fieldName, translateFieldNames);
            final ColumnDescription desc = schema.getColumns().get(normalizedColName);
            if (desc == null) {
                continue;
            }

            // Check if this column is a Update Key. If so, skip it for now. We will come
            // back to it after we finish the SET clause
            if (!normalizedUpdateNames.contains(normalizedColName)) {
                continue;
            }

            if (whereFieldCount++ > 0) {
                sqlBuilder.append(" AND ");
            }
            fieldCount++;

            sqlBuilder.append(normalizedColName).append(" = ?");
            final int sqlType = desc.getDataType();
            attributes.put("sql.args." + fieldCount + ".type", String.valueOf(sqlType));

            final Integer colSize = desc.getColumnSize();
            String fieldValue = rootNode.get(fieldName).asText();
            if (colSize != null && fieldValue.length() > colSize) {
                fieldValue = fieldValue.substring(0, colSize);
            }
            attributes.put("sql.args." + fieldCount + ".value", fieldValue);
        }

        return sqlBuilder.toString();
    }

    private static String normalizeColumnName(final String colName, final boolean translateColumnNames) {
        return translateColumnNames ? colName.toUpperCase().replace("_", "") : colName;
    }

    private static class TableSchema {
        private List<String> requiredColumnNames;
        private Set<String> primaryKeyColumnNames;
        private Map<String, ColumnDescription> columns;

        private TableSchema(final List<ColumnDescription> columnDescriptions, final boolean translateColumnNames,
                            final Set<String> primaryKeyColumnNames) {
            this.columns = new HashMap<>();
            this.primaryKeyColumnNames = primaryKeyColumnNames;

            this.requiredColumnNames = new ArrayList<>();
            for (final ColumnDescription desc : columnDescriptions) {
                columns.put(ConvertJSONToSQL.normalizeColumnName(desc.columnName, translateColumnNames), desc);
                if (desc.isRequired()) {
                    requiredColumnNames.add(desc.columnName);
                }
            }
        }

        public Map<String, ColumnDescription> getColumns() {
            return columns;
        }

        public List<String> getRequiredColumnNames() {
            return requiredColumnNames;
        }

        public Set<String> getPrimaryKeyColumnNames() {
            return primaryKeyColumnNames;
        }

        public static TableSchema from(final Connection conn, final String catalog, final String schema, final String tableName,
                                       final boolean translateColumnNames, final boolean includePrimaryKeys) throws SQLException {
            try (final ResultSet colrs = conn.getMetaData().getColumns(catalog, schema, tableName, "%")) {

                final List<ColumnDescription> cols = new ArrayList<>();
                while (colrs.next()) {
                    final ColumnDescription col = ColumnDescription.from(colrs);
                    cols.add(col);
                }

                final Set<String> primaryKeyColumns = new HashSet<>();
                if (includePrimaryKeys) {
                    try (final ResultSet pkrs = conn.getMetaData().getPrimaryKeys(catalog, null, tableName)) {

                        while (pkrs.next()) {
                            final String colName = pkrs.getString("COLUMN_NAME");
                            primaryKeyColumns.add(normalizeColumnName(colName, translateColumnNames));
                        }
                    }
                }

                return new TableSchema(cols, translateColumnNames, primaryKeyColumns);
            }
        }
    }

    private static class ColumnDescription {
        private final String columnName;
        private final int dataType;
        private final boolean required;
        private final Integer columnSize;

        private ColumnDescription(final String columnName, final int dataType, final boolean required, final Integer columnSize) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.required = required;
            this.columnSize = columnSize;
        }

        public int getDataType() {
            return dataType;
        }

        public Integer getColumnSize() {
            return columnSize;
        }

        public String getColumnName() {
            return columnName;
        }

        public boolean isRequired() {
            return required;
        }

        public static ColumnDescription from(final ResultSet resultSet) throws SQLException {
            final ResultSetMetaData md = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();

            for (int i = 1; i < md.getColumnCount() + 1; i++) {
                columns.add(md.getColumnName(i));
            }

            final String columnName = resultSet.getString("COLUMN_NAME");
            final int dataType = resultSet.getInt("DATA_TYPE");
            final int colSize = resultSet.getInt("COLUMN_SIZE");

            final String nullableValue = resultSet.getString("IS_NULLABLE");
            final boolean isNullable = "YES".equalsIgnoreCase(nullableValue) || nullableValue.isEmpty();
            final String defaultValue = resultSet.getString("COLUMN_DEF");
            String autoIncrementValue = "NO";

            if(columns.contains("IS_AUTOINCREMENT")){
                autoIncrementValue = resultSet.getString("IS_AUTOINCREMENT");
            }

            final boolean isAutoIncrement = "YES".equalsIgnoreCase(autoIncrementValue);
            final boolean required = !isNullable && !isAutoIncrement && defaultValue == null;

            return new ColumnDescription(columnName, dataType, required, colSize == 0 ? null : colSize);
        }
    }

    private static class SchemaKey {
        private final String catalog;
        private final String tableName;

        public SchemaKey(final String catalog, final String tableName) {
            this.catalog = catalog;
            this.tableName = tableName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((catalog == null) ? 0 : catalog.hashCode());
            result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final SchemaKey other = (SchemaKey) obj;
            if (catalog == null) {
                if (other.catalog != null) {
                    return false;
                }
            } else if (!catalog.equals(other.catalog)) {
                return false;
            }


            if (tableName == null) {
                if (other.tableName != null) {
                    return false;
                }
            } else if (!tableName.equals(other.tableName)) {
                return false;
            }

            return true;
        }
    }
}
