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

import com.jaxio.celerio.configuration.database.Column;
import com.jaxio.celerio.configuration.database.JdbcType;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.configuration.support.MetadataLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.oxm.XmlMappingException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;

@ContextConfiguration("classpath:applicationContext-celerio.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class CelerioLoaderTest {

    @Autowired
    private MetadataLoader loader;

    @Test
    public void roundTripsTableAndColumnMetadata() throws XmlMappingException, IOException {
        Column login = new Column();
        login.setName("LOGIN");
        login.setType(JdbcType.VARCHAR);
        Table account = new Table();
        account.setName("ACCOUNT");
        account.addColumn(login);
        Metadata metadata = new Metadata();
        metadata.add(account);

        Metadata loaded = loader.fromXml(loader.toXml(metadata));

        Table loadedAccount = loaded.getTableByName("ACCOUNT");
        assertThat(loadedAccount).isNotNull();
        assertThat(loadedAccount.getColumnByName("LOGIN")).isNotNull();
    }
}
