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

package com.jaxio.celerio.model;

import com.google.common.base.Preconditions;
import com.jaxio.celerio.Config;
import com.jaxio.celerio.configuration.Module;
import com.jaxio.celerio.configuration.convention.CollectionType;
import com.jaxio.celerio.configuration.database.Table;
import com.jaxio.celerio.configuration.entity.EntityConfig;
import com.jaxio.celerio.configuration.entity.ImplementsInterface;
import com.jaxio.celerio.configuration.entity.Inheritance;
import com.jaxio.celerio.convention.ClassType;
import com.jaxio.celerio.factory.RelationCollisionUtil;
import com.jaxio.celerio.factory.conventions.AuditEntityConvention;
import com.jaxio.celerio.factory.conventions.AuditLogConvention;
import com.jaxio.celerio.factory.conventions.SavedSearchConvention;
import com.jaxio.celerio.model.support.*;
import com.jaxio.celerio.model.support.EntityPredicates.ExcludeEntity;
import com.jaxio.celerio.model.support.account.AccountAttributes;
import com.jaxio.celerio.model.support.account.RoleAttributes;
import com.jaxio.celerio.model.support.account.SavedSearchAttributes;
import com.jaxio.celerio.support.Namer;
import com.jaxio.celerio.util.Hierarchical;
import com.jaxio.celerio.util.Labels;
import com.jaxio.celerio.util.Named;
import com.jaxio.celerio.util.support.CurrentAndFlatListHolder;
import com.jaxio.celerio.util.support.HierarchicalSupport;
import com.jaxio.celerio.util.support.ListGetter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.persistence.InheritanceType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newTreeSet;
import static com.jaxio.celerio.configuration.database.support.SqlUtil.escapeSql;
import static com.jaxio.celerio.model.support.AttributePredicates.*;
import static com.jaxio.celerio.model.support.AttributePredicates.SIMPLE;
import static com.jaxio.celerio.model.support.EntityListGetters.*;
import static com.jaxio.celerio.model.support.EntityPredicates.HAS_FILE_ATTRIBUTES;
import static com.jaxio.celerio.model.support.RelationPredicates.*;
import static com.jaxio.celerio.model.support.SuffixPrefixPredicates.IS_BINARY_SUFFIX;
import static com.jaxio.celerio.model.support.UniquePredicates.COMPOSITE_UNIQUE;
import static com.jaxio.celerio.model.support.UniquePredicates.SIMPLE_UNIQUE;
import static com.jaxio.celerio.util.FallBackUtil.fallBack;
import static com.jaxio.celerio.util.MiscUtil.toReadableLabel;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * JPA Entity meta information.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
@Slf4j
@Getter
public class Entity implements Hierarchical<Entity>, Named, Map<String, Object> {
    private final Config config;
    private final RelationCollisionUtil collisionUtil;
    @Setter
    private boolean virtual;
    @Setter
    private boolean view;
    @Setter
    private boolean manyToManyJoinEntity;
    private Entity parent;
    private EntityConfig entityConfig;

    // account / role support for spring security
    @Setter
    private AccountAttributes accountAttributes;
    @Setter
    private RoleAttributes roleAttributes;

    // metadata
    @Setter
    @Getter
    private Table table;

    // -----------------------------------
    // Namers
    // -----------------------------------

    // Used from velocity templates
    private final Namer model = newClassNamer(ClassType.model);
    private final Namer controllerWithPathVariable = newClassNamer(ClassType.controllerWithPathVariable);
    private final Namer dao = newClassNamer(ClassType.dao);
    private final Namer converter = newClassNamer(ClassType.converter);
    private final Namer formatter = newClassNamer(ClassType.formatter);
    private final Namer formService = newClassNamer(ClassType.formService);
    private final Namer validator = newClassNamer(ClassType.validator);
    private final Namer formValidator = newClassNamer(ClassType.formValidator);
    private final Namer repository = newClassNamer(ClassType.repository);
    private final Namer repositorySupport = newClassNamer(ClassType.repositorySupport);
    // service is like repository... just a matter of taste.
    private final Namer service = newClassNamer(ClassType.service);
    private final Namer serviceImpl = newClassNamer(ClassType.serviceImpl);
    private final Namer serviceSupport = newClassNamer(ClassType.serviceSupport);
    private final Namer entityForm = newClassNamer(ClassType.entityForm);
    private final Namer context = newClassNamer(ClassType.context);
    private final Namer modelGenerator = newClassNamer(ClassType.modelGenerator);
    private final Namer searchController = newClassNamer(ClassType.searchController);
    private final Namer webSupport = newClassNamer(ClassType.webSupport);
    private final Namer webModel = newClassNamer(ClassType.webModel);
    private final Namer webModelConverter = newClassNamer(ClassType.webModelConverter);
    private final Namer webModelItems = newClassNamer(ClassType.webModelItems);
    private final Namer webPermission = newClassNamer(ClassType.webPermission);
    private final Namer seleniumEditPage = new DomainSubpackageClassNamer(this, ClassType.seleniumEditPage);
    private final Namer seleniumSearchPage = new DomainSubpackageClassNamer(this, ClassType.seleniumSearchPage);
    private final Namer printer = newClassNamer(ClassType.printer);

