/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.appdata.schemas;


import com.datatorrent.lib.appdata.dimensions.AggregatorInfo;
import com.datatorrent.lib.appdata.dimensions.DimensionsStaticAggregator;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Optional
 * Schema stub
 *{
 *  "time": {
 *    "from":1123455556656,
 *    "to":382390859384
 *   }
 *}
 */
public class SchemaDimensional implements Schema
{
  private static final Logger logger = LoggerFactory.getLogger(SchemaDimensional.class);

  public static final String SCHEMA_TYPE = "dimensional";
  public static final String SCHEMA_VERSION = "1.0";

  public static final List<Fields> VALID_KEYS = ImmutableList.of(new Fields(Sets.newHashSet(SchemaWithTime.FIELD_TIME)));
  public static final List<Fields> VALID_TIME_KEYS = ImmutableList.of(new Fields(Sets.newHashSet(SchemaWithTime.FIELD_TIME_FROM,
                                                                                                 SchemaWithTime.FIELD_TIME_TO)));

  private Long from;
  private Long to;

  private boolean changed = false;
  private String schemaJSON;

  private DimensionalEventSchema eventSchema;
  //private Map<String, DimensionsStaticAggregator> nameToAggregator;
  private JSONObject schema;
  private JSONObject time;

  private boolean fixedFromTo = false;

