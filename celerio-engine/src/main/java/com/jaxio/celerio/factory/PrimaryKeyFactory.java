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

import com.jaxio.celerio.Config;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.PrimaryKey;
import com.jaxio.celerio.model.primarykey.CompositePrimaryKey;
import com.jaxio.celerio.model.primarykey.NoPrimaryKey;
import com.jaxio.celerio.model.primarykey.SimplePrimaryKey;
import com.jaxio.celerio.model.unique.CompositeUnique;
import com.jaxio.celerio.model.unique.SimpleUnique;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static com.jaxio.celerio.configuration.TrueFalse.TRUE;

@Service
@Slf4j
public class PrimaryKeyFactory {

    private final Config config;

    @Autowired
    public PrimaryKeyFactory(Config config) {
        this.config = config;
    }

    public void setupPrimaryKey(Entity entity) {
        PrimaryKey pk = null;

        if (entity.isRoot()) {
            Table table = entity.getTable();

            if (table.hasSimplePrimaryKey()) {
                pk = buildSimplePrimaryKey(entity);
            } else if (table.hasCompositePrimaryKey()) {
                pk = buildCompositePrimaryKey(entity);
            } else {
                pk = findAppropriatePrimaryKey(entity);
                log.error("No 'official' primary key found for table: " + entity.getTableName() + ". Using this column as pk instead: " + pk
                        + ". You should declare a PK...");
            }
        } else {
            pk = new NoPrimaryKey(entity);
        }

        entity.setPrimaryKey(pk);
    }

    private PrimaryKey buildSimplePrimaryKey(Entity entity) {
        Assert.isTrue(entity.isRoot(), "A primary key can only be built for a root entity");

        Table table = entity.getTable();
        Attribute pkAttribute = entity.getAttributeByTableAndColumnName(table.getName(), table.getPrimaryKey());
        pkAttribute.setSimplePk(true);
        if (config.getCelerio().getConfiguration().getConventions().getRenamePkToIdentifiableProperty() == TRUE) {
            overridePkFieldName(pkAttribute);
        }
        return new SimplePrimaryKey(entity, pkAttribute);
    }

    private void overridePkFieldName(Attribute pkAttribute) {
        log.info("Force designed pk " + pkAttribute.getFullColumnName() + " fieldName to "
                + config.getCelerio().getConfiguration().getConventions().getIdentifiableProperty());
        pkAttribute.getColumnConfig().setFieldName(config.getCelerio().getConfiguration().getConventions().getIdentifiableProperty());
    }

    private PrimaryKey buildCompositePrimaryKey(Entity entity) {
        Assert.isTrue(entity.isRoot(), "A primary key can only be built for a root entity");

        Table table = entity.getTable();
        List<String> pkColumns = table.getPrimaryKeys();

        List<Attribute> attributes = newArrayList();
        for (String pkColumn : pkColumns) {
            Attribute pkAttribute = entity.getAttributeByTableAndColumnName(table.getName(), pkColumn);
            pkAttribute.setInCpk(true);
            attributes.add(pkAttribute);
        }
        return new CompositePrimaryKey(entity, attributes);
    }

    private PrimaryKey findAppropriatePrimaryKey(Entity entity) {
        Optional<Attribute> candidate = findIdAttribute(entity)
                .or(() -> firstNonNullableUniqueAttribute(entity))
                .or(() -> firstUniqueAttribute(entity));
        if (candidate.isEmpty() && entity.getCompositeUniques().isNotEmpty()) {
            return firstCompositeUniqueAsPrimaryKey(entity);
        }

        Attribute attribute = candidate.orElseGet(() -> firstAttribute(entity));
        attribute.setSimplePk(true);
        if (config.getCelerio().getConfiguration().getConventions().getRenamePkToIdentifiableProperty() == TRUE) {
            overridePkFieldName(attribute);
        }
        return new SimplePrimaryKey(entity, attribute);
    }

    private PrimaryKey firstCompositeUniqueAsPrimaryKey(Entity entity) {
        CompositeUnique unique = (CompositeUnique) entity.getCompositeUniques().getFirst();
        return new CompositePrimaryKey(entity, unique.getAttributes());
    }

    private Optional<Attribute> findIdAttribute(Entity entity) {
        return entity.getAttributes().getList().stream()
                .filter(attribute -> attribute.getColumnName().equalsIgnoreCase("id"))
                .findFirst();
    }

    private Optional<Attribute> firstNonNullableUniqueAttribute(Entity entity) {
        return entity.getSimpleUniques().getList().stream()
                .filter(SimpleUnique.class::isInstance)
                .map(SimpleUnique.class::cast)
                .filter(SimpleUnique::isPotentialKey)
                .map(SimpleUnique::getAttribute)
                .findFirst();
    }

    private Optional<Attribute> firstUniqueAttribute(Entity entity) {
        return entity.getSimpleUniques().getList().stream()
                .filter(SimpleUnique.class::isInstance)
                .map(SimpleUnique.class::cast)
                .map(SimpleUnique::getAttribute)
                .findFirst();
    }

    private Attribute firstAttribute(Entity entity) {
        return entity.getAttributes().getList().iterator().next();
    }
}
