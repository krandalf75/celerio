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

import com.jaxio.celerio.Config;
import com.jaxio.celerio.convention.WellKnownFolder;
import com.jaxio.celerio.output.EclipseCodeFormatter;
import com.jaxio.celerio.output.OutputResult;
import com.jaxio.celerio.output.SourceFile;
import com.jaxio.celerio.output.XmlCodeFormatter;
import com.jaxio.celerio.template.pack.Template;
import com.jaxio.celerio.template.pack.TemplatePack;
import com.jaxio.celerio.util.IOUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.io.File.separatorChar;
import static java.util.regex.Pattern.*;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.substringBeforeLast;
import static org.apache.velocity.util.StringUtils.normalizePath;

// -----------
// IMPLEMENTATION NOTE: 
//
// Do not get confused!
// userDomainPath | generatedDomainPath correspond for example to src/main/java | src/main/generated-java
// while userSource | generatedSource correspond to baseDir | outputDirectory
// 
// Files are generated in the path: outputDirectory/(userDomainPath or generatedDomainPath)
// But by default outputDirectory is equals to baseDir.
//
// In case outputDirectory and baseDir are different
// having generatedDomainPath different from userDomainPath is not needed as generated code is already clearly separated.
// That's why in such case we set generatedDomainPath to the same value as userDomainPath (see ProjectFactory code).
// -----------

@Service
@Slf4j
public class PreviousEngine {

    private static final String JAVA_EXTENSION = ".java";
    private static final String METAMODEL_EXTENSION = "_" + JAVA_EXTENSION;
    private static final String DYNA_IMPORTS_TAG = "__celerio_dyna_imports__";

    private static final String BASE_SUFFIX = "Base";
    private static final String METAMODEL_BASE_SUFFIX = BASE_SUFFIX + "_";

    private final EclipseCodeFormatter eclipseCodeFormatter;
    private final XmlCodeFormatter xmlCodeFormatter;
    private final IOUtil ioUtil;
    private final ContentWriter contentWriter;
    private final Config config;
    private final VelocityGenerator velocityGenerator;
    @Getter
    private String currentFullFilename = "";
    private String currentClass = "";
    private String currentRootClass = "";
    private String currentRootCast = "";

    @Getter
    private boolean currentEnableDynamicImport;
    private OutputResult outputResult;
    private SourceFile userSource;
    private SourceFile generatedSource;

    public PreviousEngine() {
        this(null, null, null, null, null, null);
    }

    @Autowired
    public PreviousEngine(EclipseCodeFormatter eclipseCodeFormatter, XmlCodeFormatter xmlCodeFormatter, IOUtil ioUtil,
                          ContentWriter contentWriter, Config config, VelocityGenerator velocityGenerator) {
        this.eclipseCodeFormatter = eclipseCodeFormatter;
        this.xmlCodeFormatter = xmlCodeFormatter;
        this.ioUtil = ioUtil;
        this.contentWriter = contentWriter;
        this.config = config;
        this.velocityGenerator = velocityGenerator;
    }

    // ----------------------------------------------------------------------
    // Take over related (user extends the generated class + "Base")
    // ----------------------------------------------------------------------

    public String enableDynaImports() {
        currentEnableDynamicImport = true;
        return DYNA_IMPORTS_TAG;
    }

    /**
     * called within the velocity script.<br>
     * see $velocity.setJavaFilename(...)<br>
     * this allows us to generate dynamically the filename.
     *
     * @param relativePathOrPackage
     * @param filename
     * @param domain
     * @throws Exception
     */
    private String setCurrentFilename(String relativePathOrPackage, String filename, String domain) throws Exception {
        currentFullFilename = convertToFullFilename(relativePathOrPackage, filename, domain);
        return "";
    }

