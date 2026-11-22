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
import com.jaxio.celerio.configuration.pack.CelerioPack;
import com.jaxio.celerio.configuration.support.CelerioPackConfigLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.jaxio.celerio.configuration.Util.LOCAL_CELERIO_PACK;

@Service
@Slf4j
public class PackLoader {
    private final Config config;

    private final CelerioPackConfigLoader celerioPackConfigLoader;

    private final ClasspathResourceUncryptedPackLoader uncryptedPackLoader;

    @Autowired
    public PackLoader(Config config,
                      CelerioPackConfigLoader celerioPackConfigLoader,
                      ClasspathResourceUncryptedPackLoader uncryptedPackLoader) {
        this.config = config;
        this.celerioPackConfigLoader = celerioPackConfigLoader;
        this.uncryptedPackLoader = uncryptedPackLoader;
    }

    public List<TemplatePack> getTemplatePacks() {
        List<TemplatePack> packs = newArrayList();
        addTemplatePacksDefinedInXML(packs);

        if (config.getCelerio().getConfiguration().getPacks().isEmpty()) {
            addPacksFoundInClassPath(packs);
        }

        return packs;
    }

    public boolean hasPack() {
        return !getTemplatePacks().isEmpty();
    }

    private void addPacksFoundInClassPath(List<TemplatePack> packs) {
        for (TemplatePack resourcePack : getAllTemplatePacksFromClasspath()) {
            if (!config.getCelerio().getConfiguration().isPackEnabled(resourcePack.getName())) {
                log.warn("The pack " + resourcePack.getName() + " has been disabled in config");
            } else {
                packs.add(resourcePack);
            }
        }
    }

    private void addTemplatePacksDefinedInXML(List<TemplatePack> packs) {
        List<Pack> packsInConfig = new ArrayList<>(config.getCelerio().getConfiguration().getPacks());
        packsInConfig.sort(Comparator.comparing(Pack::getOrder, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Pack pack : packsInConfig) {
            if (!pack.isEnable()) {
                log.warn("The pack " + pack.getName() + " has been disabled");
            } else if (!pack.hasName()) {
                log.warn("Found an invalid pack declaration: " + pack);
            } else if (pack.hasPath()) {
                addLocalPack(packs, pack);
            } else {
                addClasspathPack(packs, pack);
            }
        }
    }

    private void addLocalPack(List<TemplatePack> packs, Pack pack) {
        File packRoot = new File(config.getBaseDir() + File.separatorChar + pack.getPath());
        if (!packRoot.exists()) {
            log.warn("The packPath " + pack.getPath() + " for the pack " + pack.getName() + " does not exist!");
            return;
        }
        File celerioPackXml = new File(packRoot, LOCAL_CELERIO_PACK);
        if (!celerioPackXml.exists()) {
            log.error("Skipping pack " + pack + " because " + celerioPackXml.getAbsolutePath() + " is missing");
            return;
        }

        try {
            CelerioPack celerioPack = celerioPackConfigLoader.load(celerioPackXml);
            TemplatePackInfo packInfo = new TemplatePackInfo(celerioPack);
            packInfo.overrideProperties(pack.getProperties());
            File templates = new File(packRoot, "celerio" + File.separatorChar + celerioPack.getPackName().getValue());
            packs.add(new LocalResourcePackFile(packInfo, templates));
        } catch (IOException e) {
            log.error("Could not load the pack " + pack, e);
        }
    }

    private void addClasspathPack(List<TemplatePack> packs, Pack pack) {
        try {
            packs.add(getPackFromClassPath(pack));
        } catch (TemplatePackNotFoundException e) {
            // Maven modules can share configuration and include different packs on their classpaths.
            log.warn(e.getMessage());
        }
    }

    private List<TemplatePack> getAllTemplatePacksFromClasspath() {
        return uncryptedPackLoader.getPacks();
    }

    private TemplatePack getPackFromClassPath(Pack packFromUserConfig) {
        for (TemplatePack p : getAllTemplatePacksFromClasspath()) {
            if (p.getName().equals(packFromUserConfig.getName())) {
                p.getTemplatePackInfo().overrideProperties(packFromUserConfig.getProperties());
                return p;
            }
        }
        throw new TemplatePackNotFoundException("Could not load the template pack " + packFromUserConfig.getName());
    }
}
