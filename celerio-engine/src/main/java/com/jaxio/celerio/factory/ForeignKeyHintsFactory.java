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
import com.jaxio.celerio.configuration.database.ForeignKey;
import com.jaxio.celerio.configuration.database.ImportedKey;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

@Service
@Slf4j
public class ForeignKeyHintsFactory {
    private final Config config;

    @Autowired
    public ForeignKeyHintsFactory(Config config) {
        this.config = config;
    }

    private void addImportedKeyDefinedInConfig(Entity entity) {
        for (Attribute attribute : entity.getCurrentAttributes()) {
            ImportedKey ik = getImportedKeysDefinedInConfig(attribute);
            if (ik != null) {
                Table table = config.getMetadata().getTableByName(attribute.getTableName());
                if (table.alreadyPresent(ik)) {
                    log.warn("Redundant configuration on entity " + entity.getName() + ". The columnConfig for column " + ik.getFkColumnName()
                            + " does not need to declare a targetTableName");
                } else {
                    table.addImportedKey(ik);
                }
            }
        }
    }

    public void setupForeignKeyHints(Entity entity) {
        addImportedKeyDefinedInConfig(entity);

        for (String tableName : entity.getTableNamesInvolvedInEntity()) {
            Table table = config.getMetadata().getTableByName(tableName);

            for (ForeignKey fk : table.getForeignKeys()) {
                if (config.getCelerio().getConfiguration().hasTable(fk.getImportedKey().getPkTableName())) {
                    markForeignKeyAttributes(entity, fk);
                }
            }
        }
    }

    private void markForeignKeyAttributes(Entity entity, ForeignKey fk) {
        if (fk.isSimple()) {
            Attribute attribute = entity.getAttributeByTableAndColumnName(fk.getFkTableName(), fk.getImportedKey().getFkColumnName());
            if (attribute != null) {
                attribute.setSimpleFk(true);
            }
        } else if (fk.isComposite()) {
            markCompositeForeignKeyAttributes(entity, fk);
        } else {
            throw new IllegalStateException("Foreign key " + fk.getName() + " has no columns");
        }
    }

    private void markCompositeForeignKeyAttributes(Entity entity, ForeignKey fk) {
        List<Attribute> attributes = newArrayList();
        for (ImportedKey column : fk.getImportedKeys()) {
            Attribute attribute = entity.getAttributeByTableAndColumnName(fk.getFkTableName(), column.getFkColumnName());
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        if (fk.getSize() == attributes.size()) {
            attributes.forEach(attribute -> attribute.setInCompositeFk(true));
        } else if (!attributes.isEmpty()) {
            log.warn("Ignoring composite FK spread across entity hierarchy: " + fk.getName());
        }
    }

    private ImportedKey getImportedKeysDefinedInConfig(Attribute attribute) {
        ImportedKey importedKey = null;

        if (attribute.getColumnConfig().hasTargetTableName()) {
            String targetTableName = attribute.getColumnConfig().getTargetTableName();

            // target entity
            Entity targetEntity = null;

            if (attribute.getColumnConfig().hasTargetEntityName()) {
                targetEntity = config.getProject().getEntityByName(attribute.getColumnConfig().lookupTargetEntityName());
            } else {
                targetEntity = config.getProject().getEntityBySchemaAndTableName(null, targetTableName);
                Assert.notNull(targetEntity, "The target Entity could not be found based on the targetTableName: " + targetTableName
                        + ". Please specify a targetEntityName for " + attribute.getFullColumnName());
            }

            Entity rootTargetEntity = targetEntity.isRoot() ? targetEntity : targetEntity.getRoot();

            if (rootTargetEntity.getPrimaryKey().isNoPk()) {
                throw new IllegalStateException("target table " + targetTableName + " has no primary key!");
            }

            if (rootTargetEntity.getPrimaryKey().isComposite()) {
                throw new IllegalStateException("target table " + targetTableName + " has a composite primary key!");
            }

            String targetColumnName;
            if (attribute.getColumnConfig().hasTargetColumnName()) {
                targetColumnName = attribute.getColumnConfig().getTargetColumnName();
            } else {
                targetColumnName = rootTargetEntity.getPrimaryKey().getAttribute().getColumnName();
            }

            importedKey = new ImportedKey();
            importedKey.setFkName(attribute.getColumnName() + "_fk_from_cfg");
            importedKey.setFkColumnName(attribute.getColumnName());
            importedKey.setPkTableName(targetTableName);
            importedKey.setPkColumnName(targetColumnName);
        }

        return importedKey;
    }
}
