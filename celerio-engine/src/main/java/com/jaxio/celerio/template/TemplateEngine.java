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

import com.jaxio.celerio.Brand;
import com.jaxio.celerio.Config;
import com.jaxio.celerio.configuration.Module;
import com.jaxio.celerio.configuration.Pack;
import com.jaxio.celerio.configuration.Pattern;
import com.jaxio.celerio.convention.ClassType;
import com.jaxio.celerio.convention.GeneratedPackage;
import com.jaxio.celerio.convention.MethodConvention;
import com.jaxio.celerio.convention.WellKnownFolder;
import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.EnumType;
import com.jaxio.celerio.model.Project;
import com.jaxio.celerio.template.pack.PackLoader;
import com.jaxio.celerio.template.pack.Template;
import com.jaxio.celerio.template.pack.TemplatePack;
import com.jaxio.celerio.util.BrandUtil;
import com.jaxio.celerio.util.IdentifiableProperty;
import com.jaxio.celerio.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.reverse;
import static com.google.common.collect.Maps.newHashMap;

@Service
@Slf4j
public class TemplateEngine {

    private static final String ENTITY_CONTEXT_KEY = "entity";
    private static final String DASH_LINE = "-----------------------------------------------------------------------";

    private final ApplicationContext applicationContext;

    private final Config config;

    private final ContentWriter contentWriter;

    private final PackLoader packLoader;

    @Autowired
    public TemplateEngine(ApplicationContext applicationContext,
                          Config config,
                          ContentWriter contentWriter,
                          PackLoader packLoader) {
        this.applicationContext = applicationContext;
        this.config = config;
        this.contentWriter = contentWriter;
        this.packLoader = packLoader;
    }

    private Map<String, Object> getGlobalContext() {
        Map<String, Object> context = newHashMap();
        // static values
        context.put("dollar", "$");
        context.put("d", "$");
        context.put("pound", "#");
        context.put("p", "#");
        context.put("serialVersionUID", "    static final private long serialVersionUID = " + 1 + "L;");
        // global context values
        context.put("brand", new Brand());
        context.put("brandUtil", new BrandUtil(config.getOutputResult()));
        context.put("config", config);
        context.put("configuration", config.getCelerio().getConfiguration());
        context.put("generation", config.getCelerio().getConfiguration().getGeneration());
        context.put("databaseInfo", config.getMetadata().getDatabaseInfo());
        context.put("jdbcConnectivity", config.getMetadata().getJdbcConnectivity());
        context.put("project", config.getProject());
        context.put("metadata", config.getOriginalMetadata());
        context.put("StringUtil", StringUtil.class);
        // enums
        ContextUtil.addEnumValues(context, ClassType.values());
        ContextUtil.addEnumValues(context, WellKnownFolder.values());
        ContextUtil.addEnumValues(context, GeneratedPackage.values());
        ContextUtil.addEnumValues(context, Module.values());
        ContextUtil.addEnumValues(context, MethodConvention.values());

        // identifiable
        context.put("identifiableProperty", new IdentifiableProperty(config.getCelerio().getConfiguration().getConventions().getIdentifiableProperty()));

        return context;
    }

    private String s(Collection<?> s) {
        return s.size() <= 1 ? "" : "s";
    }

    public void produce(Project project, boolean bootstrapOnly) throws Exception {

        List<TemplatePack> templatePacks = packLoader.getTemplatePacks();
        logTemplateConfiguration(templatePacks);

        config.getOutputResult().open();

        // producing bootstrap first
        produceBootstrapTemplates(templatePacks);

        if (bootstrapOnly) {
            return;
        }

        for (TemplatePack templatePack : templatePacks) {
            log.info(DASH_LINE);
            log.info("Template Pack " + templatePack.getName());

            produceGlobalTemplates(templatePack, TemplateType.project);
            produceEntities(project, templatePack);
            produceCompositePk(project, templatePack);
            produceAttributes(project, templatePack);
            produceEnums(project, templatePack);
            produceStaticTemplates(templatePack);
            produceCelerioExampleTemplates(templatePack);
        }

        config.getOutputResult().close();
    }

