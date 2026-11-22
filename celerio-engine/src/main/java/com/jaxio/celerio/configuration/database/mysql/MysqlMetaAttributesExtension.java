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

import com.jaxio.celerio.configuration.MetaAttribute;
import com.jaxio.celerio.configuration.database.Column;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.configuration.database.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.trim;

@Service
public class MysqlMetaAttributesExtension extends MysqlExtension {
    private static final String COLUMN_DETAILS = """
            SELECT COLUMN_NAME, COLUMN_TYPE, EXTRA
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = COALESCE(?, DATABASE()) AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

    @Override
    public void apply(Connection connection, Metadata metadata) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(connection);
        for (Table table : metadata.getTables()) {
            processColumnDetails(jdbcTemplate, table);
        }
    }

    private void processColumnDetails(JdbcTemplate jdbcTemplate, Table table) {
        jdbcTemplate.query(COLUMN_DETAILS, new Object[]{table.getCatalog(), table.getName()}, rs -> {
            Column column = table.getColumnByName(rs.getString("COLUMN_NAME"));
            if (column == null) {
                return;
            }
            column.addMetaAttribute(new MetaAttribute("type", rs.getString("COLUMN_TYPE")));
            String extra = rs.getString("EXTRA");
            if (isNotBlank(extra)) {
                column.addMetaAttribute(new MetaAttribute("extra", trim(extra)));
            }
        });
    }
}
