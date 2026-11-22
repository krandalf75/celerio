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

import com.jaxio.celerio.template.pack.TemplatePackInfo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.apache.commons.io.FileUtils.writeStringToFile;

/**
 * This plugin creates several project folder layout following Maven conventions.
 * <p>
 * A Maven <div class="filename">pom.xml</div> is generated as well as default files to help you start from scratch a project.
 * <p>
 * These files represent the minimum required files to produce a project using Celerio.
 *
 * @since 3.0.0
 */
@Mojo(name = "bootstrap-it", defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = false)
public class BootstrapForITMojo extends BootstrapMojo {
    private String currentAppName;
    private String currentBootstrapPack;
    private String currentSqlConfName;
    private String currentRootPackage;

    /**
     * The current folder
     */
    @Parameter(property = "basedir")
    protected String baseDir;

    @Override
    protected String getDefaultBootstrapPackName() {
        return currentBootstrapPack;
    }

    @Override
    protected String getDefaultSqlConfName() {
        return currentSqlConfName;
    }

    @Override
    protected boolean isInteractive() {
        return false;
    }

    @Override
    protected String getDefaultRootPackage() {
        return currentRootPackage;
    }

    @Override
    protected String getDefaultAppName() {
        return currentAppName;
    }

    @Override
    public void execute() throws MojoExecutionException {
        long timestamp = System.currentTimeMillis();
        int counter = 1;
        for (SqlConfInfo sqlConfInfo : getSqlConfInfos()) {
            if ("inheritance".equals(sqlConfInfo.getName())) {
                getLog().warn("Skipping legacy inheritance sqlconf for bootstrap IT on modern toolchains");
                continue;
            }

            for (TemplatePackInfo tpi : getBootstrapPacksInfo()) {

                currentBootstrapPack = tpi.getName();
                currentRootPackage = "integration.ts" + timestamp + "." + tpi.getName().replace("-", "_");
                currentSqlConfName = sqlConfInfo.getName();
                currentAppName = "app_" + appNo(counter++) + "_" + currentBootstrapPack.replace("pack-", "").replace("-", "_") + "_"
                        + currentSqlConfName.replace("-", "_");

                getLog().info("------------------------------------------------");
                getLog().info("Bootstrapping project using " + currentBootstrapPack);
                getLog().info("  rootPackage: " + currentRootPackage);
                getLog().info("  sqlConfName: " + currentSqlConfName);
                getLog().info("------------------------------------------------");

                try {
                    cleanupExistingAppDirectory();
                    StringBuffer toWrite = new StringBuffer("invoker.profiles=" + tpi.getProfilesCSV() + "\n");
                    toWrite.append("invoker.goals=-DskipTests -DskipITs verify\n");
                    toWrite.append("invoker.buildResult=success\n");
                    writeStringToFile(new File(currentAppName, "invoker.properties"), toWrite.toString());
                } catch (IOException ioe) {
                    throw new MojoExecutionException("Could not create invoker.properties", ioe);
                }
                super.execute();
                modernizeGeneratedPomForMaven39();
            }
        }
    }

    private void modernizeGeneratedPomForMaven39() throws MojoExecutionException {
        File generatedPom = new File(currentAppName, "pom.xml");
        if (!generatedPom.isFile()) {
            getLog().warn("Expected generated pom.xml in " + currentAppName + " but file was not found");
            return;
        }

        try {
            String content = Files.readString(generatedPom.toPath());
            String updatedContent = content.replace("<url>http://", "<url>https://");
            updatedContent = removePrimefacesBootstrapTheme(updatedContent);
            updatedContent = addTikaRestletExclusions(updatedContent);
            updatedContent = addJpaQueryByExampleExclusions(updatedContent);
            updatedContent = removeLegacySolrDependencies(updatedContent);
            updatedContent = addModernRepositories(updatedContent);
            updatedContent = modernizeBuildPlugins(updatedContent);
            updatedContent = modernizeAspectjDependencies(updatedContent);
            updatedContent = addJaxbDependencies(updatedContent);

            if (!content.equals(updatedContent)) {
                Files.writeString(generatedPom.toPath(), updatedContent);
            }

            ensureWebappDirectoryExists();
        } catch (IOException ioe) {
            throw new MojoExecutionException("Could not modernize generated pom.xml for " + currentAppName, ioe);
        }
    }

    private void cleanupExistingAppDirectory() throws IOException {
        File appDirectory = new File(currentAppName);
        if (appDirectory.exists()) {
            deleteDirectory(appDirectory);
        }
        if (!appDirectory.mkdirs()) {
            throw new IOException("Could not create IT app directory " + appDirectory.getAbsolutePath());
        }
    }

    private void ensureWebappDirectoryExists() throws IOException {
        File webappDirectory = new File(currentAppName, "src/main/webapp");
        if (!webappDirectory.exists() && !webappDirectory.mkdirs()) {
            throw new IOException("Could not create webapp directory " + webappDirectory.getAbsolutePath());
        }
    }

    private String removePrimefacesBootstrapTheme(String content) {
        final String optionalThemeDependency =
                """
                        <dependency><!-- optional theme (see also web.xml to enable it) -->
                            <groupId>org.primefaces.themes</groupId>
                            <artifactId>bootstrap</artifactId>
                            <version>${primefaces-themes.version}</version>
                        </dependency>
                """;

        if (content.contains(optionalThemeDependency)) {
            return content.replace(optionalThemeDependency, "");
        }
        return content;
    }