  public SchemaDimensional(String schemaStub,
                           DimensionalEventSchema eventSchema)
  {
    this(eventSchema);

    if(schemaStub != null) {
      fixedFromTo = true;
      try {
        setSchemaStub(schemaStub);
      }
      catch(Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public SchemaDimensional(DimensionalEventSchema eventSchema)
  {
    setEventSchema(eventSchema);

    try {
      initialize();
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  public AggregatorInfo getAggregatorInfo()
  {
    return eventSchema.getAggregatorInfo();
  }

  private void setEventSchema(DimensionalEventSchema eventSchema)
  {
    this.eventSchema = Preconditions.checkNotNull(eventSchema, "eventSchema");
  }

  private void setSchemaStub(String schemaStub) throws Exception
  {
    JSONObject jo = new JSONObject(schemaStub);
    SchemaUtils.checkValidKeysEx(jo, VALID_KEYS);

    JSONObject time = jo.getJSONObject(SchemaWithTime.FIELD_TIME);
    SchemaUtils.checkValidKeys(jo, VALID_TIME_KEYS);

    this.from = time.getLong(SchemaWithTime.FIELD_TIME_FROM);
    this.to = time.getLong(SchemaWithTime.FIELD_TIME_TO);
  }

  private void initialize() throws Exception
  {
    schema = new JSONObject();
    schema.put(SchemaTabular.FIELD_SCHEMA_TYPE, SchemaDimensional.SCHEMA_TYPE);
    schema.put(SchemaTabular.FIELD_SCHEMA_VERSION, SchemaDimensional.SCHEMA_VERSION);

    //time
    time = new JSONObject();
    schema.put(SchemaWithTime.FIELD_TIME, time);
    JSONArray bucketsArray = new JSONArray(eventSchema.getBucketsString());
    time.put(SchemaWithTime.FIELD_TIME_BUCKETS, bucketsArray);

    //keys
    JSONArray keys = new JSONArray(eventSchema.getKeysString());
    schema.put(DimensionalEventSchema.FIELD_KEYS, keys);

    //values;
    JSONArray values = new JSONArray();
    schema.put(SchemaTabular.FIELD_VALUES, values);

    FieldsDescriptor inputValuesDescriptor = eventSchema.getInputValuesDescriptor();
    Map<String, Map<String, Type>> allValueToAggregator = eventSchema.getSchemaAllValueToAggregatorToType();

    for(Map.Entry<String, Map<String, Type>> entry: allValueToAggregator.entrySet()) {
      String valueName = entry.getKey();

      for(Map.Entry<String, Type> entryAggType: entry.getValue().entrySet()) {
        String aggregatorName = entryAggType.getKey();
        Type outputValueType = entryAggType.getValue();

        JSONObject value = new JSONObject();
        String combinedName = valueName +
                              DimensionalEventSchema.ADDITIONAL_VALUE_SEPERATOR +
                              aggregatorName;
        value.put(SchemaTabular.FIELD_VALUES_NAME, combinedName);
        value.put(SchemaTabular.FIELD_VALUES_TYPE, outputValueType.getName());
        values.put(value);
      }
    }

    JSONArray dimensions = new JSONArray();

    for(int combinationID = 0;
        combinationID < eventSchema.getCombinationIDToKeys().size();
        combinationID++) {

      Fields fields = eventSchema.getCombinationIDToKeys().get(combinationID);
      Map<String, String> fieldToAggregatorAdditionalValues =
      eventSchema.getCombinationIDToFieldToAggregatorAdditionalValues().get(combinationID);

      JSONObject combination = new JSONObject();
      JSONArray combinationArray = new JSONArray();

      for(String field: fields.getFields()) {
        combinationArray.put(field);
      }

      combination.put(DimensionalEventSchema.FIELD_DIMENSIONS_COMBINATIONS, combinationArray);

      if(!fieldToAggregatorAdditionalValues.isEmpty()) {
        JSONArray additionalValueArray = new JSONArray();

        for(Map.Entry<String, String> entry: fieldToAggregatorAdditionalValues.entrySet()) {
          JSONObject additionalValueObject = new JSONObject();

          String valueName = entry.getKey();
          String aggregatorName = entry.getValue();
          String combinedName = valueName
                                + DimensionalEventSchema.ADDITIONAL_VALUE_SEPERATOR
                                + aggregatorName;
          Type inputValueType = inputValuesDescriptor.getType(valueName);

          DimensionsStaticAggregator aggregator
                  = eventSchema.getAggregatorInfo().getStaticAggregatorNameToStaticAggregator().get(aggregatorName);
          Type outputValueType = aggregator.getTypeMap().getTypeMap().get(inputValueType);

          additionalValueObject.put(DimensionalEventSchema.FIELD_VALUES_NAME, combinedName);
          additionalValueObject.put(DimensionalEventSchema.FIELD_VALUES_TYPE, outputValueType.getName());

          additionalValueArray.put(additionalValueObject);
        }

        combination.put(DimensionalEventSchema.FIELD_DIMENSIONS_ADDITIONAL_VALUES, additionalValueArray);
      }

      dimensions.put(combination);
    }

    schema.put(DimensionalEventSchema.FIELD_DIMENSIONS, dimensions);
  }

  public void setFrom(Long from)
  {
    this.from = from;
    changed = true;
  }

  public void setTo(Long to)
  {
    this.to = to;
    changed = true;
  }

  @Override
  public String getSchemaJSON()
  {
    if(!changed && schemaJSON != null) {
      return schemaJSON;
    }

    changed = false;
    Preconditions.checkState(!(from == null ^ to == null),
                             "Either both from and to should be set or both should be not set.");

    if(from != null) {
      Preconditions.checkState(to > from, "to must be greater than from.");
    }

    if(from == null) {
      time.remove(SchemaWithTime.FIELD_TIME_FROM);
      time.remove(SchemaWithTime.FIELD_TIME_TO);
    }
    else {
      try {
        time.put(SchemaWithTime.FIELD_TIME_FROM, from);
        time.put(SchemaWithTime.FIELD_TIME_TO, to);
      }
      catch(JSONException ex) {
        throw new RuntimeException(ex);
      }
    }

    schemaJSON = schema.toString();
    return schemaJSON;
  }

  public DimensionalEventSchema getGenericEventSchema()
  {
    return eventSchema;
  }

  @Override
  public String getSchemaType()
  {
    return SCHEMA_TYPE;
  }

  @Override
  public String getSchemaVersion()
  {
    return SCHEMA_VERSION;
  }

  /**
   * @return the fixedFromTo
   */
  public boolean isFixedFromTo()
  {
    return fixedFromTo;
  }
}
