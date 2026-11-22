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

package com.jaxio.celerio.model.relation;

import com.jaxio.celerio.configuration.entity.ColumnConfig;
import com.jaxio.celerio.configuration.entity.EntityConfig;
import com.jaxio.celerio.factory.RelationCollisionUtil;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.primarykey.CompositePrimaryKey;
import com.jaxio.celerio.model.unique.CompositeUnique;
import org.junit.Test;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class AbstractRelationTest {
    @Test
    public void usesOnlyUniqueConstraintsCoveredByTheForeignKey() {
        Entity source = new Entity();
        Attribute first = column(source, "FIRST");
        Attribute second = column(source, "SECOND");
        Attribute third = column(source, "THIRD");
        Attribute outside = column(source, "OUTSIDE");
        CompositeOneToMany relation = relation(source, List.of(first, second, third));

        source.addUnique(new CompositeUnique("UNRELATED", List.of(first, outside)));
        assertThat(relation.isUnique()).isFalse();

        source.addUnique(new CompositeUnique("MATCHING", List.of(second, first)));
        assertThat(relation.isUnique()).isTrue();
    }

    @Test
    public void recognizesCompositePrimaryKeyColumns() {
        Entity source = new Entity();
        EntityConfig entityConfig = new EntityConfig();
        entityConfig.setEntityName("Source");
        source.setEntityConfig(entityConfig);
        Attribute first = column(source, "FIRST");
        Attribute second = column(source, "SECOND");
        source.setPrimaryKey(new CompositePrimaryKey(source, List.of(first, second)));

        assertThat(relation(source, List.of(second, first)).isUnique()).isTrue();
    }

    private static Attribute column(Entity entity, String name) {
        ColumnConfig config = new ColumnConfig();
        config.setTableName("SOURCE");
        config.setColumnName(name);
        config.setFieldName(name);
        Attribute attribute = new Attribute(null, new RelationCollisionUtil(), null);
        attribute.setEntity(entity);
        attribute.setColumnConfig(config);
        return attribute;
    }

    private static CompositeOneToMany relation(Entity source, List<Attribute> columns) {
        Entity target = new Entity();
        return new CompositeOneToMany(source.getModel(), target.getModel(), columns, source, target, columns);
    }
}