    private String addTikaRestletExclusions(String content) {
        final String tikaDependencyWithoutRestletExclusions =
                """
                        <dependency>
                            <groupId>org.apache.tika</groupId>
                            <artifactId>tika-parsers</artifactId>
                            <version>1.11</version>
                            <exclusions>
                                <exclusion>
                                    <artifactId>commons-logging</artifactId>
                                    <groupId>commons-logging</groupId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                """;

        final String tikaDependencyWithRestletExclusions =
                """
                        <dependency>
                            <groupId>org.apache.tika</groupId>
                            <artifactId>tika-parsers</artifactId>
                            <version>1.11</version>
                            <exclusions>
                                <exclusion>
                                    <artifactId>commons-logging</artifactId>
                                    <groupId>commons-logging</groupId>
                                </exclusion>
                                <exclusion>
                                    <groupId>org.restlet.jee</groupId>
                                    <artifactId>org.restlet</artifactId>
                                </exclusion>
                                <exclusion>
                                    <groupId>org.restlet.jee</groupId>
                                    <artifactId>org.restlet.ext.servlet</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                """;

        if (content.contains(tikaDependencyWithoutRestletExclusions)) {
            return content.replace(tikaDependencyWithoutRestletExclusions, tikaDependencyWithRestletExclusions);
        }
        return content;
    }

    private String addModernRepositories(String content) {
        if (content.contains("<id>maven-restlet</id>")) {
            return content;
        }

        final String repositoriesEndTag = "    </repositories>";
        final String restletRepository =
                """
                        <repository>
                            <id>maven-restlet</id>
                            <url>https://maven.restlet.talend.com</url>
                            <layout>default</layout>
                        </repository>
                """;

        if (content.contains(repositoriesEndTag)) {
            return content.replace(repositoriesEndTag, restletRepository + repositoriesEndTag);
        }

        return content;
    }

    private String modernizeBuildPlugins(String content) {
        String updatedContent = content.replace(
                "            <artifactId>maven-war-plugin</artifactId>\n" +
                        "                <version>2.3</version>",
                "            <artifactId>maven-war-plugin</artifactId>\n" +
                        "                <version>3.4.0</version>");

        updatedContent = updatedContent.replace("-Xmx512m -XX:MaxPermSize=256m", "-Xmx512m");

        return updatedContent;
    }

    private String modernizeAspectjDependencies(String content) {
        String updatedContent = content.replace(
                """
                            <groupId>org.aspectj</groupId>
                            <artifactId>aspectjrt</artifactId>
                            <version>1.8.7</version>\
                """,
                """
                            <groupId>org.aspectj</groupId>
                            <artifactId>aspectjrt</artifactId>
                            <version>1.9.22.1</version>\
                """);

        updatedContent = updatedContent.replace(
                """
                            <groupId>org.aspectj</groupId>
                            <artifactId>aspectjweaver</artifactId>
                            <version>1.8.7</version>\
                """,
                """
                            <groupId>org.aspectj</groupId>
                            <artifactId>aspectjweaver</artifactId>
                            <version>1.9.22.1</version>\
                """);

        return updatedContent;
    }

    private String addJaxbDependencies(String content) {
        if (content.contains("<artifactId>jaxb-api</artifactId>")) {
            return content;
        }

        final String dependenciesEndTag = "    </dependencies>";
        final String jaxbDependencies =
                """
                        <dependency>
                            <groupId>javax.xml.bind</groupId>
                            <artifactId>jaxb-api</artifactId>
                            <version>2.3.1</version>
                        </dependency>
                        <dependency>
                            <groupId>com.sun.xml.bind</groupId>
                            <artifactId>jaxb-impl</artifactId>
                            <version>2.3.3</version>
                        </dependency>
                        <dependency>
                            <groupId>com.sun.activation</groupId>
                            <artifactId>javax.activation</artifactId>
                            <version>1.2.0</version>
                        </dependency>
                """;

        if (content.contains(dependenciesEndTag)) {
            return content.replace(dependenciesEndTag, jaxbDependencies + dependenciesEndTag);
        }

        return content;
    }

    private String removeLegacySolrDependencies(String content) {
        final String solrCommonDependency =
                """
                        <dependency>
                            <groupId>org.apache.solr</groupId>
                            <artifactId>solr-common</artifactId>
                            <version>1.3.0</version>
                        </dependency>
                """;

        final String solrCoreDependency =
                """
                        <dependency>
                            <groupId>org.apache.solr</groupId>
                            <artifactId>solr-core</artifactId>
                            <version>5.3.1</version>
                        </dependency>
                """;

        return content.replace(solrCommonDependency, "").replace(solrCoreDependency, "");
    }

    private String addJpaQueryByExampleExclusions(String content) {
        final String dependencyWithoutExclusions =
                """
                        <dependency>
                            <groupId>com.jaxio</groupId>
                            <artifactId>jpa-querybyexample</artifactId>
                            <version>1.0.1</version>
                        </dependency>
                """;

        final String dependencyWithExclusions =
                """
                        <dependency>
                            <groupId>com.jaxio</groupId>
                            <artifactId>jpa-querybyexample</artifactId>
                            <version>1.0.1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>org.apache.solr</groupId>
                                    <artifactId>solr-core</artifactId>
                                </exclusion>
                                <exclusion>
                                    <groupId>org.apache.hadoop</groupId>
                                    <artifactId>hadoop-annotations</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                """;

        if (content.contains(dependencyWithoutExclusions)) {
            return content.replace(dependencyWithoutExclusions, dependencyWithExclusions);
        }

        return content;
    }

    private String appNo(int no) {
        if (no < 10) {
            return "00" + no;
        }

        if (no >= 10 && no < 100) {
            return "0" + no;
        }

        return "" + no;
    }
}
