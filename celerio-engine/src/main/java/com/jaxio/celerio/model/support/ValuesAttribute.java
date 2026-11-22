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

package com.jaxio.celerio.model.support;

import com.jaxio.celerio.configuration.entity.EnumConfig;
import com.jaxio.celerio.convention.GeneratedPackage;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.spi.AttributeSpi;
import com.jaxio.celerio.template.ImportsContext;
import com.jaxio.celerio.util.MappedType;

/** Builds expressions for the generated application's ValueGenerator. */
public class ValuesAttribute implements AttributeSpi {
    private Attribute attribute;

    @Override
    public void init(Attribute attribute) {
        this.attribute = attribute;
    }

    /**
     * @return "values"
     */
    @Override
    public String velocityVar() {
        return "values";
    }

    @Override
    public Object getTarget() {
        return this;
    }

    public String getDummy() {
        if (attribute.isInPk() || attribute.isUnique()) {
            return getUnique();
        }
        if (attribute.isEnum()) {
            return getDefaultEnum();
        }
        if (attribute.isBoolean()) {
            return "true";
        }
        if (attribute.isDate()) {
            return getDummyDate();
        }
        if (attribute.isEmail()) {
            return "\"dummy@dummy.com\"";
        }
        if (attribute.isContentType()) {
            return "\"application/text\"";
        }
        if (attribute.isFilename()) {
            return "\"dummy.txt\"";
        }
        if (attribute.isBlob()) {
            return "\"dummy\".getBytes()";
        }
        return getDummyScalar();
    }

    private String getDummyDate() {
        if (attribute.isLocalDateOrTime() || attribute.isZonedDateTime()) {
            return attribute.getMappedType().getFullJavaType() + ".now()";
        }
        if (attribute.getMappedType() == MappedType.M_UTILDATE) {
            return "new " + attribute.getMappedType().getJavaType() + "()";
        }
        if (attribute.getMappedType() == MappedType.M_TIMESTAMP) {
            return "new java.sql.Timestamp(new Date().getTime())";
        }
        return "new Date()";
    }

    private String getDummyScalar() {
        if (attribute.isInteger()) {
            return "1";
        }
        if (attribute.isLong()) {
            return "1l";
        }
        if (attribute.isFloat()) {
            return "1f";
        }
        if (attribute.isDouble()) {
            return "1d";
        }
        if (attribute.isBigInteger()) {
            return "BigInteger.ONE";
        }
        if (attribute.isBigDecimal()) {
            return "BigDecimal.ONE";
        }
        if (attribute.getMappedType().isChar()) {
            return "'a'";
        }
        if (attribute.getMappedType().isByte()) {
            return "(byte)1";
        }
        if (attribute.isUrl()) {
            return "\"http://www.jaxio.com\"";
        }
        if (attribute.getMappedType().isString() && attribute.isFixedSize()) {
            return javaString("d", attribute.getSize());
        }
        if (attribute.getMappedType().isString() && attribute.getColumnConfig().getMin() != null) {
            return javaString("a", attribute.getColumnConfig().getMin());
        }
        return "\"a\"";
    }

    private String javaString(String value, int count) {
        return "\"" + value.repeat(Math.max(0, count)) + "\"";
    }

    public String getNullDummy() {
        if (attribute.isNumeric()) {
            return "(" + attribute.getType() + ")null";
        } else {
            return "null";
        }
    }

    public String getUnique() {
        // limit to avoid memory errors
        int size = Math.min(attribute.getColumnConfig().getSize(), 255);

        if (attribute.isEnum()) {
            return getDefaultEnum();
        } else if (attribute.isBoolean()) {
            // can be used if we create unique key on several columns
            return "true";
        }

        // hack
        ImportsContext.addImport(GeneratedPackage.Util.getPackageName() + ".ValueGenerator");

        if (attribute.isDate()) {
            return "ValueGenerator.getUniqueDate()";
        } else if (attribute.isEmail()) {
            return "ValueGenerator.getUniqueEmail()";
        } else if (attribute.isBlob()) {
            return "ValueGenerator.getUniqueBytes(" + size + ")";
        } else if (attribute.isInteger()) {
            return "ValueGenerator.getUniqueInteger()";
        } else if (attribute.isLong()) {
            return "ValueGenerator.getUniqueLong()";
        } else if (attribute.isFloat()) {
            return "ValueGenerator.getUniqueFloat()";
        } else if (attribute.isDouble()) {
            return "ValueGenerator.getUniqueDouble()";
        } else if (attribute.isBigInteger()) {
            return "ValueGenerator.getUniqueBigInteger()";
        } else if (attribute.isBigDecimal()) {
            return "ValueGenerator.getUniqueBigDecimal()";
        } else if (attribute.getMappedType().isChar()) {
            return "ValueGenerator.getUniqueChar()";
        } else if (attribute.getMappedType().isByte()) {
            return "ValueGenerator.getUniqueByte()";
        } else {
            return "ValueGenerator.getUniqueString(" + size + ")";
        }
    }

    private String getDefaultEnum() {
        EnumConfig enumConfig = attribute.getEnumType().getConfig();
        ImportsContext.addImport(attribute.getEnumType().getModel().getFullType());
        return enumConfig.getName() + "." + enumConfig.getEnumValues().iterator().next().getName();
    }
}