    private void logTemplateConfiguration(List<TemplatePack> templatePacks) {
        if (config.getCelerio().getConfiguration().getModules().isEmpty()) {
            log.info("No active module");
        } else {
            log.info("Active modules");
            for (Module module : config.getCelerio().getConfiguration().getModules()) {
                log.info(" . " + module);
            }
        }

        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(DASH_LINE);
        log.info("Working with " + templatePacks.size() + " templates pack" + s(templatePacks) + ".");
        for (TemplatePack templatePack : templatePacks) {
            var names = templatePack.getTemplateNames();
            log.info(". " + templatePack.getName() + " (" + names.size() + " resource" + s(names) + ")");
            logPackConfiguration(config.getCelerio().getConfiguration().getPackByName(templatePack.getName()));
        }
    }

    private void logPackConfiguration(Pack pack) {
        if (pack == null) {
            return;
        }
        log.info("        Configuration found");
        for (Pattern pattern : pack.getTemplates()) {
            log.info("        - include: " + pattern.isInclude() + ", template pattern: " + pattern.getPattern());
        }
        for (Pattern pattern : pack.getFilenames()) {
            log.info("        - include: " + pattern.isInclude() + ", filename pattern: " + pattern.getPattern());
        }
    }

    private void produceBootstrapTemplates(List<TemplatePack> templatePacks) throws Exception {
        for (TemplatePack templatePack : reverse(templatePacks)) {
            if (!getTemplateNames(templatePack, TemplateType.bootstrap).isEmpty()) {
                log.info(DASH_LINE);
                log.info("Template Pack (bootstrap) " + templatePack.getName());
                produceGlobalTemplates(templatePack, TemplateType.bootstrap);
                return;
            }
        }
    }

    private void produceStaticTemplates(TemplatePack templatePack) throws Exception {
        for (String staticTemplateName : getStaticTemplateNames(templatePack)) {
            Template template = templatePack.getTemplateByName(staticTemplateName);

            // filter
            if (!config.getCelerio().getConfiguration().hasFilename(templatePack.getName(), template.getName())) {
                if (log.isDebugEnabled()) {
                    log.debug("SKIPPING:" + template.getName());
                }
                continue;
            }

            contentWriter.processFile(config.getOutputResult(), templatePack, template, template.getBytes(), template.getName());
        }
    }

    private void produceCelerioExampleTemplates(TemplatePack templatePack) throws Exception {
        for (String staticTemplateName : getCelerioExampleTemplateNames(templatePack)) {
            Template template = templatePack.getTemplateByName(staticTemplateName);

            String physicalTargetFilename = template.getName().replace("celerio-example", "celerio");
            // filter
            if (!config.getCelerio().getConfiguration().hasFilename(templatePack.getName(), physicalTargetFilename)) {
                log.debug("SKIPPING:" + physicalTargetFilename);
                continue;
            }

            contentWriter.processFile(config.getOutputResult(), templatePack, template, template.getBytes(), physicalTargetFilename);
        }
    }

    private void produceGlobalTemplates(TemplatePack templatePack, TemplateType type) throws Exception {
        for (String templateName : getTemplateNames(templatePack, type)) {
            produce(getGlobalContext(), templatePack, templateName);
        }
    }

    private void produceEntities(Project project, TemplatePack templatePack) throws Exception {
        for (String templateName : getTemplateNames(templatePack, TemplateType.entity)) {
            for (Entity entity : project.getCurrentEntities()) {
                if (entity.isManyToManyJoinEntity() || entity.isSkip()) {
                    continue;
                }
                Map<String, Object> context = getGlobalContext();
                context.put(ENTITY_CONTEXT_KEY, entity);
                context.put("primaryKey", entity.getPrimaryKey());
                produce(context, templatePack, templateName);
            }
        }
    }

