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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.OrFileFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newTreeSet;
import static org.apache.commons.io.IOUtils.closeQuietly;

@Service
@Slf4j
public class IOUtil {

    /**
     * comes from org.codehaus.plexus.util.DirectoryScanner
     */
    @SuppressWarnings("java:S2386") // Keep the public suffix array used by existing callers.
    public static final String[] DEFAULT_EXCLUDES_SUFFIXES = {"~", "bak", "old"};

    /**
     * Write to the outputstream the bytes read from the input stream.
     */
    public int inputStreamToOutputStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024 * 100]; // 100kb
        int len = -1;
        int total = 0;

        while ((len = is.read(buffer)) >= 0) {
            os.write(buffer, 0, len);
            total += len;
        }
        return total;
    }

    /**
     * Save a string to a file.
     *
     * @param content the string to be written to file
     * @param file    fhe file object
     */
    public void stringToFile(String content, File file) throws IOException {
        stringToOutputStream(content, new FileOutputStream(file));
    }

    /**
     * Save a string to a file.
     *
     * @param content  the string to be written to file
     * @param filename the full or relative path to the file.
     */
    public void stringToFile(String content, String filename) throws IOException {
        stringToOutputStream(content, new FileOutputStream(filename));
    }

    /**
     * Save a string to a file.
     *
     * @param content the string to be written to file
     */
    public void stringToOutputStream(String content, OutputStream out) throws IOException {
        out.write(content.getBytes());
        out.close();
    }

    /**
     * Write to a file the bytes read from an input stream.
     *
     * @param filename the full or relative path to the file.
     */
    public void inputStreamToFile(InputStream is, String filename) throws IOException {
        FileOutputStream fos = new FileOutputStream(filename);
        inputStreamToOutputStream(is, fos);
        fos.close();
    }

    /**
     * Write to a string the bytes read from a file
     *
     * @param fileName the file name
     * @return the file as a string
     */
    public String fileToString(String fileName) throws IOException {
        return fileToString(new File(fileName));
    }

    /**
     * Write to a string the bytes read from a file
     *
     * @param file the file
     * @return the file as a string
     */
    public String fileToString(File file) throws IOException {
        return inputStreamToString(new FileInputStream(file), null);
    }

    /**
     * Write to a string the bytes read from an input stream.
     *
     * @param charset the charset used to read the input stream
     * @return the inputstream as a string
     */
    public String inputStreamToString(InputStream is, String charset) throws IOException {
        try (InputStreamReader reader = charset == null ? new InputStreamReader(is) : new InputStreamReader(is, charset)) {
            StringWriter writer = new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        }
    }

    public boolean contentEquals(File file1, File file2) {
        try (FileReader first = new FileReader(file1);
             FileReader second = new FileReader(file2)) {
            return IOUtils.contentEquals(first, second);
        } catch (Exception _) {
            return false;
        }
    }

    public boolean contentEquals(String file1, String file2) {
        return contentEquals(new File(file1), new File(file2));
    }

    public boolean contentEquals(Reader reader1, Reader reader2) {
        try {
            return IOUtils.contentEquals(reader1, reader2);
        } catch (Exception _) {
            return false;
        } finally {
            closeQuietly(reader1);
            closeQuietly(reader2);
        }
    }

    /**
     * Determine if the directory where the passed file resides is empty.
     *
     * @param file the folder to remove
     * @return true if the parent folder is empty, false otherwise
     */
    public boolean isParentAnEmptyDirectory(File file) {
        File parent = file.getParentFile();

        String[] entries = parent == null ? null : parent.list();
        return entries != null && entries.length == 0;
    }

    /**
     * prune empty dir
     *
     * @param targetFile the folder to remove
     */
    public void pruneEmptyDirs(String targetFile) {
        pruneEmptyDirs(new File(targetFile));
    }

    /**
     * prune empty dir
     *
     * @param targetFile the folder to remove
     */
    public void pruneEmptyDirs(File targetFile) {
        while (isParentAnEmptyDirectory(targetFile)) {
            File parent = targetFile.getParentFile();
            try {
                Files.delete(parent.toPath());
            } catch (IOException | SecurityException _) {
                return;
            }
            targetFile = parent;
        }
    }

    /**
     * Recurse in the folder to get the list all files and folders of all non svn files
     *
     * @param folder the folder to parse
     */
    public Collection<String> listFiles(File folder) {
        return listFiles(folder, null);
    }

    /**
     * Recurse in the folder to get the list all files and folders
     * <ul>
     * <li>do not recurse in svn folder</li>
     * <li>do not recurse in cvs folder</li>
     * <li>do not match .bak files</li>
     * <li>do not match .old files</li>
     * </ul>
     *
     * @param folder       the folder to parse
     * @param ioFileFilter additionnal IOFilter
     */
    @SuppressWarnings("unchecked")
    public Collection<String> listFiles(File folder, IOFileFilter ioFileFilter) {
        if (ioFileFilter == null) {
            ioFileFilter = FileFilterUtils.fileFileFilter();
        }
        OrFileFilter oldFilesFilter = new OrFileFilter();
        for (String exclude : DEFAULT_EXCLUDES_SUFFIXES) {
            oldFilesFilter.addFileFilter(FileFilterUtils.suffixFileFilter(exclude));
        }
        IOFileFilter notOldFilesFilter = FileFilterUtils.notFileFilter(oldFilesFilter);

        Collection<File> files = FileUtils.listFiles(folder, FileFilterUtils.andFileFilter(ioFileFilter, notOldFilesFilter),
                FileFilterUtils.makeSVNAware(FileFilterUtils.makeCVSAware(null)));
        Collection<String> ret = newArrayList();
        for (File file : files) {
            ret.add(file.getAbsolutePath());
        }
        return ret;
    }

    /**
     * Recurse in the folder to get the list all files and folders of all non svn files
     *
     * @param folder the folder to parse
     */
    @SuppressWarnings("unchecked")
    public Collection<String> listFolders(File folder) {
        IOFileFilter ioFileFilter = FileFilterUtils.makeSVNAware(FileFilterUtils.makeCVSAware(FileFilterUtils.trueFileFilter()));
        Collection<File> files = FileUtils.listFiles(folder, FileFilterUtils.fileFileFilter(), ioFileFilter);
        Set<String> ret = newTreeSet();
        for (File file : files) {
            ret.add(file.getParentFile().getAbsolutePath());
        }
        return ret;
    }

    /**
     * return the temp folder
     */
    public String getTempFolder() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * given a file create the folder structure
     */
    public void mkdirs(String filename) {
        mkdirs(new File(filename));
    }

    /**
     * given a file create the folder structure
     */
    public void mkdirs(File file) {
        // create the parent folder if needed
        try {
            file.getParentFile().mkdirs();
        } catch (Exception _) {
            //
        }
    }

    /**
     * force the deletion of a file
     */
    public void forceDelete(String filename) {
        forceDelete(new File(filename));
    }

    /**
     * force the deletion of a file
     */
    public void forceDelete(File tempFile) {
        try {
            if (tempFile != null && tempFile.exists()) {
                FileUtils.forceDelete(tempFile);
            }
        } catch (Exception e) {
            log.warn("Could not delete " + tempFile, e);
        }
    }

    public boolean fileExists(String filename) {
        try {
            return new File(filename).exists();
        } catch (Exception _) {
            return false;
        }
    }

    public void forceMove(File from, File to) {
        try {
            forceDelete(to);
            FileUtils.moveFile(from, to);
        } catch (Exception e) {
            log.warn("Could not move " + from + " to " + to, e);
        }
    }
}
