/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsar.functions.transforms;

import static com.datastax.oss.pulsar.functions.transforms.Utils.assertNonOptionalField;
import static com.datastax.oss.pulsar.functions.transforms.Utils.assertOptionalField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.AssertJUnit.assertNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.GenericSchema;
import org.apache.pulsar.client.api.schema.KeyValueSchema;
import org.apache.pulsar.client.api.schema.RecordSchemaBuilder;
import org.apache.pulsar.client.api.schema.SchemaBuilder;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Record;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TransformFunctionTest {

  @DataProvider(name = "validConfigs")
  public static Object[][] validConfigs() {
    return new Object[][] {
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field']}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': 'key'}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': 'value'}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'when': 'key.k1==key1'}]}"},
      {
        "{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': null, 'when': null}]}"
      },
      {"{'steps': [{'type': 'merge-key-value'}]}"},
      {"{'steps': [{'type': 'unwrap-key-value'}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'unwrap-key': false}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'unwrap-key': true}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'unwrap-key': true, 'when': 'value.v1==val1'}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'unwrap-key': null, 'when': null}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': 'key'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': 'value'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'when': 'value.v1==val1'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': null, 'when': null}]}"},
      {"{'steps': [{'type': 'flatten'}]}"},
      {"{'steps': [{'type': 'flatten', 'part': 'key'}]}"},
      {"{'steps': [{'type': 'flatten', 'part': 'value'}]}"},
      {"{'steps': [{'type': 'flatten', 'delimiter': '_'}]}"},
      {"{'steps': [{'type': 'flatten', 'when': 'prop1==val1'}]}"},
      {"{'steps': [{'type': 'flatten', 'delimiter': null, 'part': null, 'when': null}]}"},
      {"{'steps': [{'type': 'drop', 'when': null}]}"},
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'value.some-field', expression: 'true', type: 'BOOLEAN'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'key.some-field', expression: 'string', type: 'STRING'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'value.some-field', expression: 'int32', type: 'INT32'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'key.some-field', expression: 'int64', type: 'INT64'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'value.some-field', expression: 'float', type: 'FLOAT'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'key.some-field', expression: 'double', optional: true, type: 'DOUBLE'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'destinationTopic', expression: 'string', optional: true, type: 'STRING'}]}]}"
      },
      {
          "{'steps': [{'type': 'compute', 'fields': [{'name': 'destinationTopic', expression: 'date', optional: true, type: 'DATE'}]}]}"
      },
      {
          "{'steps': [{'type': 'compute', 'fields': [{'name': 'destinationTopic', expression: 'time', optional: true, type: 'TIME'}]}]}"
      },
      {
          "{'steps': [{'type': 'compute', 'fields': [{'name': 'destinationTopic', expression: 'datetime', optional: true, type: 'DATETIME'}]}]}"
      },
    };
  }

  @Test(dataProvider = "validConfigs")
  void testValidConfig(String validConfig) {
    String userConfig = validConfig.replace("'", "\"");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    Context context = new Utils.TestContext(null, config);
    TransformFunction transformFunction = new TransformFunction();
    transformFunction.initialize(context);
  }

  @DataProvider(name = "invalidConfigs")
  public static Object[][] invalidConfigs() {
    return new Object[][] {
      {"{}"},
      {"{'steps': 'invalid'}"},
      {"{'steps': [{}]}"},
      {"{'steps': [{'type': 'invalid'}]}"},
      {"{'steps': [{'type': 'drop-fields'}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['']}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': 'invalid'}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field', 42]}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': 42}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'part': 42}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field'], 'when': ''}]}"},
      {"{'steps': [{'type': 'drop-fields', 'fields': ['some-field']}, {'type': 'cast'}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'unwrap-key': 'invalid'}]}"},
      {"{'steps': [{'type': 'unwrap-key-value', 'when': ''}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 42}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'INVALID'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': 'invalid'}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': 42}]}"},
      {"{'steps': [{'type': 'cast', 'schema-type': 'STRING', 'part': 42}], 'when': ''}"},
      {"{'steps': [{'type': 'flatten', 'part': 'invalid'}]}"},
      {"{'steps': [{'type': 'flatten', 'when': ''}]}"},
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'true', type: 'BOOLEAN'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'true', type: 'BOOLEAN'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'true', type: 'BOOLEAN'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'record', type: 'AVRO'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'json', type: 'JSON', part: 'key'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'int32', type: 'INT32', part: 'non-key-or-value'}]}]}"
      },
      {"{'steps': [{'type': 'compute', 'fields': [{expression: 'int64', type: 'INT64'}]}]}"},
      {"{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', type: 'FLOAT'}]}]}"},
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'some-field', expression: 'double'}]}]}"
      },
      {"{'steps': [{'type': 'compute', 'fields': null}]}"},
      {"{'steps': [{'type': 'compute', 'fields': []}]}"},
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': '', expression: 'double', type: 'DOUBLE'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'value.some-field', expression: '', type: 'DOUBLE'}]}]}"
      },
      {
        "{'steps': [{'type': 'compute', 'fields': [{'name': 'value.some-field', expression: 'double', optional: 'true', type: 'DOUBLE'}]}]}"
      },
    };
  }

  @Test(dataProvider = "invalidConfigs")
  void testInvalidConfig(String invalidConfig) {
    String userConfig = invalidConfig.replace("'", "\"");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    Context context = new Utils.TestContext(null, config);
    TransformFunction transformFunction = new TransformFunction();
    assertThrows(IllegalArgumentException.class, () -> transformFunction.initialize(context));
  }

  @Test
  void testDropFields() throws Exception {
    String userConfig =
        (""
                + "{'steps': ["
                + "    {'type': 'drop-fields', 'fields': ['keyField1']},"
                + "    {'type': 'drop-fields', 'fields': ['keyField2'], 'part': 'key'},"
                + "    {'type': 'drop-fields', 'fields': ['keyField3'], 'part': 'value'},"
                + "    {'type': 'drop-fields', 'fields': ['valueField1']},"
                + "    {'type': 'drop-fields', 'fields': ['valueField2'], 'part': 'key'},"
                + "    {'type': 'drop-fields', 'fields': ['valueField3'], 'part': 'value'}"
                + "]}")
            .replace("'", "\"");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);
    Record<?> outputRecord = transformFunction.process(record.getValue(), context);

    KeyValueSchema<?, ?> messageSchema = (KeyValueSchema<?, ?>) outputRecord.getSchema();
    KeyValue<?, ?> messageValue = (KeyValue<?, ?>) outputRecord.getValue();

    GenericData.Record keyAvroRecord =
        Utils.getRecord(messageSchema.getKeySchema(), (byte[]) messageValue.getKey());
    assertEquals(keyAvroRecord.get("keyField3"), new Utf8("key3"));
    assertNull(keyAvroRecord.getSchema().getField("keyField1"));
    assertNull(keyAvroRecord.getSchema().getField("keyField2"));

    GenericData.Record valueAvroRecord =
        Utils.getRecord(messageSchema.getValueSchema(), (byte[]) messageValue.getValue());
    assertEquals(valueAvroRecord.get("valueField2"), new Utf8("value2"));
    assertNull(valueAvroRecord.getSchema().getField("valueField1"));
    assertNull(valueAvroRecord.getSchema().getField("valueField3"));
  }

  @Test
  void testComputeFields() throws Exception {
    String userConfig =
        (""
                + "{'steps': ["
                + "    {'type': 'compute', 'fields':["
                + "        {'name': 'key.newField1', 'expression' : '5*3', 'type': 'INT32'},"
                + "        {'name': 'key.newField2', 'expression' : 'value.valueField1', 'type': 'STRING', 'optional' : false},"
                + "        {'name': 'value.newField1', 'expression' : '5+3', 'type': 'INT32'},"
                + "        {'name': 'value.newField2', 'expression' : 'value.valueField1', 'type': 'STRING', 'optional' : false}]}"
                + "]}")
            .replace("'", "\"");

    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);
    Record<?> outputRecord = transformFunction.process(record.getValue(), context);

    KeyValueSchema<?, ?> messageSchema = (KeyValueSchema<?, ?>) outputRecord.getSchema();
    KeyValue<?, ?> messageValue = (KeyValue<?, ?>) outputRecord.getValue();

    GenericData.Record keyAvroRecord =
        Utils.getRecord(messageSchema.getKeySchema(), (byte[]) messageValue.getKey());
    assertEquals(keyAvroRecord.get("keyField1"), new Utf8("key1"));
    assertEquals(keyAvroRecord.get("keyField2"), new Utf8("key2"));
    assertEquals(keyAvroRecord.get("keyField3"), new Utf8("key3"));
    assertOptionalField(keyAvroRecord, "newField1", org.apache.avro.Schema.Type.INT, 15);
    assertNonOptionalField(
        keyAvroRecord, "newField2", org.apache.avro.Schema.Type.STRING, new Utf8("value1"));

    GenericData.Record valueAvroRecord =
        Utils.getRecord(messageSchema.getValueSchema(), (byte[]) messageValue.getValue());
    assertEquals(valueAvroRecord.get("valueField1"), new Utf8("value1"));
    assertEquals(valueAvroRecord.get("valueField2"), new Utf8("value2"));
    assertEquals(valueAvroRecord.get("valueField3"), new Utf8("value3"));
    assertOptionalField(valueAvroRecord, "newField1", org.apache.avro.Schema.Type.INT, 8);
    assertNonOptionalField(
        valueAvroRecord, "newField2", org.apache.avro.Schema.Type.STRING, new Utf8("value1"));
  }

  @Test
  void testMatchingPredicate() throws Exception {
    String userConfig =
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField1\"], \"when\": \"key.keyField1 == 'key1'\"},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField2\"], \"when\": \"key.keyField2 == 'key2'\"}"
            + "]}");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);
    Record<GenericObject> outputRecord = transformFunction.process(record.getValue(), context);

    KeyValueSchema messageSchema = (KeyValueSchema) outputRecord.getSchema();
    KeyValue messageValue = (KeyValue) outputRecord.getValue();

    GenericData.Record keyAvroRecord =
        Utils.getRecord(messageSchema.getKeySchema(), (byte[]) messageValue.getKey());
    assertEquals(keyAvroRecord.get("keyField3"), new Utf8("key3"));
    assertNull(keyAvroRecord.getSchema().getField("keyField1"));
    assertNull(keyAvroRecord.getSchema().getField("keyField2"));
  }

  @Test
  void testNonMatchingPredicate() throws Exception {
    String userConfig =
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField1\"], \"when\": \"key.keyField1 == 'key100'\"},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField2\"], \"when\": \"key.keyField2 == 'key100'\"},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField3\"]}"
            + "]}");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);
    transformFunction.process(record.getValue(), context);

    Record<GenericObject> outputRecord = transformFunction.process(record.getValue(), context);

    KeyValueSchema messageSchema = (KeyValueSchema) outputRecord.getSchema();
    KeyValue messageValue = (KeyValue) outputRecord.getValue();

    GenericData.Record keyAvroRecord =
        Utils.getRecord(messageSchema.getKeySchema(), (byte[]) messageValue.getKey());
    assertEquals(keyAvroRecord.get("keyField1"), new Utf8("key1"));
    assertEquals(keyAvroRecord.get("keyField2"), new Utf8("key2"));
    assertNull(keyAvroRecord.getSchema().getField("keyField3"));
  }

  @Test
  void testMixedPredicate() throws Exception {
    String userConfig =
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop-fields\", \"fields\": [\"keyField1\"], \"when\": \"key.keyField1 == 'key1'\"},"
            + "    {\"type\": \"merge-key-value\", \"when\": \"key.keyField2 == 'key100'\"},"
            + "    {\"type\": \"unwrap-key-value\", \"when\": \"key.keyField3 == 'key100'\"},"
            + "    {\"type\": \"cast\", \"schema-type\": \"STRING\", \"when\": \"value.valueField1 == 'value1'\"}"
            + "]}");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);

    Record<GenericObject> outputRecord = transformFunction.process(record.getValue(), context);

    KeyValue<String, String> kv = (KeyValue<String, String>) outputRecord.getValue();
    assertEquals(kv.getKey(), "{\"keyField2\": \"key2\", \"keyField3\": \"key3\"}");
    assertEquals(
        kv.getValue(),
        "{\"valueField1\": \"value1\", \"valueField2\": \"value2\", \"valueField3\": \"value3\"}");
  }

  @Test(dataProvider = "dropStepConfigs")
  void testDropOnPredicateMatch(String stepConfig, boolean drop) throws Exception {
    RecordSchemaBuilder recordSchemaBuilder = SchemaBuilder.record("record");
    recordSchemaBuilder.field("firstName").type(SchemaType.STRING);
    recordSchemaBuilder.field("lastName").type(SchemaType.STRING);
    recordSchemaBuilder.field("age").type(SchemaType.INT32);

    SchemaInfo schemaInfo = recordSchemaBuilder.build(SchemaType.AVRO);
    GenericSchema<GenericRecord> genericSchema = Schema.generic(schemaInfo);

    GenericRecord genericRecord =
        genericSchema
            .newRecordBuilder()
            .set("firstName", "Jane")
            .set("lastName", "Doe")
            .set("age", 42)
            .build();

    Record<GenericObject> record = new Utils.TestRecord<>(genericSchema, genericRecord, "test-key");
    Map<String, Object> config =
        new Gson().fromJson(stepConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);

    Record<?> outputRecord = transformFunction.process(record.getValue(), context);

    if (drop) {
      assertNull(outputRecord);
    } else {
      GenericData.Record read =
          Utils.getRecord(outputRecord.getSchema(), (byte[]) outputRecord.getValue());
      assertEquals(read.get("age"), 42);
      Assert.assertNull(read.getSchema().getField("firstName"));
      Assert.assertNull(read.getSchema().getField("lastName"));
    }
  }

  @DataProvider(name = "dropStepConfigs")
  public static Object[][] dropStepConfigs() {
    return new Object[][] {
      {
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop\", \"when\": \"value.firstName=='Jane' || value.lastName=='Doe'\"},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"firstName\"]},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"lastName\"]}"
            + "]}"),
        true
      },
      {
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop-fields\", \"fields\": [\"firstName\"]},"
            + "    {\"type\": \"drop\", \"when\": \"value.firstName=='Jane' || value.lastName=='Doe'\"},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"lastName\"]}"
            + "]}"),
        true
      },
      {
        (""
            + "{\"steps\": ["
            + "    {\"type\": \"drop-fields\", \"fields\": [\"firstName\"]},"
            + "    {\"type\": \"drop-fields\", \"fields\": [\"lastName\"]},"
            + "    {\"type\": \"drop\", \"when\": \"value.firstName=='Jane' || value.lastName=='Doe'\"}"
            + "]}"),
        false
      }
    };
  }

  // TODO: just for demo. To be removed
  @Test
  void testRemoveMergeAndToString() throws Exception {
    String userConfig =
        (""
                + "{'steps': ["
                + "    {'type': 'drop-fields', 'fields': ['keyField1']},"
                + "    {'type': 'merge-key-value'},"
                + "    {'type': 'unwrap-key-value'},"
                + "    {'type': 'cast', 'schema-type': 'STRING'}"
                + "]}")
            .replace("'", "\"");
    Map<String, Object> config =
        new Gson().fromJson(userConfig, new TypeToken<Map<String, Object>>() {}.getType());
    TransformFunction transformFunction = new TransformFunction();

    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestContext context = new Utils.TestContext(record, config);
    transformFunction.initialize(context);
    Record<?> outputRecord = transformFunction.process(record.getValue(), context);

    assertEquals(
        outputRecord.getValue(),
        "{\"keyField2\": \"key2\", \"keyField3\": \"key3\", \"valueField1\": "
            + "\"value1\", \"valueField2\": \"value2\", \"valueField3\": \"value3\"}");
  }
}
