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

package com.jaxio.celerio.factory;

import com.jaxio.celerio.configuration.database.Index;
import com.jaxio.celerio.configuration.database.IndexHolder;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.unique.CompositeUnique;
import com.jaxio.celerio.model.unique.SimpleUnique;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

@Service
@Slf4j
public class UniqueFactory {

    public void setupUniques(Entity entity) {
        Table table = entity.getTable();
        if (table == null) {
            return;
        }

        for (IndexHolder ih : table.getUniqueIndexHolders()) {
            if (ih.isSimple()) {
                Attribute a = entity.getAttributeByTableAndColumnName(ih.getTableName(), ih.getIndex().getColumnName());
                if (a != null) {
                    entity.addUnique(new SimpleUnique(ih.getName(), a));
                }
            } else if (ih.isComposite()) {
                addCompositeUnique(entity, ih);
            } else {
                throw new IllegalStateException("Should not happen!");
            }
        }
    }

    private void addCompositeUnique(Entity entity, IndexHolder index) {
        List<Attribute> attributes = newArrayList();
        for (Index column : index.getIndexes()) {
            Attribute attribute = entity.getAttributeByTableAndColumnName(index.getTableName(), column.getColumnName());
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        if (attributes.size() == index.getSize()) {
            entity.addUnique(new CompositeUnique(index.getName(), attributes));
        } else if (!attributes.isEmpty()) {
            log.warn("Skipping composite unique index {} because only some of its columns are mapped on {}",
                    index.getName(), entity.getName());
        }
    }
}