    /**
     * In your velocity template, call this method to set the path and filename of the generated file.
     * The prefix src/main/generated-java or target/.../src/main/java is added automatically.
     * If a user-defined Example extends ExampleBase, generation writes ExampleBase.
     *
     * @param filePath            for example com.acme.myapp or com/acme/myapp
     * @param filename            for example for example MyClass.java or MyClassTest.java
     * @param userDomainPath      for example src/main/java or src/test/java
     * @param generatedDomainPath for example src/main/generated-java or src/test/generated-java
     * @throws IllegalArgumentException if file extension is not .java, .htm or .html
     */
    public String setCurrentFilename(String filePath, String filename, String userDomainPath, String generatedDomainPath) throws Exception {
        if (filename.startsWith(JAVA_EXTENSION) || !filename.contains(JAVA_EXTENSION)) {
            String targetDomain = generatedDomainPath.equals(WellKnownFolder.FLOWS.getGeneratedFolder())
                    ? generatedDomainPath : userDomainPath;
            return setCurrentFilename(filePath, filename, targetDomain);
        }
        if (filename.endsWith(JAVA_EXTENSION)) {
            ImportsContext.setCurrentImportsHolder(new ImportsHolder(filePath));
        }

        String userFilename = convertToFullFilename(filePath, filename, userDomainPath);
        String generatedFilename = convertToFullFilename(filePath, filename, generatedDomainPath);
        boolean generatedMetamodel = filename.endsWith(METAMODEL_EXTENSION) && generatedSource.fileExists(generatedFilename);
        File existingFile = findExistingJavaFile(userFilename, generatedFilename, generatedMetamodel);
        if (existingFile == null) {
            return setRegularJavaFilename(filePath, filename, generatedDomainPath);
        }

        String baseClassName = convertFileNameToBaseClassName(filename);
        String comment;
        if (javaFileExtendsClass(ioUtil.fileToString(existingFile), baseClassName)) {
            currentClass = baseClassName;
            currentRootClass = convertFileNameToClassName(filename);
            currentRootCast = "(" + currentRootClass + ") ";
            ImportsContext.setIsExtendedByUser(true);
            comment = setCurrentFilename(filePath, currentClass + JAVA_EXTENSION, generatedDomainPath);
            if (log.isInfoEnabled()) {
                packInfo("TAKE OVER detected, will generate base class: " + currentClass);
            }
        } else {
            // Keep the user path so the content writer can detect a collision in shared output directories.
            String targetDomain = generatedMetamodel ? generatedDomainPath : userDomainPath;
            comment = setRegularJavaFilename(filePath, filename, targetDomain);
        }
        moveSupersededJavaFile(userDomainPath, generatedDomainPath, generatedFilename, generatedMetamodel);
        return comment;
    }

    private File findExistingJavaFile(String userFilename, String generatedFilename, boolean generatedMetamodel) {
        if (userSource.fileExists(userFilename)) {
            return new File(userSource.getFullPath(userFilename));
        }
        // The entity template may write the metamodel subclass into generated sources before this template runs.
        return generatedMetamodel ? new File(generatedSource.getFullPath(generatedFilename)) : null;
    }

    private String setRegularJavaFilename(String filePath, String filename, String domain) throws Exception {
        String comment = setCurrentFilename(filePath, filename, domain);
        currentClass = convertFileNameToClassName(filename);
        currentRootClass = currentClass;
        currentRootCast = "";
        ImportsContext.setIsExtendedByUser(false);
        return comment;
    }

    private void moveSupersededJavaFile(String userDomainPath, String generatedDomainPath, String generatedFilename, boolean generatedMetamodel) {
        boolean sameSubPath = normalizePath(userDomainPath).equals(normalizePath(generatedDomainPath));
        boolean sameDirectory = outputResult.sameDirectory();
        boolean preserveFile = generatedMetamodel || (sameDirectory ? sameSubPath : !ImportsContext.isExtendedByUser());
        if (!preserveFile && generatedSource.fileExists(generatedFilename)) {
            ioUtil.forceMove(new File(generatedSource.getFullPath(generatedFilename)),
                    new File(userSource.getFullPath(outputResult.getCollisionName(generatedFilename) + ".old")));
        }
    }

    /**
     * Java files (except test file) generated from velocity must use this method to get their class name. When the user has extended the generated class,
     * "Base" is appended to the regular class name. Otherwise, the regular class name is returned. The regular class name is deduced from the java file name.
     */
    public String getCurrentClass() {
        return currentClass;
    }

    public String getCurrentRootClass() {
        return currentRootClass;
    }

    public String getCurrentRootCast(String... generics) {
        if (isBlank(currentRootCast)) {
            return "";
        }
        if (generics == null || generics.length == 0) {
            return currentRootCast;
        }
        return "(" + currentRootClass + "<" + String.join(", ", generics) + ">) ";
    }

    @SuppressWarnings("java:S100") // Existing template packs call this method by name.
    public String getCurrentClassWithout_() {
        if (currentClass.endsWith("_")) {
            return substringBeforeLast(currentClass, "_");
        }
        throw new IllegalStateException("Must be invoked only if you know currentClass ends with '_'");
    }

    // -------------------------------------------------------------------------
    // Template processing
    // -------------------------------------------------------------------------

    /**
     * Returns "abstract" if the current class is extended by the user, an empty string otherwise.
     */
    public String getCurrentAbstract() {
        return ImportsContext.isExtendedByUser() ? "abstract" : "";
    }

    /**
     * add Base to a given java filename it is used for transparently subclassing generated classes
     */
    private String convertFileNameToBaseClassName(String filename) {
        if (filename.endsWith(METAMODEL_EXTENSION)) {
            return substringBeforeLast(filename, METAMODEL_EXTENSION) + METAMODEL_BASE_SUFFIX;
        } else {
            return substringBeforeLast(filename, JAVA_EXTENSION) + BASE_SUFFIX;
        }
    }

    // --------------------------------------------
    // private Utils
    // --------------------------------------------

