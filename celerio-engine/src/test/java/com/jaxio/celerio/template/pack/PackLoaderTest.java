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

package com.jaxio.celerio.template.pack;

import com.jaxio.celerio.Config;
import com.jaxio.celerio.configuration.Pack;
import org.junit.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class PackLoaderTest {
    @Test
    public void ordersConfiguredPacksAndKeepsUnspecifiedPacksStable() throws IOException {
        Pack implicitFirst = new Pack("implicit-first");
        Pack later = new Pack("later");
        later.setOrder(20);
        Pack earlier = new Pack("earlier");
        earlier.setOrder(10);
        Pack implicitLast = new Pack("implicit-last");

        Config config = new Config();
        config.getCelerio().getConfiguration().setPacks(List.of(implicitFirst, later, earlier, implicitLast));
        List<TemplatePack> available = List.of(pack("implicit-last"), pack("later"), pack("earlier"), pack("implicit-first"));
        ClasspathResourceUncryptedPackLoader classpath = new ClasspathResourceUncryptedPackLoader(null) {
            @Override
            public List<TemplatePack> getPacks() {
                return available;
            }
        };

        PackLoader loader = new PackLoader(config, null, classpath);

        assertThat(loader.getTemplatePacks().stream().map(TemplatePack::getName).toList())
                .containsExactly("earlier", "later", "implicit-first", "implicit-last");
    }

    private static TemplatePack pack(String name) throws IOException {
        return new ClasspathResourceUncryptedPack(new TemplatePackInfo(name), new Resource[0]);
    }
}