    @Setter
    private PrimaryKey primaryKey;

    // base attributes, the getters return unmodifiableList to make sure we go through the instance to manipulate them
    private final List<Entity> children = newArrayList();
    private final List<Attribute> currentAttributes = newArrayList();
    private final Map<String, Attribute> currentAttributesByColumnFullName = newHashMap();
    private final Map<String, Attribute> currentAttributesByName = newHashMap();

    // -----------------------------------
    // Unique
    // -----------------------------------
    private final List<Unique> currentUniques = newArrayList();
    private final EntityUniques uniques = newEntityUniques();
    private final EntityUniques simpleUniques = newEntityUniques(SIMPLE_UNIQUE::apply);
    private final EntityUniques compositeUniques = newEntityUniques(COMPOSITE_UNIQUE::apply);

    // -----------------------------------
    // Relations
    // -----------------------------------
    private final List<Relation> currentRelations = newArrayList();
    private final EntityRelations relations = newEntityRelations();
    private final EntityRelations relationsExceptNN = newEntityRelations(NOT_MANY_TO_MANY::apply);
    private final EntityRelations collectionRelations = newEntityRelations(COLLECTION::apply);
    private final EntityRelations oneToMany = newEntityRelations(ONE_TO_MANY::apply);
    private final EntityRelations oneToVirtualOne = newEntityRelations(ONE_TO_VIRTUAL_ONE::apply);
    private final EntityRelations manyToOne = newEntityRelations(MANY_TO_ONE::apply);
    private final EntityRelations xToOne = newEntityRelations(X_TO_ONE::apply);
    private final EntityRelations forwardXToOne = newEntityRelations(FORWARD_X_TO_ONE::apply);
    private final EntityRelations nonSimpleXToOne = newEntityRelations(NON_SIMPLE_X_TO_ONE::apply);
    private final EntityRelations compositeXToOne = newEntityRelations(COMPOSITE_X_TO_ONE::apply);
    private final EntityRelations unidirectionalXToOne = newEntityRelations(UNIDIRECTIONAL_X_TO_ONE::apply);
    private final EntityRelations bidirectionalXToOne = newEntityRelations(BIDIRECTIONAL_X_TO_ONE::apply);
    private final EntityRelations intermediateXToOne = newEntityRelations(INTERMEDIATE_X_TO_ONE::apply);
    private final EntityRelations oneToOne = newEntityRelations(ONE_TO_ONE::apply);
    private final EntityRelations inverseOneToOne = newEntityRelations(SIMPLE_INVERSE_ONE_TO_ONE::apply);
    private final EntityRelations manyToMany = newEntityRelations(MANY_TO_MANY::apply);
    private final EntityRelations unidirectionalManyToMany = newEntityRelations(UNIDIRECTIONAL_MANY_TO_MANY::apply);
    private final EntityRelations bidirectionalManyToMany = newEntityRelations(BIDIRECTIONAL_MANY_TO_MANY::apply);
    private final EntityRelations xToMany = newEntityRelations(X_TO_MANY::apply);
    private final EntityRelations composite = newEntityRelations(COMPOSITE::apply);
    private final EntityRelations simpleRelations = newEntityRelations(RelationPredicates.SIMPLE::apply);
    private final EntityRelations inverseRelations = newEntityRelations(RELATION_IS_INVERSE::apply);
    private final EntityRelations formInputFieldRelations = newEntityRelations(FORM_INPUT_FIELD_RELATION::apply);

