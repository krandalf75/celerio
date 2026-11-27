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

import com.jaxio.celerio.configuration.entity.*;
import com.jaxio.celerio.model.PackageImportAdder;
import com.jaxio.celerio.util.AttributeBuilder;
import org.springframework.util.Assert;

import javax.persistence.FetchType;
import java.util.List;

public class JpaConfigHelper {

    private JpaConfigHelper() {
    }

    // ------------------------------------------
    // OneToXConfig orphanRemoval
    // ------------------------------------------

    public static String orphanRemoval(OneToManyConfig... oneToManyConfigs) {
        return hasTrueOrphanRemoval(oneToManyConfigs) ? "orphanRemoval = true" : "";
    }

    public static boolean hasTrueOrphanRemoval(OneToManyConfig... oneToManyConfigs) {
        if (oneToManyConfigs == null) {
            return false;
        }

        for (OneToManyConfig oneToManyConfig : oneToManyConfigs) {
            if (oneToManyConfig != null && oneToManyConfig.getOrphanRemoval() != null) {
                return oneToManyConfig.getOrphanRemoval().booleanValue();
            }
        }

        return false; // false is the jpa default
    }

    public static String orphanRemoval(OneToOneConfig... oneToOneConfigs) {
        return hasTrueOrphanRemoval(oneToOneConfigs) ? "orphanRemoval = true" : "";
    }

    public static boolean hasTrueOrphanRemoval(OneToOneConfig... oneToOneConfigs) {
        if (oneToOneConfigs == null) {
            return false;
        }

        for (OneToOneConfig oneToOneConfig : oneToOneConfigs) {
            if (oneToOneConfig != null && oneToOneConfig.getOrphanRemoval() != null) {
                return oneToOneConfig.getOrphanRemoval().booleanValue();
            }
        }

        return false; // let jpa default applies
    }

    // ------------------------------------------
    // CASCADE
    // ------------------------------------------

    public static String jpaCascade(PackageImportAdder importAdder, CascadeGetter... cascadeGetters) {
        if (cascadeGetters == null) {
            return "";
        }

        for (CascadeGetter getter : cascadeGetters) {
            if (getter == null) {
                continue;
            }
            List<Cascade> configuration = getter.getCascades();
            if (configuration != null && !configuration.isEmpty()) {
                // An explicit NONE stops the fallback to less specific configurations.
                return formatJpaCascade(importAdder, configuration);
            }
        }
        return "";
    }

    private static String formatJpaCascade(PackageImportAdder importAdder, List<Cascade> configuration) {
        String[] names = configuration.stream()
                .map(Cascade::getType)
                .filter(CascadeType::isJpaType)
                .map(type -> type.asJpaType().name())
                .toArray(String[]::new);
        if (names.length == 0) {
            return "";
        }

        for (String name : names) {
            importAdder.addImport("static javax.persistence.CascadeType." + name);
        }
        AttributeBuilder attributes = new AttributeBuilder();
        attributes.add("cascade", names);
        return attributes.getAttributes();
    }

    // ------------------------------------------
    // FETCH
    // ------------------------------------------

    public static String jpaFetch(PackageImportAdder adder, FetchTypeGetter... fetchTypeGetters) {
        Assert.notNull(adder);

        if (fetchTypeGetters == null) {
            return "";
        }

        for (FetchTypeGetter getter : fetchTypeGetters) {
            if (getter == null || getter.getFetch() == null) {
                continue;
            }
            if (!getter.getFetch().isJpaType()) {
                return ""; // An explicit NONE stops the fallback.
            }
            FetchType fetchType = getter.getFetch().asJpaType();
            adder.addImport("static javax.persistence.FetchType." + fetchType.name());
            return "fetch = " + fetchType.name();
        }

        return "";
    }

    // ------------------------------------------
    // ORDER BY
    // ------------------------------------------

    public static String orderByAnnotation(PackageImportAdder adder, OrderByGetter... orderByGetters) {
        Assert.notNull(adder);

        if (orderByGetters == null) {
            return null;
        }

        for (OrderByGetter getter : orderByGetters) {
            if (getter != null && getter.getOrderBy() != null && !getter.getOrderBy().isEmpty()) {
                adder.addImport("javax.persistence.OrderBy");
                AttributeBuilder attributes = new AttributeBuilder();
                attributes.addString(getter.getOrderBy());
                return attributes.bindAttributesTo("@OrderBy");
            }
        }

        return null;
    }

    // ------------------------------------------
    // CacheConfig
    // ------------------------------------------

    public static String getCacheAnnotation(PackageImportAdder packageImportAdder, CacheConfigGetter... cacheConfigGetters) {
        if (cacheConfigGetters == null) {
            return "";
        }

        for (CacheConfigGetter getter : cacheConfigGetters) {
            if (getter != null && getter.getCacheConfig() != null) {
                return formatCacheAnnotation(packageImportAdder, getter.getCacheConfig());
            }
        }
        return "";
    }

    private static String formatCacheAnnotation(PackageImportAdder importAdder, CacheConfig cache) {
        if (cache.getUsage().isNone()) {
            return "";
        }
        String usage = cache.getUsage().name();
        importAdder.addImport("org.hibernate.annotations.Cache");
        importAdder.addImport("static org.hibernate.annotations.CacheConcurrencyStrategy." + usage);

        AttributeBuilder attributes = new AttributeBuilder();
        attributes.add("usage = " + usage);
        if (cache.hasInclude()) {
            attributes.addString("include", cache.getInclude());
        }
        if (cache.hasRegion()) {
            attributes.addString("region", cache.getRegion());
        }
        return attributes.bindAttributesTo("@Cache");
    }
}
