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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GITStatusCrawler {

    private GITStatusCrawler() {
    }

    public static boolean isProjectUnderGit(File baseDir) {
        File dirHack = new File(baseDir, ".celerio-please-ignore-git");
        if (dirHack.exists()) {
            return false;
        }

        File dir = new File(baseDir, ".git");
        return dir.exists() && dir.isDirectory();
    }

    public static SCMStatus doStatus(File baseDir) throws RuntimeException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder().setGitDir(new File(baseDir, ".git"));
        try (Repository repository = builder.build();
             TreeWalk treeWalk = new TreeWalk(repository)) {
            Map<String, Boolean> map = new HashMap<String, Boolean>();

            treeWalk.addTree(getTree(repository));
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                map.put(treeWalk.getPathString(), Boolean.TRUE);
            }

            log.info("-----------------------------------------------------------------------------------------------");
            log.info("PROJECT IS UNDER GIT: Files tracked by git ({}) won't be overwritten/deleted by Celerio", map.size());
            log.info("-----------------------------------------------------------------------------------------------");

            return new SCMStatus(map);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static RevTree getTree(Repository repository) throws IOException {
        ObjectId lastCommitId = repository.resolve(Constants.HEAD);

        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(lastCommitId).getTree();
        }
    }
}
