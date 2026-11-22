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

package com.jaxio.celerio.configuration.database.mysql;

import com.jaxio.celerio.configuration.database.Column;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.configuration.database.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;

@Service
public class MysqlEnumExtension extends MysqlExtension {
    private static final String ENUM_COLUMNS = """
            SELECT COLUMN_NAME, COLUMN_TYPE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = COALESCE(?, DATABASE()) AND TABLE_NAME = ? AND DATA_TYPE = 'enum'
            ORDER BY ORDINAL_POSITION
            """;

    private final MysqlEnumValuesExtractor mysqlEnumValuesExtractor = new MysqlEnumValuesExtractor();

    @Override
    public void apply(Connection connection, Metadata metadata) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(connection);
        for (Table table : metadata.getTables()) {
            processEnums(jdbcTemplate, table);
        }
    }

    private void processEnums(JdbcTemplate jdbcTemplate, Table table) {
        jdbcTemplate.query(ENUM_COLUMNS, new Object[]{table.getCatalog(), table.getName()}, rs -> {
            Column column = table.getColumnByName(rs.getString("COLUMN_NAME"));
            if (column != null) {
                mysqlEnumValuesExtractor.extract(rs.getString("COLUMN_TYPE")).forEach(column::addEnumValue);
            }
        });
    }
}
