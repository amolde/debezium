/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.postgresql.transforms;

import static io.debezium.connector.postgresql.TestHelper.topicName;
import static io.debezium.junit.EqualityCheck.LESS_THAN;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.config.Configuration;
import io.debezium.connector.postgresql.PostgresConnector;
import io.debezium.connector.postgresql.SourceInfo;
import io.debezium.connector.postgresql.TestHelper;
import io.debezium.connector.postgresql.junit.SkipTestDependingOnDecoderPluginNameRule;
import io.debezium.connector.postgresql.junit.SkipWhenDecoderPluginNameIsNot;
import io.debezium.data.Envelope;
import io.debezium.doc.FixFor;
import io.debezium.embedded.AbstractConnectorTest;
import io.debezium.junit.SkipWhenDatabaseVersion;

/**
 * Tests for {@link io.debezium.connector.postgresql.transforms.DecodeLogicalDecodingMessageContent} SMT.
 *
 * @author Roman Kudryashov
 */
public class DecodeLogicalDecodingMessageContentTest extends AbstractConnectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecodeLogicalDecodingMessageContentTest.class);

    @Rule
    public final TestRule skipName = new SkipTestDependingOnDecoderPluginNameRule();

    @BeforeClass
    public static void beforeClass() throws SQLException {
        TestHelper.dropAllSchemas();
    }

    @Before
    public void before() {
        initializeConnectorTestFramework();
    }

    @After
    public void after() {
        stopConnector();
        TestHelper.dropDefaultReplicationSlot();
        TestHelper.dropPublication();
    }

    @Test
    @FixFor("DBZ-8103")
    @SkipWhenDecoderPluginNameIsNot(value = SkipWhenDecoderPluginNameIsNot.DecoderPluginName.PGOUTPUT, reason = "Only supported on PgOutput")
    @SkipWhenDatabaseVersion(check = LESS_THAN, major = 14, minor = 0, reason = "Message not supported for PG version < 14")
    public void shouldFailWhenLogicalDecodingMessageContentIsEmptyString() throws Exception {
        DecodeLogicalDecodingMessageContent<SourceRecord> decodeLogicalDecodingMessageContent = new DecodeLogicalDecodingMessageContent<>();
        Map<String, String> smtConfig = new LinkedHashMap<>();
        decodeLogicalDecodingMessageContent.configure(smtConfig);

        Configuration.Builder configBuilder = TestHelper.defaultConfig();
        start(PostgresConnector.class, configBuilder.build());
        assertConnectorIsRunning();
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // emit non transactional logical decoding message
        TestHelper.execute("SELECT pg_logical_emit_message(false, 'foo', '');");

        SourceRecords records = consumeRecordsByTopic(1);
        List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("message"));
        assertThat(recordsForTopic).hasSize(1);

        Exception exception = assertThrows(DataException.class, () -> decodeLogicalDecodingMessageContent.apply(recordsForTopic.get(0)));

        assertThat(exception.getMessage()).isEqualTo("Conversion of logical decoding message content failed");

        decodeLogicalDecodingMessageContent.close();
        stopConnector();
    }

    @Test
    @FixFor("DBZ-8103")
    @SkipWhenDecoderPluginNameIsNot(value = SkipWhenDecoderPluginNameIsNot.DecoderPluginName.PGOUTPUT, reason = "Only supported on PgOutput")
    @SkipWhenDatabaseVersion(check = LESS_THAN, major = 14, minor = 0, reason = "Message not supported for PG version < 14")
    public void shouldConvertRecordWithNonTransactionalLogicalDecodingMessageWithEmptyContent() throws Exception {
        DecodeLogicalDecodingMessageContent<SourceRecord> decodeLogicalDecodingMessageContent = new DecodeLogicalDecodingMessageContent<>();
        Map<String, String> smtConfig = new LinkedHashMap<>();
        decodeLogicalDecodingMessageContent.configure(smtConfig);

        Configuration.Builder configBuilder = TestHelper.defaultConfig();
        start(PostgresConnector.class, configBuilder.build());
        assertConnectorIsRunning();
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // emit non transactional logical decoding message
        TestHelper.execute("SELECT pg_logical_emit_message(false, 'foo', '{}');");

        SourceRecords records = consumeRecordsByTopic(1);
        List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("message"));
        assertThat(recordsForTopic).hasSize(1);

        SourceRecord transformedRecord = decodeLogicalDecodingMessageContent.apply(recordsForTopic.get(0));
        assertThat(transformedRecord).isNotNull();

        Struct value = (Struct) transformedRecord.value();
        String op = value.getString(Envelope.FieldName.OPERATION);
        Struct source = value.getStruct(Envelope.FieldName.SOURCE);
        Struct after = value.getStruct(Envelope.FieldName.AFTER);

        assertNull(source.getInt64(SourceInfo.TXID_KEY));
        assertNotNull(source.getInt64(SourceInfo.TIMESTAMP_KEY));
        assertNotNull(source.getInt64(SourceInfo.LSN_KEY));
        assertEquals("", source.getString(SourceInfo.TABLE_NAME_KEY));
        assertEquals("", source.getString(SourceInfo.SCHEMA_NAME_KEY));

        assertEquals(Envelope.Operation.CREATE.code(), op);
        assertEquals(0, after.schema().fields().size());
        List<Field> recordValueSchemaFields = value.schema().fields();
        assertTrue(recordValueSchemaFields.stream().noneMatch(f -> f.name().equals("message")));

        decodeLogicalDecodingMessageContent.close();
        stopConnector();
    }

    @Test
    @FixFor("DBZ-8103")
    @SkipWhenDecoderPluginNameIsNot(value = SkipWhenDecoderPluginNameIsNot.DecoderPluginName.PGOUTPUT, reason = "Only supported on PgOutput")
    @SkipWhenDatabaseVersion(check = LESS_THAN, major = 14, minor = 0, reason = "Message not supported for PG version < 14")
    public void shouldConvertRecordWithTransactionalLogicalDecodingMessageWithContent() throws Exception {
        DecodeLogicalDecodingMessageContent<SourceRecord> decodeLogicalDecodingMessageContent = new DecodeLogicalDecodingMessageContent<>();
        Map<String, String> smtConfig = new LinkedHashMap<>();
        decodeLogicalDecodingMessageContent.configure(smtConfig);

        Configuration.Builder configBuilder = TestHelper.defaultConfig();
        start(PostgresConnector.class, configBuilder.build());
        assertConnectorIsRunning();
        waitForStreamingRunning("postgres", TestHelper.TEST_SERVER);

        // emit transactional logical decoding message
        TestHelper.execute("SELECT pg_logical_emit_message(true, 'foo', '{\"bar\": \"baz\", \"qux\": 9703}');");

        SourceRecords records = consumeRecordsByTopic(1);
        List<SourceRecord> recordsForTopic = records.recordsForTopic(topicName("message"));
        assertThat(recordsForTopic).hasSize(1);

        SourceRecord transformedRecord = decodeLogicalDecodingMessageContent.apply(recordsForTopic.get(0));
        assertThat(transformedRecord).isNotNull();

        Struct value = (Struct) transformedRecord.value();
        String op = value.getString(Envelope.FieldName.OPERATION);
        Struct source = value.getStruct(Envelope.FieldName.SOURCE);
        Struct after = value.getStruct(Envelope.FieldName.AFTER);

        assertNotNull(source.getInt64(SourceInfo.TXID_KEY));
        assertNotNull(source.getInt64(SourceInfo.TIMESTAMP_KEY));
        assertNotNull(source.getInt64(SourceInfo.LSN_KEY));
        assertEquals("", source.getString(SourceInfo.TABLE_NAME_KEY));
        assertEquals("", source.getString(SourceInfo.SCHEMA_NAME_KEY));

        assertEquals(Envelope.Operation.CREATE.code(), op);
        assertEquals(2, after.schema().fields().size());
        assertEquals("baz", after.get("bar"));
        assertEquals(9703, after.get("qux"));
        List<Field> recordValueSchemaFields = value.schema().fields();
        assertTrue(recordValueSchemaFields.stream().noneMatch(f -> f.name().equals("message")));

        decodeLogicalDecodingMessageContent.close();
        stopConnector();
    }
}
