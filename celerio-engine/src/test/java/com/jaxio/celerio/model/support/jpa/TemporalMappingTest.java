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

import com.jaxio.celerio.template.TemporalMappingContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.fest.assertions.Assertions.assertThat;

public class TemporalMappingTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void fromValueAcceptsSupportedMappings() {
        assertThat(TemporalMapping.fromValue(TemporalMapping.LEGACY.getValue())).isEqualTo(TemporalMapping.LEGACY);
        assertThat(TemporalMapping.fromValue(TemporalMapping.HIBERNATE7.getValue())).isEqualTo(TemporalMapping.HIBERNATE7);
    }

    @Test
    public void fromValueRejectsUnknownMappingWithAcceptedValues() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(TemporalMapping.LEGACY.getValue());
        thrown.expectMessage(TemporalMapping.HIBERNATE7.getValue());

        TemporalMapping.fromValue("providerDefault");
    }

    @Test
    public void contextReturnsToLegacyAfterClear() {
        TemporalMappingContext.setTemporalMapping(TemporalMapping.HIBERNATE7);

        TemporalMappingContext.clear();

        assertThat(TemporalMappingContext.getTemporalMapping()).isEqualTo(TemporalMapping.LEGACY);
    }
}
