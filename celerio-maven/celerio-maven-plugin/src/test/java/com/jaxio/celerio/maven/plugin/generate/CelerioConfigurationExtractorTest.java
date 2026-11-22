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

package com.jaxio.celerio.maven.plugin.generate;

import com.jaxio.celerio.maven.plugin.celerio.CelerioConfigurationExtractor;
import org.junit.Assert;
import org.junit.Test;

public class CelerioConfigurationExtractorTest {

    @Test
    public void normalCelerioConfig() {
        String xml = """

                <configuration><celerio:celerio>
                \t\t<celerio:configuration>
                \t\t\t<celerio:conventions>
                \t\t\t\t<celerio:convention name="formService">
                \t\t\t\tglouglou
                \t\t\t\t</celerio:convention>
                \t\t\t</celerio:conventions>
                \t\t</celerio:configuration>
                \t</celerio:celerio>
                </configuration>\
                """;
        CelerioConfigurationExtractor extractor = new CelerioConfigurationExtractor(xml);
        String expected = """

                \t\t<configuration>
                \t\t\t<conventions>
                \t\t\t\t<convention name="formService">
                \t\t\t\tglouglou
                \t\t\t\t</convention>
                \t\t\t</conventions>
                \t\t</configuration>
                \t\
                """;
        Assert.assertEquals(CelerioConfigurationExtractor.CELERIO_XML_START + expected + CelerioConfigurationExtractor.CELERIO_XML_END,
                extractor.getCelerioConfig());
    }

    @Test
    public void noCelerioConfig() {
        String xml = "\n<configuration><jdbcUser>me</jdbcUser></configuration>";
        CelerioConfigurationExtractor extractor = new CelerioConfigurationExtractor(xml);
        Assert.assertNull(extractor.getCelerioConfig());
    }

    @Test
    public void mixedCelerioConfig() {
        String xml = """

                <configuration><jdbcUser>me</jdbcUser><celerio:celerio>
                \t\t<celerio:configuration>
                \t\t\t<celerio:conventions>
                \t\t\t\t<celerio:convention name="formService">
                \t\t\t\tglouglou
                \t\t\t\t</celerio:convention>
                \t\t\t</celerio:conventions>
                \t\t</celerio:configuration>
                \t</celerio:celerio></configuration>\
                """;
        CelerioConfigurationExtractor extractor = new CelerioConfigurationExtractor(xml);
        String expected = """

                \t\t<configuration>
                \t\t\t<conventions>
                \t\t\t\t<convention name="formService">
                \t\t\t\tglouglou
                \t\t\t\t</convention>
                \t\t\t</conventions>
                \t\t</configuration>
                \t\
                """;
        Assert.assertEquals(CelerioConfigurationExtractor.CELERIO_XML_START + expected + CelerioConfigurationExtractor.CELERIO_XML_END,
                extractor.getCelerioConfig());
    }
}
