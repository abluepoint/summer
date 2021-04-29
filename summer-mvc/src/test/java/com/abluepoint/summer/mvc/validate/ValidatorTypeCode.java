package com.abluepoint.summer.mvc.validate;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidatorTypeCode.java
 * Date:2020-03-18 15:39:18
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;

import java.lang.reflect.Constructor;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum ValidatorTypeCode implements Keyword, ErrorMessageType {
    ADDITIONAL_PROPERTIES("additionalProperties", "1001", new String("{0}.{1}: is not defined in the schema and the schema does not allow additional properties"), AdditionalPropertiesValidator.class, 15L), ALL_OF("allOf", "1002", new String("{0}: should be valid to all the schemas {1}"), AllOfValidator.class, 15L), ANY_OF("anyOf", "1003", new String("{0}: should be valid to any of the schemas {1}"), AnyOfValidator.class, 15L), CROSS_EDITS("crossEdits", "1004", new String("{0}: has an error with 'cross edits'"), (Class) null, 15L), DEPENDENCIES("dependencies", "1007", new String("{0}: has an error with dependencies {1}"), DependenciesValidator.class, 15L), EDITS("edits", "1005", new String("{0}: has an error with 'edits'"), (Class) null, 15L), ENUM("enum", "1008", new String("{0}: does not have a value in the enumeration {1}"), EnumValidator.class, 15L), FORMAT("format", "1009", new String("{0}: does not match the {1} pattern {2}"), (Class) null, 15L) {
        public JsonValidator newValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) throws Exception {
            throw new UnsupportedOperationException("Use FormatKeyword instead");
        }
    }, ITEMS("items", "1010", new String("{0}[{1}]: no validator found at this index"), ItemsValidator.class, 15L), MAXIMUM("maximum", "1011", new String("{0}: must have a maximum value of {1}"), MaximumValidator.class, 15L), MAX_ITEMS("maxItems", "1012", new String("{0}: there must be a maximum of {1} items in the array"), MaxItemsValidator.class, 15L), MAX_LENGTH("maxLength", "1013", new String("{0}: may only be {1} characters long"), MaxLengthValidator.class, 15L), MAX_PROPERTIES("maxProperties", "1014", new String("{0}: may only have a maximum of {1} properties"), MaxPropertiesValidator.class, 15L), MINIMUM("minimum", "1015", new String("{0}: must have a minimum value of {1}"), MinimumValidator.class, 15L), MIN_ITEMS("minItems", "1016", new String("{0}: there must be a minimum of {1} items in the array"), MinItemsValidator.class, 15L), MIN_LENGTH("minLength", "1017", new String("{0}: must be at least {1} characters long"), MinLengthValidator.class, 15L), MIN_PROPERTIES("minProperties", "1018", new String("{0}: should have a minimum of {1} properties"), MinPropertiesValidator.class, 15L), MULTIPLE_OF("multipleOf", "1019", new String("{0}: must be multiple of {1}"), MultipleOfValidator.class, 15L), NOT_ALLOWED("notAllowed", "1033", new String("{0}.{1}: is not allowed but it is in the data"), NotAllowedValidator.class, 15L), NOT("not", "1020", new String("{0}: should not be valid to the schema {1}"), NotValidator.class, 15L), ONE_OF("oneOf", "1022", new String("{0}: should be valid to one and only one of the schemas {1}"), OneOfValidator.class, 15L), PATTERN_PROPERTIES("patternProperties", "1024", new String("{0}: has some error with 'pattern properties'"), PatternPropertiesValidator.class, 15L), PATTERN("pattern", "1023", new String("{0}: does not match the regex pattern {1}"), PatternValidator.class, 15L), PROPERTIES("properties", "1025", new String("{0}: has an error with 'properties'"), PropertiesValidator.class, 15L), READ_ONLY("readOnly", "1032", new String("{0}: is a readonly field, it cannot be changed"), ReadOnlyValidator.class, 15L), REF("$ref", "1026", new String("{0}: has an error with 'refs'"), RefValidator.class, 15L), REQUIRED("required", "1028", new String("{0}.{1}: is missing but it is required"), RequiredValidator.class, 15L), TYPE("type", "1029", new String("{0}: {1} found, {2} expected"), TypeValidator.class, 15L), UNION_TYPE("unionType", "1030", new String("{0}: {1} found, but {2} is required"), UnionTypeValidator.class, 15L), UNIQUE_ITEMS("uniqueItems", "1031", new String("{0}: the items in the array must be unique"), UniqueItemsValidator.class, 15L), DATETIME("date-time", "1034", new String("{0}: {1} is an invalid {2}"), (Class) null, 15L), UUID("uuid", "1035", new String("{0}: {1} is an invalid {2}"), (Class) null, 15L), ID("id", "1036", new String("{0}: {1} is an invalid segment for URI {2}"), (Class) null, 15L), IF_THEN_ELSE("if", "1037",  null, IfValidator.class, 12L), EXCLUSIVE_MAXIMUM("exclusiveMaximum", "1038", new String("{0}: must have a exclusive maximum value of {1}"), ExclusiveMaximumValidator.class, 14L), EXCLUSIVE_MINIMUM("exclusiveMinimum", "1039", new String("{0}: must have a exclusive minimum value of {1}"), ExclusiveMinimumValidator.class, 14L), TRUE("true", "1040",  null, TrueValidator.class, 14L), FALSE("false", "1041", new String("Boolean schema false is not valid"), FalseValidator.class, 14L), CONST("const", "1042", new String("{0}: must be a constant value {1}"), ConstValidator.class, 14L), CONTAINS("contains", "1043", new String("{0}: does not contain an element that passes these validations: {1}"), ContainsValidator.class, 14L);

    private static Map<String, ValidatorTypeCode> constants = new HashMap();
    private static SpecVersion specVersion = new SpecVersion();
    private final String value;
    private final String errorCode;
    private final String messageFormat;
    private final String errorCodeKey;
    private final Class validator;
    private final long versionCode;

    private ValidatorTypeCode(String value, String errorCode, String messageFormat, Class validator, long versionCode) {
        this.value = value;
        this.errorCode = errorCode;
        this.messageFormat = messageFormat;
        this.errorCodeKey = value + "ErrorCode";
        this.validator = validator;
        this.versionCode = versionCode;
    }

    public static List<ValidatorTypeCode> getNonFormatKeywords(SpecVersion.VersionFlag versionFlag) {
        List<ValidatorTypeCode> result = new ArrayList();
        ValidatorTypeCode[] var2 = values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            ValidatorTypeCode keyword = var2[var4];
            if (!FORMAT.equals(keyword) && specVersion.getVersionFlags(keyword.versionCode).contains(versionFlag)) {
                result.add(keyword);
            }
        }

        return result;
    }

    public static ValidatorTypeCode fromValue(String value) {
        ValidatorTypeCode constant = (ValidatorTypeCode) constants.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

    public JsonValidator newValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) throws Exception {
        if (this.validator == null) {
            throw new UnsupportedOperationException("No suitable validator for " + this.getValue());
        } else {
            Constructor<JsonValidator> c = this.validator.getConstructor(String.class, JsonNode.class, JsonSchema.class, ValidationContext.class);
            return (JsonValidator) c.newInstance(schemaPath + "/" + this.getValue(), schemaNode, parentSchema, validationContext);
        }
    }

    @Override
    public String toString() {
        return "ValidatorTypeCode{" + "value='" + value + '\'' + ", errorCode='" + errorCode + '\'' + ", messageFormat=" + messageFormat + ", errorCodeKey='" + errorCodeKey + '\'' + '}';
    }

    public String getValue() {
        return this.value;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public MessageFormat getMessageFormat() {
        return null;
    }

    public String getMessageKey() {
        return this.messageFormat;
    }

    public String getErrorCodeKey() {
        return this.errorCodeKey;
    }

    public long getVersionCode() {
        return this.versionCode;
    }

    static {
        ValidatorTypeCode[] var0 = values();
        int var1 = var0.length;

        for (int var2 = 0; var2 < var1; ++var2) {
            ValidatorTypeCode c = var0[var2];
            constants.put(c.value, c);
        }

    }

    public static Map<String, ValidatorTypeCode> getConstants() {
        return constants;
    }
}