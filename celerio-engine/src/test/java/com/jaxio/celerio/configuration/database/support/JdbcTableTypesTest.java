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

package com.jaxio.celerio.configuration.database.support;

import com.jaxio.celerio.configuration.database.JdbcConnectivity;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.configuration.database.TableType;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JdbcTableTypesTest {
    private static final String SAMPLE_TABLE = "SAMPLE";
    private static final String SAMPLE_VIEW = "SAMPLE_VIEW";

    @Test
    public void customTypesOverrideTheDefaultFilter() throws Exception {
        MetadataExtractor extractor = new MetadataExtractor();
        JdbcConnectivity config = new JdbcConnectivity(TableType.TABLE);
        config.setSchemaName("PUBLIC");

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbc_table_types");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SAMPLE (ID INT PRIMARY KEY)");
            statement.execute("CREATE VIEW SAMPLE_VIEW AS SELECT ID FROM SAMPLE");

            Metadata tables = extractor.extract(config, connection);
            assertNotNull(tables.getTableByName(SAMPLE_TABLE));
            assertNull(tables.getTableByName(SAMPLE_VIEW));

            config.setJdbcReverseTypes("VIEW");
            Metadata views = extractor.extract(config, connection);
            assertNull(views.getTableByName(SAMPLE_TABLE));
            assertEquals(TableType.VIEW, views.getTableByName(SAMPLE_VIEW).getType());

            config.setJdbcReverseTypes("TABLE,VIEW");
            Metadata both = extractor.extract(config, connection);
            assertNotNull(both.getTableByName(SAMPLE_TABLE));
            assertNotNull(both.getTableByName(SAMPLE_VIEW));
        }
    }

    @Test
    public void mapsVendorTableTypesToCelerioTypes() {
        assertEquals(TableType.VIEW, MetadataExtractor.resolveTableType("materialized view"));
        assertEquals(TableType.TABLE, MetadataExtractor.resolveTableType("PARTITIONED TABLE"));
        assertEquals(TableType.VIEW, MetadataExtractor.resolveTableType("VIEW"));
    }
}