    // -----------------------------------
    // Hierarchy
    // -----------------------------------
    private final HierarchicalSupport<Entity> hierarchicalSupport = new HierarchicalSupport<Entity>(this);
    private final CurrentAndFlatListHolder<Entity, Entity> hierarchy = new CurrentAndFlatListHolder<Entity, Entity>(this, HIERARCHY_ATTRIBUTES);

    // -----------------------------------
    // Attributes
    // -----------------------------------
    private final EntityAttributes attributes = newEntityAttributes();
    private final EntityAttributes allAttributes = new EntityAttributes(this, ATTRIBUTES_AND_PK_ATTRIBUTES);

    // pk
    private final EntityAttributes primaryKeyAttributes = new EntityAttributes(this, PK_ATTRIBUTES);
    private final EntityAttributes nonCpkAttributes = newEntityAttributes(NOT_IN_COMPOSITE_PK::apply);
    private final EntityAttributes inPkAttributes = newEntityAttributes(IN_PK::apply);
    private final EntityAttributes simplePkAttributes = newEntityAttributes(SIMPLE_PK::apply);
    private final EntityAttributes notInPkAttributes = newEntityAttributes(NOT_IN_PK::apply);
    private final EntityAttributes cpkDateAttributes = new EntityAttributes(this, CPK_ATTRIBUTES, DATE);
    private final EntityAttributes businessKeyByConfiguration = newEntityAttributes(BUSINESS_KEY_BY_CONFIGURATION::apply);

    @Setter
    private List<Attribute> businessKey = newArrayList(); // set by BusinessKeyFactory.

    // fk
    private final EntityAttributes inFkAttributes = newEntityAttributes(IN_FK::apply);
    private final EntityAttributes simpleFkAttributes = newEntityAttributes(SIMPLE_FK::apply);

    // misc
    private final EntityAttributes simpleAttributes = newEntityAttributes(SIMPLE::apply);
    private final EntityAttributes simpleStringAttributes = newEntityAttributes(SIMPLE::apply, STRING::apply);
    private final EntityAttributes uniqueAttributes = newEntityAttributes(IS_UNIQUE::apply, NOT_SIMPLE_PK::apply, WITH_PUBLIC_SETTER_ACCESSIBILITY::apply);
    private final EntityAttributes dateAttributes = newEntityAttributes(DATE::apply, NOT_VERSION::apply);
    private final EntityAttributes anyDateAttributes = newEntityAttributes(DATE::apply);
    private final EntityAttributes fileAttributes = newEntityAttributes(FILE::apply);
    private final EntityAttributes numericAttributes = newEntityAttributes(NUMERIC::apply);
    private final EntityAttributes localeKeyAttributes = newEntityAttributes(LOCALE_KEY::apply);
    private final EntityAttributes localizableAttributes = newEntityAttributes(LOCALIZABLE::apply);
    private final EntityAttributes enumAttributes = newEntityAttributes(ENUM::apply);
    private final EntityAttributes uniqueEnumAttributes = newEntityAttributes(ENUM::apply, IS_UNIQUE::apply);
    private final EntityAttributes pertinentDefaultValueAttributes = newEntityAttributes(HAS_PERTINENT_DEFAULT_VALUE::apply);
    private final EntityAttributes labelAttributes = newEntityAttributes(IS_LABEL::apply);
    private final EntityAttributes printerAttributes = new EntityAttributes(this, PRINTER_ATTRIBUTES);
    private final EntityAttributes stringPrinterAttributes = new EntityAttributes(this, STRING_PRINTER_ATTRIBUTES);
    private final EntityAttributes indexedPrinterAttributes = new EntityAttributes(this, INDEXED_PRINTER_ATTRIBUTES);
    private final EntityAttributes localizableDisplayStringAttributes = new EntityAttributes(this, LOCALIZABLE_TO_DISPLAY_STRING_ATTRIBUTES);
    private final EntityAttributes versionAttributes = newEntityAttributes(VERSION::apply);

    // search
    private final EntityAttributes searchAttributes = newEntityAttributes(SEARCH_FIELD::apply, AttributeOrder.SEARCH_FIELD);
    private final EntityAttributes rangeableSearchAttributes = newEntityAttributes(SEARCH_FIELD::apply, RANGEABLE_FIELD::apply, AttributeOrder.SEARCH_FIELD);
    private final EntityAttributes multiSelectableSearchAttributes = newEntityAttributes(SEARCH_FIELD::apply, MULTI_SELECTABLE_FIELD::apply, AttributeOrder.SEARCH_FIELD);
    private final EntityAttributes hibernateSearchAttributes = newEntityAttributes(HIBERNATE_SEARCH_FIELD::apply);

