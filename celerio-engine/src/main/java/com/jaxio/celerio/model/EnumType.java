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

import com.jaxio.celerio.configuration.entity.EnumConfig;
import com.jaxio.celerio.configuration.entity.EnumValue;
import com.jaxio.celerio.convention.ClassType;
import com.jaxio.celerio.model.support.EnumNamer;
import com.jaxio.celerio.util.Labels;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * EnumType is for an enum what {@link Entity} is for an entity.
 */
@Slf4j
public class EnumType implements Comparable<EnumType> {

    private final EnumConfig enumConfig;
    private EnumNamer enumModel;
    private EnumNamer enumItems;
    private EnumNamer enumConverter;
    private EnumNamer enumController;

    public EnumType(EnumConfig enumConfig) {
        this.enumConfig = enumConfig;
    }

    public EnumConfig getConfig() {
        return enumConfig;
    }

    public List<EnumValue> getEnumValues() {
        return enumConfig.getEnumValues();
    }

    public EnumNamer getModel() {
        if (enumModel == null) {
            enumModel = new EnumNamer(enumConfig, ClassType.enumModel);
        }
        return enumModel;
    }

    public EnumNamer getItems() {
        if (enumItems == null) {
            enumItems = new EnumNamer(enumConfig, ClassType.enumItems);
        }
        return enumItems;
    }

    /**
     * @deprecated Use {@link EnumValue#labels()} in new templates.
     */
    @Deprecated(forRemoval = false)
    @SuppressWarnings("java:S1133") // Existing template packs still call this method.
    public Labels getLabelsByEnumValue(EnumValue ev) {
        log.warn("getLabelsByEnumValue is deprecated. Use EnumValue.labels() instead.");
        return ev.labels();
    }

    public EnumNamer getConverter() {
        if (enumConverter == null) {
            enumConverter = new EnumNamer(enumConfig, ClassType.webModelConverter);
        }
        return enumConverter;
    }

    public EnumNamer getController() {
        if (enumController == null) {
            enumController = new EnumNamer(enumConfig, ClassType.webController);
        }
        return enumController;
    }

    @Override
    public int compareTo(EnumType other) {
        return getModel().getFullType().compareTo(other.getModel().getFullType());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EnumType enumType && getModel().getFullType().equals(enumType.getModel().getFullType());
    }

    @Override
    public int hashCode() {
        return getModel().getFullType().hashCode();
    }
}