    protected String convertToFullFilename(String relativePathOrPackage, String filename, String domain) throws Exception {

        if (relativePathOrPackage != null && ".".equals(relativePathOrPackage.trim())) {
            log.warn("******** BUG IN FilenameUtils ==============> " + normalize(relativePathOrPackage.replace('.', separatorChar) + separatorChar + filename));

            throw new IllegalArgumentException("not a good practice, please clean up your template relative path, you may set it to empty string... :" + relativePathOrPackage
                    + ", " + filename);
        }

        String ret;

        if (hasLength(relativePathOrPackage)) {
            ret = normalize(relativePathOrPackage.replace('.', separatorChar) + separatorChar + filename);
        } else {
            ret = filename;
        }

        if (hasLength(domain)) {
            return normalize(domain + File.separator + ret);
        } else {
            return ret;
        }
    }

    /**
     * This method creates a file and generates the class given a template and the type of template @param templateName the velocity template @param
     * templateType the type of template (schema/table/column)
     */
    public void processDynamicFile(Map<String, Object> context, TemplatePack templatePack, Template template) throws Exception {
        try {
            if (!(template.getName().indexOf(".vm.") >= 0 || template.getName().endsWith(".vm"))) {
                throw new IllegalStateException("not a velocity template!: " + template.getName());
            }

            String evaluatedTemplate = null;

            try {
                evaluatedTemplate = velocityGenerator.evaluate(context, templatePack, template);
            } catch (StopFileReachedException _) {
                return;
            }

            if (currentFullFilename.endsWith(".donotgenerate")) {
                return;
            }
            if (isBlank(currentFullFilename)) {
                log.error("In " + templatePack.getName() + ":" + template.getName() + "  target filename is missing");
                return;
            }
            if (!config.getCelerio().getConfiguration().hasFilename(templatePack.getName(), currentFullFilename)) {
                packDebug(templatePack, "SKIPPING:" + currentFullFilename);
                return;
            }

            if (currentEnableDynamicImport) {
                if (ImportsContext.getCurrentImportsHolder().hasImports()) {
                    evaluatedTemplate = evaluatedTemplate.replace(DYNA_IMPORTS_TAG, "\n" + ImportsContext.getCurrentImportsHolder().toJavaImportString());
                } else {
                    evaluatedTemplate = evaluatedTemplate.replace(DYNA_IMPORTS_TAG, "");
                }
            }

            if (currentFullFilename.endsWith(JAVA_EXTENSION)) {
                evaluatedTemplate = eclipseCodeFormatter.format(evaluatedTemplate);
            } else if (currentFullFilename.endsWith(".xml") || currentFullFilename.endsWith(".xhtml")) {
                evaluatedTemplate = xmlCodeFormatter.format(evaluatedTemplate);
            }

            // generated content is in the string writer
            if (log.isDebugEnabled()) {
                packDebug(templatePack, "processing template " + template.getName() + " (" + currentFullFilename + ")");
            }
            try {
                contentWriter.processFile(outputResult, templatePack, template, evaluatedTemplate.getBytes(UTF_8), currentFullFilename);
            } catch (Exception e) {
                log.error("In " + templatePack.getName() + ":" + template.getName() + " template, got exception " + e.getMessage(), e);
            }

        } finally {
            clearDynamicFileContext();
        }
    }

    private void clearDynamicFileContext() {
        currentFullFilename = "";
        currentClass = "";
        ImportsContext.setIsExtendedByUser(false);
        ImportsContext.setCurrentImportsHolder(null);
        currentEnableDynamicImport = false;
    }

    // --------------------------------------------
    // Logging methods
    // --------------------------------------------

    /**
     * return the class name given a java file.
     */
    private String convertFileNameToClassName(String filename) {
        return StringUtils.substringBefore(filename, JAVA_EXTENSION);
    }

    private boolean javaFileExtendsClass(String javaFileContent, String extendedClassName) {
        return match(".*\\s+extends\\s+" + extendedClassName + "\\s*.*", javaFileContent);
    }

    private boolean match(String pattern, String content) {
        if (content == null) {
            return false;
        }

        return compile(pattern, MULTILINE | DOTALL | UNIX_LINES).matcher(content).matches();
    }

    protected void packDebug(TemplatePack templatePack, String message) {
        log.debug("[" + templatePack.getName() + "][" + message + "]");
    }

    // -------------------------------------------------
    // Engine Configuration
    // -------------------------------------------------

    protected void packInfo(TemplatePack templatePack, String message) {
        log.info("[" + templatePack.getName() + "][" + message + "]");
    }

    protected void packInfo(String message) {
        log.info("[" + message + "]");
    }

    protected void packWarn(TemplatePack templatePack, String message) {
        log.warn("[" + templatePack.getName() + "][" + message + "]");
    }

    public OutputResult getOutputResult() {
        return outputResult;
    }

    public void setOutputResult(OutputResult outputResult) {
        this.outputResult = outputResult;
        this.userSource = outputResult.getUserSource();
        this.generatedSource = outputResult.getGeneratedSource();
    }

    private boolean hasLength(String s) {
        return s != null && !s.isEmpty();
    }

    public void stopFileGeneration() {
        throw new StopFileReachedException();
    }

    public static class StopFileReachedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
