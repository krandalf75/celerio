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

import com.jaxio.celerio.output.OutputResult;
import com.jaxio.celerio.output.SourceFile;
import com.jaxio.celerio.template.pack.Template;
import com.jaxio.celerio.template.pack.TemplatePack;
import com.jaxio.celerio.util.IOUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

import static com.jaxio.celerio.template.ContentWriter.Action.*;
import static org.apache.commons.io.FilenameUtils.normalize;

@Slf4j
@Service
public class ContentWriter {
    private final IOUtil ioUtil;

    @Autowired
    public ContentWriter(IOUtil ioUtil) {
        this.ioUtil = ioUtil;
    }

    public void processFile(OutputResult outputResult, TemplatePack templatePack, Template template, byte[] contentToPublish, String targetFilename)
            throws IOException {
        SourceFile userSource = outputResult.getUserSource();
        SourceFile generatedSource = outputResult.getGeneratedSource();
        String filename = normalize(targetFilename);

        if (!outputResult.sameDirectory() && userSource.fileExists(filename)) {
            if (generatedSource.fileExists(filename)) {
                ioUtil.forceMove(new File(generatedSource.getFullPath(filename)),
                        new File(userSource.getFullPath(outputResult.getCollisionName(filename) + ".old")));
            }
            if (userSource.isSameContent(filename, contentToPublish)) {
                logIdentical(templatePack, filename);
            } else {
                writeCollision(outputResult, templatePack, template, contentToPublish, filename);
            }
            return;
        }

        if (!generatedSource.fileExists(filename)) {
            writeGenerated(outputResult, templatePack, template, contentToPublish, filename, GENERATE);
        } else if (generatedSource.isSameContent(filename, contentToPublish)) {
            logIdentical(templatePack, filename);
        } else if (outputResult.hasCollision(filename)) {
            writeCollision(outputResult, templatePack, template, contentToPublish, filename);
        } else {
            writeGenerated(outputResult, templatePack, template, contentToPublish, filename, GENERATE_AND_REPLACE);
        }
    }

    private void logIdentical(TemplatePack templatePack, String filename) {
        if (log.isDebugEnabled()) {
            packDebug(templatePack, IDENTICAL_EXISTS, filename);
        }
    }

    private void writeCollision(OutputResult outputResult, TemplatePack pack, Template template, byte[] content, String filename)
            throws IOException {
        String collisionName = outputResult.getCollisionName(filename);
        if (log.isInfoEnabled()) {
            packInfo(pack, GENERATE_IN_COLLISIONS_FOLDER, collisionName);
        }
        outputResult.addCollisionContent(content, collisionName, pack, template);
    }

    private void writeGenerated(OutputResult outputResult, TemplatePack pack, Template template, byte[] content, String filename,
            Action action) throws IOException {
        if (log.isInfoEnabled()) {
            packInfo(pack, action, getFullPathForLog(outputResult, filename));
        }
        outputResult.addContent(content, filename, pack, template);
    }

    /**
     * Use it only to display proper path in log
     *
     * @param targetFilename
     */
    private String getFullPathForLog(OutputResult or, String targetFilename) {
        if (or.sameDirectory()) {
            return targetFilename;
        } else {
            return or.getGeneratedSource().getFullPath(targetFilename);
        }
    }

    private void packDebug(TemplatePack templatePack, Action action, String file) {
        log.debug("[" + templatePack.getName() + "][" + action.value + "] " + file);
    }

    private void packInfo(TemplatePack templatePack, Action action, String file) {
        log.info("[" + templatePack.getName() + "][" + action.value + "] " + file);
    }

    public enum Action {
        GENERATE("generate"), //
        GENERATE_AND_REPLACE("generate and replace"), //
        IDENTICAL_EXISTS("identical exists"), //
        GENERATE_IN_COLLISIONS_FOLDER("generate in collision folder");
        String value;

        Action(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }
}