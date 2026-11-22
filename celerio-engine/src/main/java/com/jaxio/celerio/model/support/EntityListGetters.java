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

import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.Relation;
import com.jaxio.celerio.model.Unique;
import com.jaxio.celerio.util.support.ListGetter;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;

public class EntityListGetters {

    private EntityListGetters() {
    }

    public static ListGetter<Attribute, Entity> LOCALIZABLE_TO_DISPLAY_STRING_ATTRIBUTES = o -> {
        List<Attribute> result = newArrayList();
        for (Attribute a : o.printerAttributes()) {
            if (a.isLocalizable()) {
                result.add(a);
            }
        }
        return result;
    };

    public static ListGetter<Attribute, Entity> PRINTER_ATTRIBUTES = Entity::printerAttributes;

    public static ListGetter<Attribute, Entity> STRING_PRINTER_ATTRIBUTES = Entity::stringPrinterAttributes;

    public static ListGetter<Attribute, Entity> INDEXED_PRINTER_ATTRIBUTES = Entity::indexedPrinterAttributes;

    public static ListGetter<Attribute, Entity> ATTRIBUTES = Entity::getCurrentAttributes;

    public static ListGetter<Attribute, Entity> PK_ATTRIBUTES = o -> o.getPrimaryKey().getAttributes();

    public static ListGetter<Attribute, Entity> CPK_ATTRIBUTES = o -> {
        if (o.hasCompositePk()) {
            return o.getPrimaryKey().getAttributes();
        } else {
            return emptyList();
        }
    };

    public static ListGetter<Attribute, Entity> ATTRIBUTES_AND_PK_ATTRIBUTES = o -> {
        List<Attribute> result = newArrayList();
        result.addAll(o.getCurrentAttributes());
        if (!o.isRoot()) {
            result.addAll(o.getRoot().getPrimaryKey().getAttributes());
        }
        return result;
    };

    public static ListGetter<Entity, Entity> HIERARCHY_ATTRIBUTES = o -> newArrayList(o.getParent());

    public static ListGetter<Entity, Entity> RELATED_ENTITIES = entity -> {
        ArrayList<Entity> ret = newArrayList(entity);
        for (Relation relation : entity.getRelations().getList()) {
            ret.add(relation.getToEntity());
        }
        return ret;
    };

    public static ListGetter<Relation, Entity> RELATIONS = Entity::getCurrentRelations;

    public static ListGetter<Unique, Entity> UNIQUES = Entity::getCurrentUniques;

    public static ListGetter<Attribute, Entity> SEARCH_RESULTS = entity -> {
        ArrayList<Attribute> ret = newArrayList();
        ret.addAll(entity.getSearchResultAttributesManual().getList());
        if (ret.isEmpty()) {
            int left = 6;
            for (Attribute attr : entity.getSearchResultAttributesConvention().getList()) {
                if (left > 0) {
                    left--;
                    ret.add(attr);
                }
            }
        }
        return ret;
    };
}
