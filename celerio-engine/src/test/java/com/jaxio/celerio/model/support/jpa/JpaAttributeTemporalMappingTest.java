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

package com.jaxio.celerio.model.support.jpa;

import com.jaxio.celerio.configuration.database.JdbcType;
import com.jaxio.celerio.configuration.entity.ColumnConfig;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.template.ImportsContext;
import com.jaxio.celerio.template.ImportsHolder;
import com.jaxio.celerio.template.TemporalMappingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.jaxio.celerio.util.MappedType.M_UTILDATE;
import static org.fest.assertions.Assertions.assertThat;

public class JpaAttributeTemporalMappingTest {
    private static final String CURRENT_PACKAGE = "";

    @Before
    public void setUp() {
        TemporalMappingContext.clear();
        ImportsContext.setJakartaNamespace(false);
    }

    @After
    public void tearDown() {
        TemporalMappingContext.clear();
        ImportsContext.setJakartaNamespace(false);
        ImportsContext.setCurrentImportsHolder(null);
    }

    @Test
    public void legacyModeKeepsTemporalAnnotationsAndImports() {
        assertLegacyMapping(JdbcType.DATE, "@Temporal(DATE)", "javax.persistence.TemporalType.DATE");
        assertLegacyMapping(JdbcType.TIME, "@Temporal(TIME)", "javax.persistence.TemporalType.TIME");
        assertLegacyMapping(JdbcType.TIMESTAMP, "@Temporal(TIMESTAMP)", "javax.persistence.TemporalType.TIMESTAMP");
    }

    @Test
    public void hibernate7ModeUsesJdbcTypeForDateAndTime() {
        assertHibernate7Mapping(JdbcType.DATE, "@JdbcTypeCode(SqlTypes.DATE)");
        assertHibernate7Mapping(JdbcType.TIME, "@JdbcTypeCode(SqlTypes.TIME)");
    }

    @Test
    public void hibernate7TimestampUsesProviderDefaultWithoutImports() {
        ImportsHolder importsHolder = new ImportsHolder(CURRENT_PACKAGE);
        ImportsContext.setCurrentImportsHolder(importsHolder);
        TemporalMappingContext.setTemporalMapping(TemporalMapping.HIBERNATE7);

        assertThat(createAttribute(JdbcType.TIMESTAMP).getJpa().getTemporalAnnotation()).isEmpty();
        assertThat(importsHolder.toJavaImportString()).isEmpty();
    }

    @Test
    public void legacyModeMapsPersistenceImportsToJakarta() {
        ImportsHolder importsHolder = new ImportsHolder(CURRENT_PACKAGE);
        ImportsContext.setCurrentImportsHolder(importsHolder);
        ImportsContext.setJakartaNamespace(true);

        String annotation = createAttribute(JdbcType.DATE).getJpa().getTemporalAnnotation();

        assertThat(annotation).isEqualTo("@Temporal(DATE)");
        assertThat(importsHolder.toJavaImportString()).contains("import jakarta.persistence.Temporal;");
        assertThat(importsHolder.toJavaImportString()).contains("import static jakarta.persistence.TemporalType.DATE;");
        assertThat(importsHolder.toJavaImportString()).doesNotContain("javax.persistence");
    }

    @Test
    public void temporalPredicatesRemainIndependentOfMappingStrategy() {
        TemporalMappingContext.setTemporalMapping(TemporalMapping.HIBERNATE7);

        Attribute dateAttribute = createAttribute(JdbcType.DATE);
        Attribute timeAttribute = createAttribute(JdbcType.TIME);
        Attribute timestampAttribute = createAttribute(JdbcType.TIMESTAMP);

        assertThat(dateAttribute.isJavaUtilOnlyDate()).isTrue();
        assertThat(dateAttribute.isJavaUtilOnlyTime()).isFalse();
        assertThat(dateAttribute.isJavaUtilDateAndTime()).isFalse();
        assertThat(timeAttribute.isJavaUtilOnlyDate()).isFalse();
        assertThat(timeAttribute.isJavaUtilOnlyTime()).isTrue();
        assertThat(timestampAttribute.isJavaUtilDateAndTime()).isTrue();
    }

    private void assertLegacyMapping(JdbcType jdbcType, String expectedAnnotation, String expectedTemporalTypeImport) {
        ImportsHolder importsHolder = new ImportsHolder(CURRENT_PACKAGE);
        ImportsContext.setCurrentImportsHolder(importsHolder);
        TemporalMappingContext.setTemporalMapping(TemporalMapping.LEGACY);

        String annotation = createAttribute(jdbcType).getJpa().getTemporalAnnotation();

        assertThat(annotation).isEqualTo(expectedAnnotation);
        assertThat(importsHolder.toJavaImportString()).contains("import javax.persistence.Temporal;");
        assertThat(importsHolder.toJavaImportString()).contains(expectedTemporalTypeImport);
    }

    private void assertHibernate7Mapping(JdbcType jdbcType, String expectedAnnotation) {
        ImportsHolder importsHolder = new ImportsHolder(CURRENT_PACKAGE);
        ImportsContext.setCurrentImportsHolder(importsHolder);
        TemporalMappingContext.setTemporalMapping(TemporalMapping.HIBERNATE7);

        String annotation = createAttribute(jdbcType).getJpa().getTemporalAnnotation();

        assertThat(annotation).isEqualTo(expectedAnnotation);
        assertThat(importsHolder.toJavaImportString()).contains("import org.hibernate.annotations.JdbcTypeCode;");
        assertThat(importsHolder.toJavaImportString()).contains("import org.hibernate.type.SqlTypes;");
        assertThat(importsHolder.toJavaImportString()).doesNotContain("Temporal");
    }

    private Attribute createAttribute(JdbcType jdbcType) {
        ColumnConfig columnConfig = new ColumnConfig();
        columnConfig.setType(jdbcType);
        columnConfig.setMappedType(M_UTILDATE);

        Attribute attribute = new Attribute();
        attribute.setColumnConfig(columnConfig);
        return attribute;
    }
}
