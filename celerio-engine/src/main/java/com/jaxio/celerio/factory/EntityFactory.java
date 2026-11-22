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
import com.jaxio.celerio.configuration.Celerio;
import com.jaxio.celerio.configuration.CelerioTemplateContext;
import com.jaxio.celerio.configuration.Configuration;
import com.jaxio.celerio.configuration.EntityContextProperty;
import com.jaxio.celerio.configuration.database.Column;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.configuration.database.TableType;
import com.jaxio.celerio.configuration.entity.ColumnConfig;
import com.jaxio.celerio.configuration.entity.EntityConfig;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.Project;
import com.jaxio.celerio.model.support.ClassNamer2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static javax.persistence.InheritanceType.*;

@Service
@Slf4j
public class EntityFactory {

    private static final String WEB_DOMAIN_PACKAGE = "web.domain";

    private final Config config;

    private final ApplicationContext applicationContext;

    private final EntityConfigFactory entityConfigFactory;

    private final ColumnConfigFactory columnConfigFactory;

    private final AttributeFactory attributeFactory;

    @Autowired
    public EntityFactory(Config config,
                         ApplicationContext applicationContext,
                         EntityConfigFactory entityConfigFactory,
                         ColumnConfigFactory columnConfigFactory,
                         AttributeFactory attributeFactory) {
        this.config = config;
        this.applicationContext = applicationContext;
        this.entityConfigFactory = entityConfigFactory;
        this.columnConfigFactory = columnConfigFactory;
        this.attributeFactory = attributeFactory;
    }

    // -------------------------------------------
    // Public service
    // -------------------------------------------

    void prepareConfiguredEntities(Celerio celerio) {
        celerio.setEntityConfigs(entityConfigFactory.filterEntityConfigs(celerio.getEntityConfigs()));
        log.info("Checking configuration consistency...");
        entityConfigFactory.assertEntityConfigListIsConsistent(celerio.getEntityConfigs());
    }

    void buildEntities(Project project, List<EntityConfig> entityConfigs) {
        Set<String> tablesProcessed = new HashSet<String>();
        addEntitiesDefinedInUserConfiguration(project, entityConfigs, tablesProcessed);
        addRemainingEntities(project, tablesProcessed);
    }

    private void addRemainingEntities(Project project, Set<String> tableProcessed) {
        for (Table table : config.getMetadata().getTables()) {
            // Make sure we do not process tables twice
            if (tableProcessed.contains(table.getName().toUpperCase())) {
                // skip default entity creation
                continue;
            }

            addDefaultEntity(project, table, tableProcessed);
        }
    }

    private void addDefaultEntity(Project project, Table table, Set<String> tableProcessed) {
        if (!config.getCelerio().getConfiguration().hasTable(table.getName())) {
            log.info("Skipping table '" + table.getName() + "'. Reason: excluded in configuration");
            return;
        }
        EntityConfig entityConfig = entityConfigFactory.buildEntityConfig(table);
        if (project.hasEntityByName(entityConfig.getEntityName())) {
            log.warn("Skip default entity creation for table name=" + table.getName() + " entity name=" + entityConfig.getEntityName()
                    + " as it was already registered!");
            return;
        }
        Entity entity = buildEntity(entityConfig);
        log.info("Adding entity " + entity.getName());
        project.addEntity(entity);
        project.putEntity(entity);
        tableProcessed.add(table.getName().toUpperCase());
    }

    private void addEntitiesDefinedInUserConfiguration(Project project, List<EntityConfig> entityConfigs, Set<String> tableProcessed) {
        for (EntityConfig entityConfig : entityConfigs) {
            if (entityConfig.hasTableName() && !config.getCelerio().getConfiguration().hasTable(entityConfig.getTableName())) {
                log.info("Skipping table '" + entityConfig.getTableName() + "'. Reason: excluded in configuration");
                continue;
            }

            entityConfigFactory.applyFallBacks(entityConfig);
            Entity entity = buildEntity(entityConfig);
            log.info("Adding entity " + entity.getName());
            project.addEntity(entity);

            if (!entity.hasInheritance()) {
                project.putEntity(entity);
            }

            // note: we may add a table twice, this is OK, this code is just here to
            // avoid processing tables not present in the conf
            // please check addRemainingEntities
            tableProcessed.add(entity.getTableName().toUpperCase());
        }
    }

