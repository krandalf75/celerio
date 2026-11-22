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

package com.jaxio.celerio.maven.plugin.bootstrap;

import com.jaxio.celerio.Brand;
import com.jaxio.celerio.configuration.BuildInfo;
import com.jaxio.celerio.configuration.Configuration;
import com.jaxio.celerio.configuration.Pack;
import com.jaxio.celerio.configuration.database.JdbcConnectivity;
import com.jaxio.celerio.configuration.database.Metadata;
import com.jaxio.celerio.main.CelerioProducer;
import com.jaxio.celerio.output.OutputResult;
import com.jaxio.celerio.output.OutputResultFactory;
import com.jaxio.celerio.template.pack.ClasspathTemplatePackInfoLoader;
import com.jaxio.celerio.template.pack.TemplatePackInfo;
import com.jaxio.celerio.util.XsdHelper;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.jaxio.celerio.util.PackageUtil.isPackageNameValid;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.substringAfter;

/**
 * This plugin creates a default project folder layout following Maven conventions.
 * <p>
 * A Maven <div class="filename">pom.xml</div> is generated as well as default files to help you start from scratch a project.
 * <p>
 * These files represent the minimum required files to produce a project using Celerio.
 *
 * @since 3.0.0
 */
@Mojo(name = "bootstrap", defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = false)
public class BootstrapMojo extends AbstractMojo {
    private static final String GENERATOR_CONFIG_DIRECTORY = "src/main/config/celerio-maven-plugin";

    /**
     * Whether the bootstrap ask interactively for appName, rootPackage etc.?
     */
    @Parameter(property = "bootstrap-maven-plugin.interactive", defaultValue = "true")
    protected String interactive = "true";

    /**
     * The bootstrap pack to use by default if none is specified or if running in non-interactive mode.
     */
    @Parameter(property = "bootstrap-maven-plugin.defaultBootstrapPackName", defaultValue = "pack-backend-jpa")
    protected String defaultBootstrapPackName = "pack-backend-jpa";

    /**
     * The sql conf to use by default if none is specified or if running in non-interactive mode.
     */
    @Parameter(property = "bootstrap-maven-plugin.defaultSqlConfName", defaultValue = "books")
    protected String defaultSqlConfName = "books";


    private List<TemplatePackInfo> bootstrapPacksInfo;
    private final Brand brand = new Brand();
    private String rootPackage;
    private String appName;
    private String bootstrapPackName;
    private String sqlConfName;
    private String packCommand = "";
    private String packCommandHelp = "";

    // ------------------------------------
    // cmd line params
    // ------------------------------------
    private static final String PARAM_CELERIO_PACK = "celerioPack";

    private static final String PARAM_APP_NAME = "appName";
    private static final String PARAM_APP_ROOT_PACKAGE = "appRootPackage";

    // driver
    private static final String PARAM_JDBC_GROUP_ID = "jdbcGroupId";
    private static final String PARAM_JDBC_ARTIFACT_ID = "jdbcArtifactId";
    private static final String PARAM_JDBC_VERSION = "jdbcVersion";
    private static final String PARAM_JDBC_DRIVER = "jdbcDriver";

    // access
    private static final String PARAM_HIBERNATE_DIALECT = "hibernateDialect";
    private static final String PARAM_JDBC_USER = "jdbcUser";
    private static final String PARAM_JDBC_PASSWORD = "jdbcPassword";
    private static final String PARAM_JDBC_URL = "jdbcUrl";
    private static final String PARAM_JDBC_CATALOG = "jdbcCatalog";
    private static final String PARAM_JDBC_SCHEMA = "jdbcSchema";

    private static final List<String> PARAMS = List.of(
            PARAM_CELERIO_PACK, PARAM_APP_NAME, PARAM_APP_ROOT_PACKAGE,
            PARAM_JDBC_GROUP_ID, PARAM_JDBC_ARTIFACT_ID, PARAM_JDBC_VERSION, PARAM_JDBC_DRIVER,
            PARAM_HIBERNATE_DIALECT, PARAM_JDBC_USER, PARAM_JDBC_PASSWORD,
            PARAM_JDBC_URL, PARAM_JDBC_CATALOG, PARAM_JDBC_SCHEMA);


