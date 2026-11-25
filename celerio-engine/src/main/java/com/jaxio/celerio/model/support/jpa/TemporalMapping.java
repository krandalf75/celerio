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

package com.jaxio.celerio.model.support.jpa;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Selects the temporal annotation strategy used for generated entities.
 */
public enum TemporalMapping {
    LEGACY("legacy"),
    HIBERNATE7("hibernate7");

    private final String value;

    TemporalMapping(String value) {
        this.value = value;
    }

    /**
     * Returns the Maven configuration value for this strategy.
     *
     * @return the configuration value
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a Maven configuration value to a temporal mapping strategy.
     *
     * @param value the configured value
     * @return the matching strategy
     * @throws IllegalArgumentException if the value is not supported
     */
    public static TemporalMapping fromValue(String value) {
        for (TemporalMapping mapping : values()) {
            if (mapping.value.equals(value)) {
                return mapping;
            }
        }

        String acceptedValues = Arrays.stream(values())
                .map(TemporalMapping::getValue)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown temporalMapping '" + value
                + "'. Accepted values: " + acceptedValues + ".");
    }
}
