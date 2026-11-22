/*
 * Copyright 2015 JAXIO http://www.jaxio.com
 *
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

package com.jaxio.celerio.util;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang.BooleanUtils.toBoolean;

public enum MappedType {
    // NOTE: fullType of byte[] is useless in theory but for consistency and to avoid generation errors we need
    // to set one. We set it to java.lang.Byte in order to have it ignored (as any package starting with java.lang.*).
    M_ARRAY("Array", "java.sql.Array"), //
    M_BIGDECIMAL("BigDecimal", "java.math.BigDecimal"), //
    M_BIGINTEGER("BigInteger", "java.math.BigInteger"), //
    M_BOOLEAN("Boolean", "java.lang.Boolean"), //
    M_BYTES("byte[]", Byte.class.getName()), //
    M_CLOB("String", "java.lang.String"), //
    M_DOUBLE("Double", "java.lang.Double"), //
    M_FLOAT("Float", "java.lang.Float"), //
    M_BLOB("byte[]", Byte.class.getName()), //
    M_INTEGER("Integer", "java.lang.Integer"), //
    M_LONG("Long", "java.lang.Long"), //
    M_REF("Ref", "java.sql.Ref"), //
    M_STRING("String", "java.lang.String"), //
    M_CHAR("Character", "java.lang.Character"), //
    M_BYTE("Byte", Byte.class.getName()), //
    M_LOCALDATE("LocalDate", "java.time.LocalDate"), //
    M_LOCALDATETIME("LocalDateTime", "java.time.LocalDateTime"), //
    M_ZONEDDATETIME("ZonedDateTime", "java.time.ZonedDateTime"),
    M_SQLDATE("java.sql.Date", "java.sql.Date"), //
    M_UTILDATE("Date", "java.util.Date"), //
    M_INSTANT("Instant", "java.time.Instant"),
    M_TIME("java.sql.Time", "java.sql.Time"), //
    M_TIMESTAMP("java.sql.Timestamp", "java.sql.Timestamp"), //
    M_URL("java.net.URL", "java.net.URL"), //
    M_OBJECT("Object", "java.lang.Object");

    private final String javaType;

    private final String fullJavaType;

    private MappedType(String javaType, String fullJavaType) {
        this.javaType = javaType;
        this.fullJavaType = fullJavaType;
    }

    public static MappedType fromFullJavaType(String fullJavaType) {
        for (MappedType mappedType : MappedType.values()) {
            if (mappedType.getFullJavaType().equals(fullJavaType)) {
                return mappedType;
            }
        }

        return null;
    }

    public String getJavaType() {
        return javaType;
    }

    public String getFullJavaType() {
        return fullJavaType;
    }

    public boolean isSupportedType() {
        switch (this) {
            case M_ARRAY, M_OBJECT, M_REF, M_URL:
                return false;
            default:
                return true;
        }
    }

    public boolean isNumeric() {
        switch (this) {
            case M_BIGDECIMAL, M_BIGINTEGER, M_DOUBLE, M_FLOAT, M_INTEGER, M_LONG:
                return true;
            default:
                return false;
        }
    }

    public boolean isLong() {
        return this == M_LONG;
    }

    public boolean isInteger() {
        return this == M_INTEGER;
    }

    public boolean isBigInteger() {
        return this == M_BIGINTEGER;
    }

    public final boolean isFloat() {
        return this == M_FLOAT;
    }

    public final boolean isDouble() {
        return this == M_DOUBLE;
    }

    public boolean isBigDecimal() {
        return this == M_BIGDECIMAL;
    }

    /**
     * is Eligible for Version column Version numbers may be of Hibernate type long, integer, short, timestamp or calendar.
     *
     * @return true if numeric, false otherwise
     */
    public boolean isEligibleForVersion() {
        return isInteger() || isLong() || (isDate() && this != M_LOCALDATE && this != M_LOCALDATETIME && this != M_ZONEDDATETIME);
    }

    public boolean isString() {
        return this == M_STRING || this == M_CLOB;
    }

    public boolean isChar() {
        return this == M_CHAR;
    }

    public boolean isObject() {
        return this == M_OBJECT;
    }

    public boolean isBoolean() {
        return this == M_BOOLEAN;
    }

    public boolean isDate() {
        switch (this) {
            case M_SQLDATE, M_UTILDATE, M_TIME, M_TIMESTAMP, M_LOCALDATE, M_LOCALDATETIME, M_ZONEDDATETIME, M_INSTANT:
                return true;
            default:
                return false;
        }
    }

    public boolean isJavaUtilDate() {
        return this == M_UTILDATE;
    }

    public boolean isLocalDate() {
        return this == M_LOCALDATE;
    }

    public boolean isLocalDateTime() {
        return this == M_LOCALDATETIME;
    }

    public boolean isZonedDateTime() {
        return this == M_ZONEDDATETIME;
    }

    public boolean isInstant() {
        return this == M_INSTANT;
    }

    public boolean isByte() {
        return this == M_BYTE;
    }

    public boolean isLob() {
        return isBlob() || isClob();
    }

    public boolean isBlob() {
        return this == M_BLOB || this == M_BYTES;
    }

    public boolean isClob() {
        return this == M_CLOB;
    }

    public boolean isComparable() {
        switch (this) {
            case M_BIGDECIMAL, M_BIGINTEGER, M_BOOLEAN, M_CLOB, M_DOUBLE, M_FLOAT, M_INTEGER, M_LONG, M_STRING, M_CHAR, M_BYTE, M_SQLDATE, M_UTILDATE, M_INSTANT:
                return true;
            case M_TIME, M_REF, M_BLOB, M_BYTES, M_ARRAY, M_TIMESTAMP, M_URL, M_OBJECT:
            default:
                return false;
        }
    }

    public String getJavaValue(String value) {
        if (value == null) {
            return null;
        }

        try {
            return switch (this) {
                case M_STRING -> "\"" + value.replace("\"", "\\\"") + "\"";
                case M_BOOLEAN -> Boolean.toString(toBoolean(value));
                case M_BIGINTEGER -> "BigInteger.valueOf(" + value + ")";
                case M_INTEGER -> Integer.toString(Integer.parseInt(value));
                case M_LONG -> Long.parseLong(value) + "L";
                case M_DOUBLE -> Double.parseDouble(value) + "d";
                case M_SQLDATE, M_UTILDATE, M_TIME, M_TIMESTAMP -> dateExpression(value, "new Date()");
                case M_LOCALDATE, M_LOCALDATETIME, M_ZONEDDATETIME, M_INSTANT -> dateExpression(value, getJavaType() + ".now()");
                default -> null;
            };
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private String dateExpression(String value, String expression) {
        return isCurrentDate(value.toUpperCase()) ? expression : null;
    }

    private final List<String> isNow = newArrayList("now()", "sysdate", "current_time");

    private boolean isCurrentDate(String value) {
        return value != null && isNow.contains(value.toLowerCase());
    }

    public String getJavaDefinition(String value) {
        switch (this) {
            case M_STRING:
                return value;
            case M_BOOLEAN:
                return "Boolean.valueOf(" + value + ")";
            case M_BIGINTEGER:
                return "BigInteger.valueOf(" + value + ")";
            case M_INTEGER, M_LONG, M_FLOAT, M_DOUBLE, M_CLOB, M_CHAR, M_BYTE, M_BIGDECIMAL:
                return "new " + this.getJavaType() + "(" + value + ")";
            case M_BLOB, M_BYTES:
                return value + ".getBytes()";
            case M_SQLDATE, M_UTILDATE, M_TIME, M_TIMESTAMP:
                return "new " + this.getFullJavaType() + "(Long.parseLong(" + value + "))";
            case M_LOCALDATETIME, M_LOCALDATE, M_ZONEDDATETIME, M_INSTANT:
                return this.getFullJavaType() + ".parse(" + value + ")";
            case M_ARRAY, M_REF, M_URL, M_OBJECT:
            default:
                throw new IllegalArgumentException("getJavaDefinition for " + this.name() + " is not implemented yet");
        }
    }

    public String getToStringMethod() {
        if (this == M_STRING) {
            return "";
        }
        return ".toString()";
    }

    public boolean isEnumEligible() {
        return !isByte() && !isBlob() && !isDate() && !isObject();
    }

    public String getPackageName() {
        int lastDot = getFullJavaType().lastIndexOf('.');
        return (lastDot > 0) ? getFullJavaType().substring(0, lastDot) : "";
    }

    public String getHibernateType() {
        if (isString()) {
            return "org.hibernate.type.StringType";
        } else if (isInteger()) {
            return "org.hibernate.type.IntegerType";
        } else if (isLong()) {
            return "org.hibernate.type.LongType";
        } else {
            throw new UnsupportedOperationException(this + " does not support Hibernate types");
        }
    }

    public boolean isJavaBaseClass() {
        String fullType = getFullJavaType();
        if (fullType.startsWith("java.lang")) {
            return true;
        }
        String[] nativeTypes = {"boolean", "byte", "char", "short", "int", "float", "double", "void"};
        for (String nativeType : nativeTypes) {
            if (fullType.startsWith(nativeType)) {
                return true;
            }
        }
        return false;
    }
}
