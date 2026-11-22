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

import com.jaxio.celerio.aspects.ForbiddenWhenBuildingAspect;
import com.jaxio.celerio.model.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class EntityModelAssembler {
    private final EntityFactory entityFactory;
    private final UniqueFactory uniqueFactory;
    private final PrimaryKeyFactory primaryKeyFactory;
    private final ForeignKeyHintsFactory foreignKeyHintsFactory;
    private final ForbiddenWhenBuildingAspect forbiddenWhenBuildingAspect;
    private final RelationFactory relationFactory;
    private final BusinessKeyFactory businessKeyFactory;

    @Autowired
    EntityModelAssembler(EntityFactory entityFactory, UniqueFactory uniqueFactory, PrimaryKeyFactory primaryKeyFactory,
                         ForeignKeyHintsFactory foreignKeyHintsFactory, ForbiddenWhenBuildingAspect forbiddenWhenBuildingAspect,
                         RelationFactory relationFactory, BusinessKeyFactory businessKeyFactory) {
        this.entityFactory = entityFactory;
        this.uniqueFactory = uniqueFactory;
        this.primaryKeyFactory = primaryKeyFactory;
        this.foreignKeyHintsFactory = foreignKeyHintsFactory;
        this.forbiddenWhenBuildingAspect = forbiddenWhenBuildingAspect;
        this.relationFactory = relationFactory;
        this.businessKeyFactory = businessKeyFactory;
    }

    void assemble(Project project) {
        log.info("Processing uniques/pk/fk...");
        project.getCurrentEntities().forEach(entityFactory::setupAttributes);
        project.getCurrentEntities().forEach(uniqueFactory::setupUniques);
        project.getCurrentEntities().forEach(primaryKeyFactory::setupPrimaryKey);
        // Foreign key hints need the primary keys to be available for every entity.
        project.getCurrentEntities().forEach(foreignKeyHintsFactory::setupForeignKeyHints);
        forbiddenWhenBuildingAspect.buildingDone();

        log.info("Processing relations...");
        project.getCurrentEntities().forEach(relationFactory::setupRelations);

        log.info("Processing business keys...");
        project.getCurrentEntities().forEach(businessKeyFactory::setupBusinessKey);
    }
}
