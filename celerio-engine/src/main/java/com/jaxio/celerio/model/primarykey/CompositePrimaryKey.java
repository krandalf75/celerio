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

package com.jaxio.celerio.model.primarykey;

import com.jaxio.celerio.convention.ClassType;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.PrimaryKey;
import com.jaxio.celerio.model.support.ClassNamer;
import lombok.Getter;
import org.springframework.util.Assert;

import java.util.List;

import static java.util.Collections.unmodifiableList;

@Getter
public class CompositePrimaryKey extends ClassNamer implements PrimaryKey {
    private final Entity entity;
    private final List<Attribute> attributes;

    public CompositePrimaryKey(Entity entity, List<Attribute> attributes) {
        Assert.notNull(entity);
        Assert.isTrue(attributes.size() > 1, "Composite PK must have at least 2 attributes");
        super(entity, ClassType.primaryKey);
        for (Attribute attribute : attributes) {
            attribute.setInCpk(true);
        }
        this.attributes = unmodifiableList(attributes);
        this.entity = entity;
    }

    @Override
    public final String getVar() {
        return entity.getConfig().getCelerio().getConfiguration().getConventions().getIdentifiableProperty();
    }

    @Override
    public final boolean isNoPk() {
        return false;
    }

    @Override
    public final boolean isSimple() {
        return false;
    }

    @Override
    public final boolean isComposite() {
        return true;
    }

    @Override
    public final boolean isJavaBaseClass() {
        return false;
    }

    @Override
    public final boolean isImported() {
        return attributes.stream().anyMatch(Attribute::isInFk);
    }

    @Override
    public final boolean isDate() {
        return false;
    }

    @Override
    public final boolean isEnum() {
        return false;
    }

    @Override
    public Attribute getAttribute() {
        throw new IllegalStateException("You cannot invoke this method on a composite pk");
    }

    @Override
    public String getToStringMethod() {
        return ".toString()";
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder().append('[').append(entity.getName()).append(']');
        for (Attribute attribute : attributes) {
            result.append(attribute.getName()).append(',');
        }
        return result.toString();
    }
}