    private final EntityAttributes searchResultAttributesConvention = newEntityAttributes(SEARCH_RESULT_CONVENTION::apply, AttributeOrder.SEARCH_RESULT);
    private final EntityAttributes searchResultAttributesManual = newEntityAttributes(SEARCH_RESULT_FIELD_DEFINED_MANUALLY::apply, AttributeOrder.SEARCH_RESULT);
    private final EntityAttributes searchResultAttributes = new EntityAttributes(this, SEARCH_RESULTS, AttributeOrder.SEARCH_RESULT);

    private final EntityAttributes patternSearchableAttributes = newEntityAttributes(IS_PATTERN_SEARCHABLE::apply);
    private final EntityAttributes patternSearchableCpkAttributes = new EntityAttributes(this, CPK_ATTRIBUTES, IS_CPK_PATTERN_SEARCHABLE);
    private final EntityAttributes withPublicSetterAccessibilityAttributes = newEntityAttributes(WITH_PUBLIC_SETTER_ACCESSIBILITY::apply);

    // web
    private final EntityAttributes visibleAttributes = newEntityAttributes(VISIBLE::apply);
    private final EntityAttributes sortableAttributes = newEntityAttributes(SORTABLE::apply);
    private final EntityAttributes defaultSortAttributes = newEntityAttributes(DEFAULT_SORT::apply);

    private final EntityAttributes formAttributes = newEntityAttributes(FORM_FIELD::apply, AttributeOrder.FORM_FIELD);
    private final EntityAttributes visibleFormAttributes = newEntityAttributes(FORM_FIELD::apply, VISIBLE::apply, AttributeOrder.FORM_FIELD);
    private final EntityAttributes hiddenFormAttributes = newEntityAttributes(FORM_FIELD::apply, HIDDEN::apply, AttributeOrder.FORM_FIELD);

    // -----------------------------------
    // Entities
    // -----------------------------------    
    private final EntityEntity meAndRelatedEntities = newEntityEntity();
    private final EntityEntity relatedEntities = newEntityEntity(new ExcludeEntity(this)::apply);

    public void setEntityConfig(EntityConfig config) {
        Assert.isNull(this.entityConfig, "entityConfig can be set only once");
        Assert.notNull(config);
        this.entityConfig = config;
    }

    public void setParent(Entity parent) {
        Assert.isNull(this.parent, "parent can be set only once");
        this.parent = parent;
        parent.addChild(this);
    }