    public Entity buildEntity(EntityConfig entityConfig) {
        Entity entity = applicationContext.getBean(Entity.class);
        entityConfigFactory.applyFallBacks(entityConfig);
        entity.setEntityConfig(entityConfig);

        Table table = getTableStrict(entityConfig);
        if (table.getType() == TableType.VIEW) {
            entity.setView(true);
        }
        entity.setTable(table);

        namerDefault(entity);
        namerExtension(entity, config.getCelerio().getConfiguration());

        if (entityConfig.hasInheritance()) {
            if (entityConfig.is(JOINED)) {
                buildEntityInvolvedWithJoinedInheritance(entityConfig, entity);
            } else if (entityConfig.is(SINGLE_TABLE)) {
                buildEntityInvolvedWithSingleTableInheritance(entityConfig, entity);
            } else if (entityConfig.is(TABLE_PER_CLASS)) {
                buildEntityInvolvedWithTablePerClassInheritance(entityConfig, entity);
            } else {
                throw new IllegalStateException("An inheritance strategy should have been found in entity (or its ancestor) " + entityConfig.getEntityName());
            }
        } else {
            buildSimpleEntity(entityConfig, entity);
        }
        return entity;
    }

    public void setupAttributes(Entity entity) {
        for (Attribute attribute : entity.getCurrentAttributes()) {
            attributeFactory.setup(attribute);
        }
    }

    // -------------------------------------------
    // Implementation details
    // -------------------------------------------

    /**
     * Build simple entity using user configuration
     */
    private void buildSimpleEntity(EntityConfig entityConfig, Entity entity) {
        Table table = entity.getTable();

        // 1st phase: process all column and look for corresponding columnConfig
        for (Column column : table.getColumns()) {
            boolean processed = processColumnUsingColumnConfigIfAny(entityConfig, entity, column);

            // no columnConfig, we just use defaults
            if (!processed) {
                entity.addAttribute(attributeFactory.build(entity, table, column));
            }
        }

        // detect invalid column and jpa secondary case
        processInvalidColumnConfigAndJpaSecondaryTable(entityConfig, entity);
    }

    private void buildEntityInvolvedWithJoinedInheritance(EntityConfig entityConfig, Entity entity) {
        Table table = entity.getTable();
        String pk = table.getPrimaryKey();

        // 1st phase: process all column and look for corresponding columnConfig
        for (Column column : table.getColumns()) {
            if (entity.hasParentEntityName() && pk.equalsIgnoreCase(column.getName())) {
                // A shared primary-key column is mapped by the parent entity.
                continue;
            }

            boolean processed = processColumnUsingColumnConfigIfAny(entityConfig, entity, column);
            // no columnConfig, we just use defaults
            if (!processed) {
                entity.addAttribute(attributeFactory.build(entity, table, column));
            }
        }

        // detect invalid column and jpa secondary case
        processInvalidColumnConfigAndJpaSecondaryTable(entityConfig, entity);
    }

    private void buildEntityInvolvedWithSingleTableInheritance(EntityConfig entityConfig, Entity entity) {
        Table table = entity.getTable();

        // 1st phase: process all column and look for corresponding columnConfig
        for (Column column : table.getColumns()) {
            processColumnUsingColumnConfigIfAny(entityConfig, entity, column);
            // if processColumnUsingColumnConfigIfAny returned false, we do not
            // do anything as the column is certainly part of another entity, but we cannot guess which one.
        }

        // detect invalid column and jpa secondary case
        processInvalidColumnConfigAndJpaSecondaryTable(entityConfig, entity);
    }

    private void buildEntityInvolvedWithTablePerClassInheritance(EntityConfig entityConfig, Entity entity) {
        Table table = entity.getTable();
        String pk = table.getPrimaryKey();

        // 1st phase: process all column and look for corresponding columnConfig
        for (Column column : table.getColumns()) {
            if (entity.hasParentEntityName() && pk.equalsIgnoreCase(column.getName())) {
                // A shared primary-key column is mapped by the parent entity.
                continue;
            }

            processColumnUsingColumnConfigIfAny(entityConfig, entity, column);
            // if processColumnUsingColumnConfigIfAny returned false, we do not
            // do anything as the column is certainly part of another entity.
        }

        // detect invalid column and jpa secondary case
        processInvalidColumnConfigAndJpaSecondaryTable(entityConfig, entity);
    }

    // -------------------------------
    // Commons
    // -------------------------------

