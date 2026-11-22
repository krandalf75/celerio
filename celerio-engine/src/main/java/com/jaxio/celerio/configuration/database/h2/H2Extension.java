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

package com.jaxio.celerio.configuration.database.h2;

import com.jaxio.celerio.configuration.database.Column;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.configuration.database.support.Extension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Service
@Slf4j
public class H2Extension implements Extension {

    private static final String H2_DEFAULT_SCHEMA_NAME = "PUBLIC";

    private static final String H2_DATABASE = "H2";

    public class Constraint {
        private String tableName;
        private String columnName;
        private String checkExpression;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getConstraint() {
            return checkExpression;
        }

        public void setConstraint(String constraint) {
            this.checkExpression = constraint;
        }

    }

    @Override
    public boolean applyable(Metadata metadata) {
        return H2_DATABASE.equalsIgnoreCase(metadata.getDatabaseInfo().getDatabaseProductName());
    }

    @Override
    public void apply(Connection connection, Metadata metadata) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(connection);
        String schemaName = getSchemaName(metadata);

        List<Constraint> constraints = jdbcTemplate.query(
                "SELECT table_name, column_name, check_constraint FROM information_schema.columns WHERE table_schema = ? AND LENGTH(check_constraint) > 0",
                new Object[]{schemaName}, (rs, rowNum) -> {
                    Constraint constraint = new Constraint();
                    constraint.setTableName(rs.getString("table_name"));
                    constraint.setColumnName(rs.getString("column_name"));
                    constraint.setConstraint(rs.getString("check_constraint"));
                    return constraint;
                });
        for (Constraint constraint : constraints) {
            addEnumValue(metadata, constraint.getTableName(), constraint.getColumnName(), constraint.getConstraint());
        }
    }

    private void addEnumValue(Metadata metadata, String tableName, String columnName, String constraint) {
        Table table = metadata.getTableByName(tableName);
        if (table == null) {
            log.debug("Ignoring H2 enum constraint for table {} outside the extracted metadata", tableName);
            return;
        }
        Column column = table.getColumnByName(columnName);
        if (column == null) {
            log.debug("Ignoring H2 enum constraint for column {}.{} outside the extracted metadata", tableName, columnName);
            return;
        }
        if (!column.getType().isString()) {
            log.debug("H2 numeric check constraints do not define enum constant names. Configure the ordinal enum for {}.{}", tableName, columnName);
            return;
        }
        H2EnumValuesExtractor extractor = new H2EnumValuesExtractor();
        List<String> values = extractor.extract(constraint);
        for (String value : values) {
            column.addEnumValue(value);
        }
    }

    private JdbcTemplate getJdbcTemplate(Connection connection) {
        DataSource dataSource = new SingleConnectionDataSource(connection, true);
        return new JdbcTemplate(dataSource);
    }

    private String getSchemaName(Metadata metadata) {
        String schemaName = metadata.getJdbcConnectivity().getSchemaName();
        if (schemaName == null) {
            schemaName = H2_DEFAULT_SCHEMA_NAME;
        }
        return schemaName;
    }
}