    public boolean hasParent() {
        return parent != null;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public String getParentsTrainAndSelf() {
        StringBuilder result = new StringBuilder();
        if (hasParent()) {
            for (Entity e : getParents()) {
                result.append(e.getName()).append(':');
            }
        }
        return result.append(getName()).toString();
    }

    public void addAttribute(Attribute attribute) {
        if (currentAttributes.isEmpty()) {
            // let's add the entity name as it is used in certain classes and can clash with field vars.
            collisionUtil.addVar(getName(), getName());
        }

        currentAttributes.add(attribute);

        // will prevent clash with relation var name...
        attribute.getColumnConfig().setFieldName(collisionUtil.getClashSafeVar(getName(), attribute.getName()));

        currentAttributesByName.put(attribute.getName().toUpperCase(), attribute);

        if (!currentAttributesByColumnFullName.containsKey(attribute.getColumnFullName().toUpperCase())) {
            currentAttributesByColumnFullName.put(attribute.getColumnFullName().toUpperCase(), attribute);
        } else {
            throw new IllegalStateException("The column " + attribute.getColumnFullName() + " seems to be duplicated! Please fix your database schema.");
        }
    }

    public void addChild(Entity child) {
        children.add(child);
    }

    public Entity() {
        this(null, null);
    }

    @Autowired
    public Entity(Config config, RelationCollisionUtil collisionUtil) {
        this.config = config;
        this.collisionUtil = collisionUtil;
    }

    public void addUnique(Unique unique) {
        currentUniques.add(unique);
    }


    public int getHierarchyLevel() {
        return hierarchicalSupport.getHierarchyLevel();
    }

    //-------------------------------
    // Hierarchical Implementation
    //-------------------------------
    @Override
    public Entity getRoot() {
        return hierarchicalSupport.getRoot();
    }

    @Override
    public boolean isRoot() {
        return hierarchicalSupport.isRoot();
    }

    @Override
    public List<Entity> getParents() {
        List<Entity> result = newArrayList();
        Entity current = this;
        while (current.hasParent()) {
            result.add(0, current.getParent());
            current = current.getParent();
        }

        return result;
    }

    @Override
    public List<Entity> getChildren() {
        return unmodifiableList(children);
    }

    // end Hierarchical Implementation

    public void addRelation(Relation relation) {
        if (!currentRelations.contains(relation)) {
            currentRelations.add(relation);
        }
    }

    public Attribute getAttributeByName(String name) {
        return currentAttributesByName.get(name.toUpperCase());
    }

    public Attribute getAttributeByTableAndColumnName(String tableName, String columnName) {
        String fullName = tableName.toUpperCase() + "." + columnName.toUpperCase();
        return currentAttributesByColumnFullName.get(fullName);
    }

    // -----------------------------------------------------
    // Inheritance Config shortcuts
    // -----------------------------------------------------

    public boolean hasInheritance() {
        return entityConfig.hasInheritance();
    }

    public Inheritance getInheritance() {
        return entityConfig.getInheritance();
    }

    public boolean hasParentEntityName() {
        return entityConfig.hasParentEntityName();
    }

    public String getParentEntityName() {
        return entityConfig.getInheritance().getParentEntityName();
    }

    // -----------------------------------------------------
    // Config shortcuts
    // -----------------------------------------------------

    public boolean isSkip() {
        return entityConfig.shouldSkip();
    }

    @Override
    public String getName() {
        return entityConfig.getEntityName();
    }

    public boolean hasTableName() {
        return isNotBlank(entityConfig.getTableName());
    }

    public String getTableName() {
        return entityConfig.getTableName();
    }

    public String getTableNameEscaped() {
        return escapeSql(getTableName());
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean hasCompositePk() {
        return primaryKey.isComposite();
    }

    public boolean hasSimplePk() {
        return primaryKey.isSimple();
    }

    public boolean hasDatePk() {
        return primaryKey.isSimple() && getPrimaryKey().getAttribute().isDate();
    }

    public boolean hasPrimaryKey() {
        return hasCompositePk() || hasSimplePk();
    }

    public List<Attribute> getCurrentAttributes() {
        return unmodifiableList(currentAttributes);
    }

    public List<Relation> getCurrentRelations() {
        return unmodifiableList(currentRelations);
    }

    public List<Unique> getCurrentUniques() {
        return unmodifiableList(currentUniques);
    }

    // -----------------------------------------------------
    // Tables
    // -----------------------------------------------------

    public Set<String> getTableNamesInvolvedInEntity() {
        Set<String> tableNames = newTreeSet();

        for (Attribute attribute : getCurrentAttributes()) {
            tableNames.add(attribute.getTableName());
        }

        return tableNames;
    }

    // -----------------------------------------------------
    // Config shortcuts
    // -----------------------------------------------------

    public boolean hasComment() {
        return getEntityConfig().hasComment();
    }

    public String getComment() {
        return getEntityConfig().getComment();
    }

    public String getCommentAsJavadoc() {
        return getEntityConfig().getCommentAsJavadoc();
    }

    public List<Attribute> printerAttributes() {
        List<Attribute> result = newArrayList();
        attributes.getList().stream().filter(attribute -> Boolean.TRUE.equals(attribute.getColumnConfig().getSelectLabel()))
                .forEach(result::add);
        if (result.isEmpty() && useBusinessKey()) {
            getBusinessKey().stream().filter(attribute -> !attribute.isPassword() && attribute.isSimple()).forEach(result::add);
        }
        if (result.size() < 2) {
            labelAttributes.getList().stream().filter(attribute -> !attribute.isPassword()).limit(3L - result.size())
                    .forEach(result::add);
        }
        if (result.isEmpty()) {
            result.addAll(defaultPrinterAttributes());
        }
        if (result.isEmpty() && hasSimplePk()) {
            result.add(getRoot().getPrimaryKey().getAttribute());
        }
        return unmodifiableList(result);
    }

    private List<Attribute> defaultPrinterAttributes() {
        List<Attribute> candidates = simpleAttributes.getList();
        List<Attribute> selection = candidates.stream().filter(attribute -> attribute.isUnique() && !attribute.isPassword()).limit(3).toList();
        if (!selection.isEmpty()) {
            return selection;
        }
        selection = candidates.stream()
                .filter(attribute -> attribute.isString() && attribute.isNotNullable() && (!attribute.isInFileDefinition() || attribute.isFilename()))
                .limit(3).toList();
        if (!selection.isEmpty()) {
            return selection;
        }
        selection = candidates.stream().filter(attribute -> (attribute.isString() || attribute.isFilename()) && !attribute.isPassword()).limit(3).toList();
        return selection.isEmpty() ? candidates.stream().filter(attribute -> !attribute.isPassword()).limit(3).toList() : selection;
    }

    public List<Attribute> stringPrinterAttributes() {
        List<Attribute> result = newArrayList();
        for (Attribute attribute : printerAttributes()) {
            if (attribute.isString()) {
                result.add(attribute);
            }
        }
        return unmodifiableList(result);
    }

    public List<Attribute> indexedPrinterAttributes() {
        List<Attribute> result = newArrayList();
        for (Attribute attribute : printerAttributes()) {
            if (attribute.isIndexed()) {
                result.add(attribute);
            }
        }
        return unmodifiableList(result);
    }

    public String getSpaceAndExtendsStatement() {
        if (isRoot() && getEntityConfig().hasExtendsClass()) {
            return " extends " + getEntityConfig().getExtendsClass().getFullType();
        }

        return "";
    }

    public String getCommaAndImplementedInterfaces() {
        StringBuilder sb = new StringBuilder("");

        if (getEntityConfig().hasImplementsInterfaces()) {
            for (ImplementsInterface inter : getEntityConfig().getImplementsInterfaces()) {
                if (inter.hasFullType()) {
                    sb.append(", ").append(inter.getFullType());
                }
            }
        }

        if (getConfig().getCelerio().getConfiguration().has(Module.COPYABLE)) {
            sb.append(", Copyable<" + getModel().getType() + ">");
        }
        return sb.toString();
    }

    // ----------------------------------------
    // Account/Role attributes
    // ----------------------------------------

    public boolean isAccount() {
        return accountAttributes != null;
    }

    public boolean isRole() {
        return roleAttributes != null;
    }

    AuditLogConvention auditLogConvention = null;

    public boolean isAuditLog() {
        if (auditLogConvention == null) {
            auditLogConvention = new AuditLogConvention(this);
        }
        return auditLogConvention.isMatch();
    }

    public AuditLogAttribute getAuditLogAttributes() {
        return auditLogConvention.getAuditLogAttribute();
    }

    SavedSearchConvention savedSearchConvention = null;

    public boolean isSavedSearch() {
        if (isManyToManyJoinEntity()) {
            return false;
        }
        if (savedSearchConvention == null) {
            savedSearchConvention = new SavedSearchConvention(this);
        }
        return savedSearchConvention.isMatch();
    }

    public SavedSearchAttributes getSavedSearchAttributes() {
        return savedSearchConvention.getSavedSearchAttributes();
    }

    AuditEntityConvention auditEntityConvention = null;

    public boolean isEntityAudited() {
        if (auditEntityConvention == null) {
            auditEntityConvention = new AuditEntityConvention(this);
        }
        return auditEntityConvention.isAudited();
    }

    public AuditEntityAttribute getAuditEntityAttributes() {
        if (auditEntityConvention == null) {
            auditEntityConvention = new AuditEntityConvention(this);
        }
        return auditEntityConvention.getAuditEntityAttribute();
    }

    // ----------------------------------------
    // Files
    // ----------------------------------------
    private Boolean hasFileAttributes;

    public boolean hasFileAttributes() {
        if (hasFileAttributes == null) {
            hasFileAttributes = HAS_FILE_ATTRIBUTES.apply(this);
        }
        return hasFileAttributes;
    }

    // ----------------------------------------
    // Identifiable
    // ----------------------------------------

    /**
     * Tells whether we should generate identifiable methods. We do not need to generate them in the case where the identifiable property matches exactly the
     * entity's PK field.
     */
    public boolean generateIdentifiableMethods() {
        Assert.isTrue(isRoot(), "generateIdentifiableMethods() can be invoked only on root entity. Please fix your template.");

        if (hasSimplePk() || hasCompositePk()) {
            String identifiableProperty = config.getCelerio().getConfiguration().getConventions().getIdentifiableProperty();
            // keep it with equalsIgnoreCase as people tends to use 'Id'
            return !getPrimaryKey().getVar().equalsIgnoreCase(identifiableProperty);
        }

        return true;
    }

    // ----------------------------------------
    // Support for localized database schema (columns example: text_fr, text_en, text_de)
    // ----------------------------------------

    List<AttributeBundle> attributeBundles;

    /**
     * we should also check on types.
     */
    public List<AttributeBundle> getAttributeBundles() {
        if (attributeBundles != null) {
            return attributeBundles;
        }

        Map<String, AttributeBundle> bundlesMap = newHashMap();

        for (Attribute attribute : getAttributes().getList()) {
            if (attribute.columnNameHasLanguageSuffix()) {
                String base = attribute.getColumnNameWithoutLanguage();
                AttributeBundle bundle = bundlesMap.get(base);
                if (bundle == null) {
                    bundle = new AttributeBundle(attribute); // add first attribute
                    bundlesMap.put(base, bundle);
                } else {
                    bundle.addAttribute(attribute);
                }
            }
        }

        // keep bundles with more than 1 attribute
        attributeBundles = newArrayList();
        for (AttributeBundle bundle : bundlesMap.values()) {
            if (bundle.getAttributes().size() > 1) {
                attributeBundles.add(bundle);
                log.info("Found columns satisfying localization pattern: " + bundle.toString());
            }
        }

        return attributeBundles;
    }

    // ----------------------------------------
    // Misc
    // ----------------------------------------

    public boolean useBusinessKey() {
        return !businessKey.isEmpty();
    }

    /**
     * In case this entity is used as a many to one target. Which attribute should be used for sorting.
     */
    public Attribute getXToOneSortAttribute() {
        // must be consistent with formatter...
        return printerAttributes.getFlatUp().getFirst();
    }

    public boolean hasDateAttribute() {
        return any(currentAttributes, DATE);
    }

    public boolean hasUniqueDateAttribute() {
        return any(currentAttributes, and(DATE, IS_UNIQUE));
    }

    public boolean hasUniqueBigIntegerAttribute() {
        return any(currentAttributes, and(BIG_INTEGER, IS_UNIQUE));
    }

    public boolean hasUniqueAttribute() {
        return any(currentAttributes, IS_BINARY_SUFFIX);
    }

    public boolean overrideDeleteInManagerImpl() {
        return !isManyToManyJoinEntity() && !isView()
                && relations.getList().stream().filter(Relation::hasInverse).map(Relation::getInverse)
                        .anyMatch(inverse -> inverse.isOneToMany() || inverse.isOneToOne());
    }

    public boolean is(InheritanceType strategy) {
        Assert.notNull(strategy);
        if (getInheritance() == null) {
            return false;
        }
        return strategy == getInheritance().getStrategy();
    }

    public boolean isTable() {
        return !isView() && !isVirtual();
    }

    public List<Entity> getMandatoryToEntitiesUpToRoot() {
        List<Entity> result = newArrayList();
        for (Relation r : getRelations().getFlatUp().getList()) {
            if (r.isMandatory() && !result.contains(r.getToEntity())) {
                result.add(r.getToEntity());
            }
        }
        return result;
    }

    /**
     * @return true when this entity can be edited directly, false when it should be edited after navigating through it from another entity.
     */
    public boolean isDrivesAllRelations() {
        for (Relation r : getRelations().getFlatUp().getList()) {
            if (!r.getFromEntity().equals(r.getToEntity())) {
                if (r.isManyToOne() && r.hasInverse()) {
                    return false;
                }
                if (r.isOneToOne() && r.hasInverse() && !r.isInverse()) {
                    return false;
                }
            }
        }

        return true;
    }

    // ----------------------------------------
    // Hibernate Search
    // ----------------------------------------

    /**
     * Whether this entity is indexed by hibernate search. When true, we can use full text search on front side.
     */
    public boolean isIndexed() {
        return getEntityConfig().hasTrueIndexed() || getEntityConfig().atLeastOneColumnConfigIsIndexed();
    }

    // -----------------------------------------------------
    // Internationalization
    // -----------------------------------------------------
    private Labels labels;

    public String getLabelName() {
        return getModel().getVar();
    }

    public Labels getLabels() {
        if (labels == null) {
            labels = new Labels(entityConfig.getLabels());
            labels.setFallBack(fallBack(entityConfig.getLabel(), toReadableLabel(entityConfig.getEntityName())));
        }
        return labels;
    }

    // -----------------------------------------------------
    // equals & hashCode
    // -----------------------------------------------------

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Entity)) {
            return false;
        }