    private Table getTableStrict(EntityConfig entityConfig) {
        Assert.isTrue(entityConfig.hasTableName(), "A tableName is expected for the entityConfig " + entityConfig.getEntityName());
        Table table = config.getMetadata().getTableBySchemaAndName(entityConfig.getSchemaName(), entityConfig.getTableName());
        Assert.notNull(table, "Could not find table named " + entityConfig.getTableName());
        return table;
    }

    private boolean processColumnUsingColumnConfigIfAny(EntityConfig entityConfig, Entity entity, Column column) {
        ColumnConfig columnConfig = entityConfig.getColumnConfigByColumnName(column.getName());
        if (columnConfig != null) {
            if (columnConfig.hasTrueIgnore()) {
                // just skip it
                return true;
            } else {
                columnConfigFactory.applyFallBacks(columnConfig, entity.getTable(), column);
                columnConfigFactory.buildEnum(entity, columnConfig, column);
                entity.addAttribute(attributeFactory.build(entity, columnConfig));
                return true;
            }
        }
        return false;
    }

    private void processInvalidColumnConfigAndJpaSecondaryTable(EntityConfig entityConfig, Entity entity) {
        for (ColumnConfig cc : entityConfig.getColumnConfigs()) {
            if (cc.hasTrueIgnore()) {
                continue;
            }

            // 2d phase: We may have some invalid columnConfig in our conf (not detected in 1st phase above),
            // for example column config that binds to unknown column.
            // We can detect them if their tableName is null. Indeed not null table name
            // means it was processed in the 1st phase above or it is a secondary table (handled in 3d phase below)
            if (cc.getTableName() == null) {
                throw new IllegalArgumentException("The entityConfig '" + entityConfig.getEntityName()
                        + "' has a columnConfig that refers to the following unknown column: '" + cc.getColumnName()
                        + "'. Please make sure that this column exists and has been reversed. Code Generation aborted.");
            }

            // skip same table (was processed in the 1st phase)
            if (!entity.getTableName().equalsIgnoreCase(cc.getTableName())) {
                addSecondaryTableAttribute(entityConfig, entity, cc);
            }
        }
    }

    private void addSecondaryTableAttribute(EntityConfig entityConfig, Entity entity, ColumnConfig cc) {
        // 3d phase: process columnConfigs that belong to another table (jpa secondary table feature)
        Table secondaryTable = entity.getConfig().getMetadata().getTableByName(cc.getTableName());
        Assert.notNull(secondaryTable, "Could not find the table " + cc.getTableName() + ". Is it ignored? Has it been reversed?"
                + "Please review the columnConfig " + cc.getColumnName() + " in entity " + entityConfig.getEntityName());

        Column secondaryColumn = secondaryTable.getColumnByName(cc.getColumnName());
        Assert.notNull(secondaryColumn, "Could not find the column " + cc.getColumnName() + ". Is it ignored? Has it been reversed?"
                + "Please review the columnConfig " + cc.getColumnName() + " in entity " + entityConfig.getEntityName());

        columnConfigFactory.applyFallBacks(cc, secondaryTable, secondaryColumn);
        columnConfigFactory.buildEnum(entity, cc, secondaryColumn);
        entity.addAttribute(attributeFactory.build(entity, cc));
    }

    /**
     * Namers declared here can be overridden easily through configuration.
     *
     * @param entity
     */
    private void namerDefault(Entity entity) {
        entity.put("searchForm", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "SearchForm"));
        entity.put("editForm", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "EditForm"));
        entity.put("graphLoader", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "GraphLoader"));
        entity.put("controller", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "Controller"));
        entity.put("excelExporter", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "ExcelExporter"));
        entity.put("lazyDataModel", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "LazyDataModel"));
        entity.put("fileUpload", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "FileUpload"));
        entity.put("fileDownload", new ClassNamer2(entity, null, WEB_DOMAIN_PACKAGE, null, "FileDownload"));
    }

    private void namerExtension(Entity entity, Configuration configuration) {
        CelerioTemplateContext celerioContext = configuration.getCelerioTemplateContext();
        if (celerioContext == null || celerioContext.getEntityContextProperties() == null || celerioContext.getEntityContextProperties().isEmpty()) {
            return;
        }

        for (EntityContextProperty ecp : celerioContext.getEntityContextProperties()) {
            entity.put(ecp.getProperty(), new ClassNamer2(entity, ecp.getRootPackage(), ecp.getSubPackage(), ecp.getPrefix(), ecp.getSuffix()));
        }
    }
}