    // ------------------------------
    // CELERIO GLUE
    // ------------------------------
    private ApplicationContext context;

    protected boolean isInteractive() {
        return interactive.equals("true");
    }

    protected String getDefaultBootstrapPackName() {
        return defaultBootstrapPackName;
    }

    protected String getDefaultSqlConfName() {
        return defaultSqlConfName;
    }

    protected String getDefaultRootPackage() {
        return brand.getRootPackage();
    }

    protected String getDefaultAppName() {
        if (bootstrapPackName != null && sqlConfName != null) {
            return substringAfter(bootstrapPackName, "pack-").replace('-', '_') + "_" + sqlConfName.replace('-', '_');
        }

        return "appli";
    }

    // ------------------------------------
    // MOJO & WIZARDS
    // ------------------------------------

    /**
     * Mojo entry point.
     */
    @Override
    public void execute() throws MojoExecutionException {

        try {
            if (getBootstrapPacksInfo().isEmpty()) {
                getLog().error("Could not find any Celerio Template Pack having a META-INF/celerio.txt file on the classpath!");
                return;
            }
            celerioWelcomeBanner();

            JdbcConnectivity jdbcConnectivity = null;

            if (useCommandLineParameters()) {
                jdbcConnectivity = setUpParamValuesAndReturnJdbcConnectivity();

                TemplatePackInfo tpi = getBootstrapPackInfoByName(bootstrapPackName);
                packCommand = tpi.getCommand();
                packCommandHelp = tpi.getCommandHelp();
            } else {
                if (isInteractive()) {
                    startInteractiveConfWizard();
                } else {
                    useDefaultConf();
                }
            }

            runCelerioInBootstrapMode(jdbcConnectivity);

            copySqlConf();

            copyCelerioXsd();

            printInstructionsOnceBootstrapIsReady();

        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    private boolean useCommandLineParameters() {
        // light check
        return checkParamPresence(PARAM_APP_NAME) && checkParamPresence(PARAM_JDBC_DRIVER) && checkParamPresence(PARAM_JDBC_USER);
    }

    private boolean checkParamPresence(String paramName) {
        String v = System.getProperty(paramName);
        return v != null && !v.isEmpty();
    }

    private JdbcConnectivity setUpParamValuesAndReturnJdbcConnectivity() {

        HashMap<String, String> map = new HashMap<>();
        for (String param : PARAMS) {
            map.put(param, System.getProperty(param));
        }

        rootPackage = map.get(PARAM_APP_ROOT_PACKAGE);
        appName = map.get(PARAM_APP_NAME);
        bootstrapPackName = map.get(PARAM_CELERIO_PACK);
        sqlConfName = "empty";

        JdbcConnectivity jdbc = new JdbcConnectivity();
        jdbc.setDriver(map.get(PARAM_JDBC_DRIVER));
        jdbc.setDriverGroupId(map.get(PARAM_JDBC_GROUP_ID));
        jdbc.setDriverArtifactId(map.get(PARAM_JDBC_ARTIFACT_ID));
        jdbc.setDriverArtifactIdVersion(map.get(PARAM_JDBC_VERSION));

        jdbc.setHibernateDialect(map.get(PARAM_HIBERNATE_DIALECT));
        jdbc.setUser(map.get(PARAM_JDBC_USER));
        jdbc.setPassword(map.get(PARAM_JDBC_PASSWORD));
        jdbc.setSchemaName(map.get(PARAM_JDBC_SCHEMA));
        jdbc.setCatalog(map.get(PARAM_JDBC_CATALOG));
        jdbc.setUrl(map.get(PARAM_JDBC_URL));

        if (jdbc.getDriver().toLowerCase().contains("oracle") || jdbc.getDriver().toLowerCase().contains("derby")) {
            jdbc.setSqlDelimiter("/");
        } else {
            jdbc.setSqlDelimiter(";");
        }

        return jdbc;
    }

    /**
     * Ask the user all the info we need.
     */
    @SuppressWarnings("java:S8450") // A single reader shares all buffered input across wizard steps.
    private void startInteractiveConfWizard() throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            printConsoleLine("");
            chooseBootstrapPack(br);
            chooseSampleSqlAndConf(br);
            enterAppName(br);
            enterPackageName(br);
        }
    }