        Entity other = (Entity) o;
        if (getName() != null && !getName().isEmpty()) {
            return getName().equals(other.getName());
        } else {
            throw new IllegalStateException("equals is called whereas 'name' is not yet set, this is dangerous");
        }
    }

    @Override
    public final int hashCode() {
        if (getName() != null && !getName().isEmpty()) {
            return getName().hashCode();
        } else {
            throw new IllegalStateException("hashCode is called whereas 'name' is not yet set, this is dangerous");
        }
    }

    // -----------------------------------------------------
    // Builders to lower the attributes declarations
    // -----------------------------------------------------

    private EntityUniques newEntityUniques() {
        return new EntityUniques(this, UNIQUES);
    }

    private EntityUniques newEntityUniques(Predicate<Unique> predicate) {
        return new EntityUniques(this, UNIQUES, predicate::test);
    }

    private EntityRelations newEntityRelations() {
        return new EntityRelations(this, RELATIONS);
    }

    private EntityRelations newEntityRelations(Predicate<Relation> predicate) {
        return new EntityRelations(this, RELATIONS, predicate::test);
    }

    private EntityAttributes newEntityAttributes() {
        return new EntityAttributes(this, ATTRIBUTES);
    }

    private EntityAttributes newEntityAttributes(Predicate<Attribute> predicate) {
        return new EntityAttributes(this, ATTRIBUTES, predicate::test);
    }

    private EntityAttributes newEntityAttributes(Predicate<Attribute> predicate, AttributeOrder attributeOrder) {
        return new EntityAttributes(this, ATTRIBUTES, predicate::test, attributeOrder);
    }

    private EntityAttributes newEntityAttributes(Predicate<Attribute> p1, Predicate<Attribute> p2) {
        return newEntityAttributes(p1.and(p2));
    }

    private EntityAttributes newEntityAttributes(Predicate<Attribute> p1, Predicate<Attribute> p2, AttributeOrder attributeOrder) {
        return newEntityAttributes(p1.and(p2), attributeOrder);
    }

    private EntityAttributes newEntityAttributes(Predicate<Attribute> p1, Predicate<Attribute> p2, Predicate<Attribute> p3) {
        return newEntityAttributes(p1.and(p2).and(p3));
    }

    private Namer newClassNamer(ClassType type) {
        return new ClassNamer(this, type);
    }

    private EntityEntity newEntityEntity() {
        return newEntityEntity(RELATED_ENTITIES);
    }

    private EntityEntity newEntityEntity(ListGetter<Entity, Entity> listGetter) {
        return new EntityEntity(this, listGetter);
    }

    private EntityEntity newEntityEntity(Predicate<Entity> predicate) {
        return new EntityEntity(this, RELATED_ENTITIES, predicate::test);
    }

    /** Returns descendants of a root entity in depth-first order. */
    public List<Entity> getAllChildrenRecursive() {
        List<Entity> result = newArrayList();
        if (isRoot()) {
            allChildrenRecursive(this, result);
        }
        return result;
    }

    private void allChildrenRecursive(Entity e, List<Entity> result) {
        for (Entity child : e.getChildren()) {
            result.add(child);
            allChildrenRecursive(child, result);
        }
    }

    public CollectionType getCollectionType() {
        return getEntityConfig().getCollectionType();
    }

    // ------------------------------------
    // SPI / Custom Namer are put in a Map so we can access
    // from velocity templates as if we had getter.
    // ------------------------------------

    private final Map<String, Object> spis = newHashMap();

    @Override
    public void clear() {
        spis.clear();
    }

    @Override
    public boolean containsKey(Object arg0) {
        return spis.containsKey(arg0);
    }

    @Override
    public boolean containsValue(Object arg0) {
        return spis.containsValue(arg0);
    }

    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return spis.entrySet();
    }

    @Override
    public Object get(Object arg0) {
        Object o = spis.get(arg0);
        Preconditions.checkNotNull(o, "No SPI having its var=" + arg0 + " was found. Tip: in your template for predicate method, use always ref.isSomething() instead of xxx.something");
        return o;
    }

    @Override
    public boolean isEmpty() {
        return spis.isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return spis.keySet();
    }

    @Override
    public Object put(String arg0, Object arg1) {
        return spis.put(arg0, arg1);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> arg0) {
        spis.putAll(arg0);
    }

    @Override
    public Object remove(Object arg0) {
        return spis.remove(arg0);
    }

    @Override
    public int size() {
        return spis.size();
    }

    @Override
    public Collection<Object> values() {
        return spis.values();
    }
}
