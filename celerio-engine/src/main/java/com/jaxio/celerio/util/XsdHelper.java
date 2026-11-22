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

package com.jaxio.celerio.util;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class XsdHelper {

    private XsdHelper() {
    }

    public static String getCelerioXsdAsString() {
        return getResourceContentAsString("classpath*:jibx/celerio.xsd");
    }

    public static String getNonamespaceXsdAsString() {
        return getResourceContentAsString("classpath*:jibx/nonamespace.xsd");
    }

    public static String getResourceContentAsString(String resourcePath) {
        PathMatchingResourcePatternResolver o = new PathMatchingResourcePatternResolver();

        try {
            Resource[] packInfosAsResource = o.getResources(resourcePath);
            if (packInfosAsResource.length == 0) {
                return null;
            }
            try (InputStream stream = packInfosAsResource[0].getInputStream()) {
                return IOUtils.toString(stream);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException("Could not load " + resourcePath, ioe);
        }
    }
}