    private String readWizardInput(BufferedReader reader) throws IOException {
        String input = reader.readLine();
        if (input == null) {
            throw new EOFException("Celerio bootstrap setup ended before all selections were entered");
        }
        return input;
    }

    private int readMenuIndex(BufferedReader reader, int optionCount) throws IOException {
        String choice = readWizardInput(reader);
        if (isBlank(choice)) {
            return -1;
        }
        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < optionCount) {
                return index;
            }
        } catch (NumberFormatException _) {
            // The wizard displays the choices again after invalid input.
        }
        printConsoleLine("");
        return -1;
    }

    // ----------------------------------------
    // TEMPLATE PACKS
    // ----------------------------------------

    private void useDefaultConf() {
        appName = getDefaultAppName();
        rootPackage = getDefaultRootPackage() + "." + getDefaultAppName();
        bootstrapPackName = getDefaultBootstrapPackName();
        sqlConfName = getDefaultSqlConfName();
    }

    /**
     * Interactively ask the user which pack should be used.
     */
    private void chooseBootstrapPack(BufferedReader br) throws IOException {
        List<TemplatePackInfo> options = getBootstrapPacksInfo();
        while (true) {
            printInstruction("Choose the type of application you want to generate:");
            for (int i = 0; i < options.size(); i++) {
                TemplatePackInfo templatePackInfo = options.get(i);
                printConsoleLine(" " + (i + 1) + ") " + templatePackInfo.getName());
                printConsoleLine("    " + templatePackInfo.getProjectLink());
                printConsoleLine("    " + templatePackInfo.getDescription());

                if (templatePackInfo.getDescription2() != null) {
                    printConsoleLine("    " + templatePackInfo.getDescription2());
                }
                printConsoleLine("");
            }

            int index = readMenuIndex(br, options.size());
            if (index >= 0) {
                TemplatePackInfo chosen = options.get(index);
                bootstrapPackName = chosen.getName();
                printConsoleLine("OK, using: " + bootstrapPackName);
                packCommand = chosen.getCommand();
                packCommandHelp = chosen.getCommandHelp();
                return;
            }
        }
    }

    // ----------------------------------------
    // SQL CONF
    // ----------------------------------------

    /**
     * Return the celerio template packs found on the classpath.
     */
    protected List<TemplatePackInfo> getBootstrapPacksInfo() {
        if (bootstrapPacksInfo == null) {
            bootstrapPacksInfo = getCelerioApplicationContext().getBean(ClasspathTemplatePackInfoLoader.class).resolveTopLevelPacks();
        }
        return bootstrapPacksInfo;
    }

    private TemplatePackInfo getBootstrapPackInfoByName(String name) {
        for (TemplatePackInfo tpi : getBootstrapPacksInfo()) {
            if (tpi.getName().equals(name)) {
                return tpi;
            }
        }
        return null;
    }


    /**
     * Interactively ask the user which sql conf should be used.
     */
    private void chooseSampleSqlAndConf(BufferedReader br) throws IOException {
        List<SqlConfInfo> options = getSqlConfInfos();
        while (true) {
            printInstruction("Which sample SQL schema would you like to use?");
            for (int i = 0; i < options.size(); i++) {
                printConsoleLine(" " + (i + 1) + ") " + options.get(i).getName());
                printConsoleLine("    " + options.get(i).getDescription());
                if (options.get(i).getDescription2() != null) {
                    printConsoleLine("    " + options.get(i).getDescription2());
                }
                printConsoleLine("");
            }

            int index = readMenuIndex(br, options.size());
            if (index >= 0) {
                sqlConfName = options.get(index).getName();
                printConsoleLine("OK, using: " + sqlConfName);
                return;
            }
        }
    }

    /**
     * Scan the classpath for SQL configurations.
     */
    protected List<SqlConfInfo> getSqlConfInfos() {
        List<SqlConfInfo> packInfos = newArrayList();
        PathMatchingResourcePatternResolver o = new PathMatchingResourcePatternResolver();

        try {
            Resource[] packInfosAsResource = o.getResources("classpath*:sqlconf/*/00-info.txt");
            for (Resource r : packInfosAsResource) {
                packInfos.add(new SqlConfInfo(r));
            }

            packInfos.sort(null);
            return packInfos;
        } catch (IOException ioe) {
            throw new RuntimeException("Error while searching for SQL CONF having a sqlconf/*/00-info.txt file!", ioe);
        }
    }

    private void copySqlConf() throws IOException {
        PathMatchingResourcePatternResolver o = new PathMatchingResourcePatternResolver();

        File sqlDir = new File(appName + "/src/main/sql/h2");
        sqlDir.mkdirs();

        // copy sql
        copySingleSqlFile(o, sqlDir, "01-drop.sql");
        copySingleSqlFile(o, sqlDir, "02-create.sql");
        copySingleSqlFile(o, sqlDir, "03-import.sql");

        // copy conf.
        Resource[] xmlResources = o.getResources("classpath*:sqlconf/" + sqlConfName + "/celerio-maven-plugin.xml");
        String xmlConf;
        try (InputStream in = xmlResources[0].getInputStream()) {
            xmlConf = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        xmlConf = xmlConf.replace("${groupId}", rootPackage); // same as for archetype
        xmlConf = xmlConf.replace("${artifactId}", appName); // same as for archetype
        File xmlDir = new File(appName, GENERATOR_CONFIG_DIRECTORY);
        xmlDir.mkdirs();
        File xmlFile = new File(xmlDir, "celerio-maven-plugin.xml");
        getLog().info("Copy configuration file from '" + sqlConfName + "' resources: " + xmlFile.getName());
        Files.writeString(xmlFile.toPath(), xmlConf);
    }

    private void copySingleSqlFile(PathMatchingResourcePatternResolver o, File sqlDir, String filename) throws IOException {
        Resource[] sqlResources = o.getResources("classpath*:sqlconf/" + sqlConfName + "/" + filename);
        File sqlFile = new File(sqlDir, filename);
        getLog().info("Copy SQL file from '" + sqlConfName + "' resources: " + sqlFile.getName());
        try (InputStream in = sqlResources[0].getInputStream();
             OutputStream out = new FileOutputStream(sqlFile)) {
            in.transferTo(out);
        }
    }

    // ----------------------------------------
    // APP NAME AND PACKAGE NAME
    // ----------------------------------------

    private void copyCelerioXsd() throws IOException {
        File xsdDir = new File(appName, GENERATOR_CONFIG_DIRECTORY);
        xsdDir.mkdirs();
        File celerioXsdFile = new File(xsdDir, "celerio.xsd");
        File nonamespaceXsdFile = new File(xsdDir, "nonamespace.xsd");

        getLog().info("Copy Celerio configuration xsd files to " + xsdDir);
        Files.writeString(celerioXsdFile.toPath(), XsdHelper.getCelerioXsdAsString());
        Files.writeString(nonamespaceXsdFile.toPath(), XsdHelper.getNonamespaceXsdAsString());
    }

    /**
     * Ask the user to enter the package name.
     */
    private void enterPackageName(BufferedReader br) throws IOException {
        String suggestedRootPackage = getDefaultRootPackage() + "." + appName;
        while (true) {
            printInstruction("Enter the Java root package of your application: [" + suggestedRootPackage + "]");

            String packageNameCandidate = readWizardInput(br);
            if (isBlank(packageNameCandidate)) {
                rootPackage = suggestedRootPackage;
                return;
            }
            if (isPackageNameValid(packageNameCandidate)) {
                rootPackage = packageNameCandidate;
                return;
            }
            printConsoleLine("Oops! invalid Java package name.");
            printConsoleLine("");
        }
    }

    /**
     * Ask the user to enter the application name.
     */
    private void enterAppName(BufferedReader br) throws IOException {
        while (true) {
            printInstruction("Enter your application name: [" + getDefaultAppName() + "]");

            String appNameCandidate = readWizardInput(br);
            if (isBlank(appNameCandidate)) {
                appName = getDefaultAppName();
                return;
            }
            if (isPackageNameValid(getDefaultRootPackage() + "." + appNameCandidate)) {
                appName = appNameCandidate;
                return;
            }
            printConsoleLine("Oops! invalid application name. Keep it simple, no '-', etc...");
            printConsoleLine("");
        }
    }

    /**
     * Returns the Celerio engine spring application context.
     */
    private ApplicationContext getCelerioApplicationContext() {
        if (context == null) {
            context = new ClassPathXmlApplicationContext("classpath:applicationContext-celerio.xml");
        }
        return context;
    }

    private BuildInfo getCelerioBuildInfo() {
        return getCelerioApplicationContext().getBean(BuildInfo.class);
    }

    private void runCelerioInBootstrapMode(JdbcConnectivity jdbcConnectivity) throws Exception {
        getLog().info("Please wait a moment...");
        CelerioProducer producer = getCelerioApplicationContext().getBean(CelerioProducer.class);

        Configuration configuration = new Configuration();
        configuration.setRootPackage(rootPackage);
        configuration.setApplicationName(appName);
        configuration.setPacks(newArrayList(new Pack(bootstrapPackName)));

        producer.getConfig().getCelerio().setConfiguration(configuration);
        if (jdbcConnectivity != null) {
            Metadata metadata = new Metadata();
            metadata.setJdbcConnectivity(jdbcConnectivity);
            producer.getConfig().setMetadata(metadata);
        }

        producer.getConfig().setOutputResult(getOutputResult(getCelerioApplicationContext()));
        producer.bootstrap();
    }

    private OutputResult getOutputResult(ApplicationContext context) {
        return context.getBean(OutputResultFactory.class).getOutputResult(appName, appName);
    }

    // ----------------------------------------
    // COSMETIC PRINT
    // ----------------------------------------

    private void celerioWelcomeBanner() {
        // http://ascii.mastervb.net/
        // font : varsity.ftl
        int currentYear = Year.now(ZoneId.systemDefault()).getValue();
        getLog().info("");
        getLog().info("        ______         __                  _          ");
        getLog().info("      .' ___  |       [  |                (_)         ");
        getLog().info("     / .'   \\_| .---.  | | .---.  _ .--.  __   .--.   ");
        getLog().info("     | |       / /__\\\\ | |/ /__\\\\[ `/'`\\][  |/ .'`\\ \\ ");
        getLog().info("     \\ `.___.'\\| \\__., | || \\__., | |     | || \\__. | ");
        getLog().info("      `.____ .' '.__.'[___]'.__.'[___]   [___]'.__.'   v" + getCelerioBuildInfo().getPomVersion());
        getLog().info("");
        getLog().info("       Branding file: " + brand.getBrandingPath());
        getLog().info("       Documentation: http://www.jaxio.com/documentation/celerio/");
        getLog().info("       (c) 2005-" + currentYear + " Jaxio, http://www.jaxio.com");
        getLog().info("");
    }

    private void printInstructionsOnceBootstrapIsReady() {
        getLog().info("");
        getLog().info("============================================");
        getLog().info("");
        printConsoleLine("");
        printConsoleLine("FINAL STEPS:");
        printConsoleLine("");
        printConsoleLine("1/ Optional: you can edit the generated pom.xml file to use your own database");
        printConsoleLine("");
        printConsoleLine("2/ Execute the following commands:");
        printConsoleLine("");
        printConsoleLine("cd " + appName);
        printConsoleLine("" + packCommand);
        printConsoleLine("");
        if (packCommandHelp != null && !packCommandHelp.isEmpty()) {
            printConsoleLine("3/ " + packCommandHelp);
            printConsoleLine("");
        }
        getLog().info("");
        getLog().info("============================================");
    }

    /**
     * Print the passed instruction in the console.
     */
    private void printInstruction(String instruction) {
        printConsoleLine(StringUtils.repeat("-", instruction.length()));
        printConsoleLine(instruction);
        printConsoleLine(StringUtils.repeat("-", instruction.length()));
    }
    @SuppressWarnings("java:S106") // Bootstrap prompts and choices are part of the interactive console interface.
    private void printConsoleLine(String line) {
        System.out.println(line);
    }

}
