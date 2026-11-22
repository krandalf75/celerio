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

package com.jaxio.celerio.output;

import com.jaxio.celerio.util.IOUtil;
import com.thoughtworks.xstream.XStream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Maps.newHashMap;

@Service
@Slf4j
public class FileTracker {
    @Setter
    private String generatedFileLocation = ".celerio";

    private final IOUtil ioUtil;

    @Autowired
    public FileTracker(IOUtil ioUtil) {
        this.ioUtil = ioUtil;
    }

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private String getFilename() {
        return generatedFileLocation + File.separatorChar + "generated.xml";
    }

    public SCMStatus getSCMStatus(File projectDir) {
        if (GITStatusCrawler.isProjectUnderGit(projectDir)) {
            return GITStatusCrawler.doStatus(projectDir);
        } else if (SVNStatusCrawler.isProjectUnderSvn(projectDir)) {
            return SVNStatusCrawler.doStatus(projectDir);
        } else {
            return new SCMStatus(null);
        }
    }

    // Keep the concrete return type in the existing public API.
    @SuppressWarnings({"unchecked", "java:S1319"})
    public HashMap<String, FileMetaData> loadFromProjectDir(File projectDir) throws IOException {
        File xmlFileMetaDatas = new File(projectDir, getFilename());

        if (!xmlFileMetaDatas.exists()) {
            info("File Tracker Metadata not found: " + xmlFileMetaDatas.getAbsolutePath());
            return newHashMap();
        }

        XStream xstream = createXStream();
        try (FileInputStream in = new FileInputStream(xmlFileMetaDatas)) {
            return (HashMap<String, FileMetaData>) xstream.fromXML(in);
        }
    }

    // Keep the public signature and the existing HashMap XML representation.
    @SuppressWarnings("java:S1319")
    public void saveToProjectDir(HashMap<String, FileMetaData> fileMetaDatas, File projectDir) throws IOException {
        File xmlFileMetaDatas = new File(projectDir, getFilename());
        File parent = xmlFileMetaDatas.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        } // else child was for example "."

        XStream xstream = createXStream();
        try (FileOutputStream out = new FileOutputStream(xmlFileMetaDatas)) {
            xstream.toXML(fileMetaDatas, out);
        }
    }

    private XStream createXStream() {
        XStream xstream = new XStream();
        xstream.allowTypesByWildcard(new String[]{
                "com.jaxio.celerio.output.*",
                "java.util.*"
        });
        return xstream;
    }

    @SuppressWarnings("java:S1319") // Keep the concrete return type for existing callers.
    public HashSet<FileMetaData> deleteGeneratedFileIfIdentical(File projectDir, List<String> excludedPatterns) throws IOException {
        SCMStatus scmStatus = getSCMStatus(projectDir);
        var deletedFiles = new HashSet<FileMetaData>();
        File trackerFile = new File(projectDir, getFilename());
        if (!trackerFile.exists()) {
            info("CANNOT FIND FILE TRACKER METADATA " + trackerFile.getAbsolutePath());
            return deletedFiles;
        }

        var filesToScan = loadFromProjectDir(projectDir);
        filesToScan.entrySet().removeIf(entry -> removeTrackedFile(projectDir, scmStatus, excludedPatterns,
                entry.getKey(), entry.getValue(), deletedFiles));
        info("rewrite file tracker metadata: " + getFilename());
        saveToProjectDir(filesToScan, projectDir);
        return deletedFiles;
    }

    private boolean removeTrackedFile(File projectDir, SCMStatus scmStatus, List<String> excludedPatterns,
            String relativePath, FileMetaData trackedFile, Set<FileMetaData> deletedFiles) {
        File file = new File(projectDir, relativePath);
        if (!file.exists()) {
            info("skip delete (no longer exists): " + relativePath);
            return true;
        }
        if (scmStatus.isUnderSCM(relativePath)) {
            info("skip delete (file now under SCM): " + relativePath);
            return true;
        }
        if (trackedFile.isBootstrapFile()) {
            info("skip file generated during bootstrap: " + relativePath);
            return false;
        }
        FileMetaData currentFile = new FileMetaData(null, null, relativePath, file);
        if (!trackedFile.equals(currentFile)) {
            info("skip delete (potential user modifications): " + relativePath);
            return false;
        }
        if (mustExcludeFile(relativePath, excludedPatterns)) {
            info("skip delete (present in excludedFiles): " + relativePath);
            return false;
        }

        info("delete: " + relativePath);
        try {
            if (Files.deleteIfExists(file.toPath())) {
                ioUtil.pruneEmptyDirs(file);
                deletedFiles.add(currentFile);
            }
            return true;
        } catch (IOException | SecurityException e) {
            log.warn("Could not delete generated file {}", relativePath, e);
            return false;
        }
    }

    private void info(String msg) {
        log.info("[" + msg + "]");
    }

    private boolean mustExcludeFile(String path, List<String> excludedPatterns) {
        if (excludedPatterns == null || excludedPatterns.isEmpty()) {
            return false;
        }

        for (String pattern : excludedPatterns) {
            if (pattern != null && antPathMatcher.match(pattern, path)) {
                return true;
            }
        }

        return false;
    }
}
