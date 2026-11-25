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

package com.jaxio.celerio.maven.plugin.celerio;

import com.jaxio.celerio.model.support.jpa.TemporalMapping;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class GenerateMojoTemporalMappingTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void hibernate7MappingRequiresJakartaNamespace() throws MojoExecutionException {
        GenerateMojo mojo = new GenerateMojo();
        mojo.temporalMapping = TemporalMapping.HIBERNATE7.getValue();

        thrown.expect(MojoExecutionException.class);
        thrown.expectMessage("jakartaNamespace=true");

        mojo.resolveTemporalMapping();
    }

    @Test
    public void hibernate7MappingIsAcceptedWithJakartaNamespace() throws MojoExecutionException {
        GenerateMojo mojo = new GenerateMojo();
        mojo.temporalMapping = TemporalMapping.HIBERNATE7.getValue();
        mojo.jakartaNamespace = true;

        assertEquals(TemporalMapping.HIBERNATE7, mojo.resolveTemporalMapping());
    }

    @Test
    public void legacyMappingDoesNotRequireJakartaNamespace() throws MojoExecutionException {
        GenerateMojo mojo = new GenerateMojo();
        mojo.temporalMapping = TemporalMapping.LEGACY.getValue();

        assertEquals(TemporalMapping.LEGACY, mojo.resolveTemporalMapping());
    }

    @Test
    public void unknownMappingReportsAcceptedValues() throws MojoExecutionException {
        GenerateMojo mojo = new GenerateMojo();
        mojo.temporalMapping = "unsupported";

        thrown.expect(MojoExecutionException.class);
        thrown.expectMessage(TemporalMapping.LEGACY.getValue());
        thrown.expectMessage(TemporalMapping.HIBERNATE7.getValue());

        mojo.resolveTemporalMapping();
    }
}