    private void produceCompositePk(Project project, TemplatePack templatePack) throws Exception {
        for (String templateName : getTemplateNames(templatePack, TemplateType.compositePrimaryKey)) {
            for (Entity entity : project.getRootEntities().getList()) {
                if (entity.hasCompositePk() && !entity.isManyToManyJoinEntity() && !entity.isSkip()) {
                    Map<String, Object> context = getGlobalContext();
                    context.put(ENTITY_CONTEXT_KEY, entity);
                    context.put("primaryKey", entity.getPrimaryKey());
                    produce(context, templatePack, templateName);
                }
            }
        }
    }

    private void produceEnums(Project project, TemplatePack templatePack) throws Exception {
        for (String templateName : getTemplateNames(templatePack, TemplateType.enumeration)) {
            for (EnumType enumType : project.getEnumTypes()) {
                Map<String, Object> context = getGlobalContext();
                context.put("enum", enumType);
                produce(context, templatePack, templateName);
            }
        }
    }

    private void produceAttributes(Project project, TemplatePack templatePack) throws Exception {
        for (String templateName : getTemplateNames(templatePack, TemplateType.attribute)) {
            for (Entity entity : project.getCurrentEntities()) {
                for (Attribute attribute : entity.getCurrentAttributes()) {
                    Map<String, Object> context = getGlobalContext();
                    context.put(ENTITY_CONTEXT_KEY, entity);
                    context.put("attribute", attribute);
                    produce(context, templatePack, templateName);
                }
            }
        }
    }

    private void produce(Map<String, Object> context, TemplatePack templatePack, String templateName) throws Exception {
        Template template = templatePack.getTemplateByName(templateName);
        context.put("pack", templatePack.getTemplatePackInfo());
        TemplateExecution execution = applicationContext.getBean("templateExecution", TemplateExecution.class);
        context.put("output", execution);
        execution.write(config.getOutputResult(), context, templatePack, template);
    }

    private List<String> getTemplateNames(TemplatePack templatePack, TemplateType templateType) {
        String velocityPattern = templateType.getVelocityPattern();
        return templatePack.getTemplateNames().stream()
                .filter(name -> name.contains(velocityPattern))
                .filter(name -> isTemplateEnabled(templatePack, name))
                .filter(name -> !isCelerioExample(name))
                .toList();
    }

    private List<String> getStaticTemplateNames(TemplatePack templatePack) {
        List<String> velocityPatterns = Arrays.stream(TemplateType.values()).map(TemplateType::getVelocityPattern).toList();
        return templatePack.getTemplateNames().stream()
                .filter(name -> isTemplateEnabled(templatePack, name))
                .filter(name -> !isCelerioExample(name))
                .filter(name -> velocityPatterns.stream().noneMatch(name::contains))
                .toList();
    }

    private List<String> getCelerioExampleTemplateNames(TemplatePack templatePack) {
        return templatePack.getTemplateNames().stream()
                .filter(name -> isTemplateEnabled(templatePack, name))
                .filter(this::isCelerioExample)
                .toList();
    }

    private boolean isTemplateEnabled(TemplatePack templatePack, String name) {
        return config.getCelerio().getConfiguration().hasTemplate(templatePack.getName(), name);
    }

    private boolean isCelerioExample(String path) {
        return path.contains("src\\main\\celerio-example") || path.startsWith("src/main/celerio-example");
    }

    @SuppressWarnings("java:S115") // Keep the names used by template packs and plugin callers.
    public enum TemplateType {
        bootstrap("boot"), //
        project("p"), //
        entity("e"), //
        attribute("a"), //
        enumeration("enum"), //
        compositePrimaryKey("cpk");
        private final String pattern;

        TemplateType(String pattern) {
            this.pattern = pattern;
        }

        public String getVelocityPattern() {
            return "." + pattern + ".vm";
        }
    }

}
