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

package com.jaxio.celerio.template;

import com.jaxio.celerio.model.support.jpa.TemporalMapping;

import java.util.Objects;

/**
 * Holds the temporal mapping strategy during a source generation run.
 */
public final class TemporalMappingContext {
    private static final InheritableThreadLocal<TemporalMapping> CURRENT_MAPPING =
            new InheritableThreadLocal<TemporalMapping>() {
                @Override
                protected TemporalMapping initialValue() {
                    return TemporalMapping.LEGACY;
                }
            };

    private TemporalMappingContext() {
    }

    /**
     * Returns the strategy bound to the current generation thread.
     *
     * @return the current strategy, or {@link TemporalMapping#LEGACY} by default
     */
    public static TemporalMapping getTemporalMapping() {
        return CURRENT_MAPPING.get();
    }

    /**
     * Binds a strategy to the current generation thread.
     *
     * @param temporalMapping the strategy to bind
     */
    public static void setTemporalMapping(TemporalMapping temporalMapping) {
        CURRENT_MAPPING.set(Objects.requireNonNull(temporalMapping, "temporalMapping"));
    }

    /**
     * Removes the strategy bound to the current generation thread.
     */
    public static void clear() {
        CURRENT_MAPPING.remove();
    }
}
