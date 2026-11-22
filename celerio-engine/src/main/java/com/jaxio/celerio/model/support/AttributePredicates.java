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

package com.jaxio.celerio.model.support;

import com.google.common.base.Predicate;
import com.jaxio.celerio.model.Attribute;

import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.or;

@SuppressWarnings("java:S4738") // Template packs use these Guava predicate types.
public class AttributePredicates {

    private AttributePredicates() {
    }

    public static Predicate<Attribute> LOCALE_KEY = Attribute::isLocaleKey;

    public static Predicate<Attribute> LOCALIZABLE = Attribute::isLocalizable;

    public static Predicate<Attribute> FILE = Attribute::isFile;

    public static Predicate<Attribute> SIMPLE = Attribute::isSimple;

    public static Predicate<Attribute> VISIBLE = Attribute::isVisible;

    public static Predicate<Attribute> HIDDEN = not(VISIBLE);

    public static Predicate<Attribute> SORTABLE = Attribute::isSortable;

    public static Predicate<Attribute> FORM_FIELD = Attribute::isFormField;

    public static Predicate<Attribute> SEARCH_FIELD = Attribute::isSearchField;

    public static Predicate<Attribute> RANGEABLE_FIELD = Attribute::isRangeable;

    /**
     * Fields that can be used with the PropertySelector.
     */
    public static Predicate<Attribute> MULTI_SELECTABLE_FIELD = Attribute::isMultiSelectable;

    public static Predicate<Attribute> SEARCH_RESULT_CONVENTION = Attribute::isSearchResultFieldConvention;

    public static Predicate<Attribute> SEARCH_RESULT_FIELD_DEFINED_MANUALLY = attribute -> {
        if (attribute.getColumnConfig().getSearchResult() != null) {
            return attribute.getColumnConfig().getSearchResult();
        }
        return false;
    };

    public static Predicate<Attribute> HIBERNATE_SEARCH_FIELD = attribute -> attribute.getColumnConfig().getIndexedField() != null;

    public static Predicate<Attribute> SIMPLE_PK = Attribute::isSimplePk;

    public static Predicate<Attribute> IS_UNIQUE = Attribute::isUnique;

    public static Predicate<Attribute> IN_COMPOSITE_PK = Attribute::isInCpk;

    public static Predicate<Attribute> IN_PK = or(SIMPLE_PK, IN_COMPOSITE_PK);

    public static Predicate<Attribute> NOT_IN_PK = not(IN_PK);

    public static Predicate<Attribute> NOT_IN_COMPOSITE_PK = not(IN_COMPOSITE_PK);

    public static Predicate<Attribute> NOT_SIMPLE_PK = not(SIMPLE_PK);

    public static Predicate<Attribute> SIMPLE_FK = Attribute::isSimpleFk;

    public static Predicate<Attribute> IN_COMPOSITE_FK = Attribute::isInCompositeFk;

    public static Predicate<Attribute> IN_FK = or(SIMPLE_FK, IN_COMPOSITE_FK);

    public static Predicate<Attribute> NOT_IN_FK = not(IN_FK);

    public static Predicate<Attribute> BUSINESS_KEY_BY_CONFIGURATION = attribute -> attribute.getColumnConfig().hasTrueBusinessKey();

    public static Predicate<Attribute> ENUM = Attribute::isEnum;

    public static Predicate<Attribute> HAS_PERTINENT_DEFAULT_VALUE = Attribute::hasPertinentDefaultValue;

    public static Predicate<Attribute> NUMERIC = Attribute::isNumeric;

    public static Predicate<Attribute> DATE = Attribute::isDate;

    public static Predicate<Attribute> DEFAULT_SORT = input -> input.isSimple() && input.isInBk();

    public static Predicate<Attribute> BIG_INTEGER = Attribute::isBigInteger;

    public static Predicate<Attribute> VERSION = Attribute::isVersion;

    public static Predicate<Attribute> NOT_VERSION = not(VERSION);

    public static Predicate<Attribute> STRING = Attribute::isString;

    public static Predicate<Attribute> BOOLEAN = Attribute::isBoolean;

    public static Predicate<Attribute> BLOB = Attribute::isBlob;

    public static Predicate<Attribute> IS_PATTERN_SEARCHABLE = Attribute::isPatternSearchable;

    public static Predicate<Attribute> IS_CPK_PATTERN_SEARCHABLE = Attribute::isCpkPatternSearchable;


    public static Predicate<Attribute> WITH_PUBLIC_SETTER_ACCESSIBILITY = Attribute::isSetterAccessibilityPublic;

    public static Predicate<Attribute> IS_DATE = Attribute::isDate;

    public static Predicate<Attribute> IS_STRING = Attribute::isString;

    public static Predicate<Attribute> IS_LABEL = Attribute::isLabel;
}
