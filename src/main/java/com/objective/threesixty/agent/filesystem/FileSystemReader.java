package com.objective.threesixty.agent.filesystem;

/*-
 * %%
 * 3Sixty Remote Agent Example
 * -
 * Copyright (C) 2024 - 2025 Objective Corporation Limited.
 * -
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * %-
 */

import com.google.common.annotations.VisibleForTesting;
import com.objective.threesixty.Document;
import com.objective.threesixty.MetadataType;
import com.objective.threesixty.remoteagent.sdk.BinaryDetails;
import com.objective.threesixty.remoteagent.sdk.agent.RepositoryReader;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import com.objective.threesixty.remoteagent.sdk.utils.RepositoryUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class FileSystemReader implements RepositoryReader {
    //Not needed for this implementation
    @Override
    public void init(CustomParameters parameters) {
    }

    @Override
    public Document getDocument(String docId, CustomParameters parameters) {
        return documentFromPath(Paths.get(docId), parameters);
    }

    @Override
    public Map<String, MetadataType> getDocumentMetadata(String docId, CustomParameters parameters) throws IOException {
        Path path = Paths.get(docId);
        Map<String, MetadataType> metadata = new ConcurrentHashMap<>();
        metadata.put("fileName", MetadataType.newBuilder().setString(path.getFileName().toString()).build());
        metadata.put("fileSize", MetadataType.newBuilder().setLong(Files.size(path)).build());
        return metadata;
    }

    @Override
    public Stream<Document> getDocuments(CustomParameters parameters) throws Exception {
        String filePath = parameters.get("filePath").getString();
        Path directory = Paths.get(filePath);

        if (!Files.isDirectory(directory)) {
            return Stream.of(documentFromPath(directory, parameters)).filter(Objects::nonNull);
        }

        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory);
        Stream<Path> pathStream = StreamSupport.stream(directoryStream.spliterator(), false);

        return pathStream
            .onClose(() -> { // close the directoryStream when all the paths have been processed
                try {
                    directoryStream.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .map((path -> documentFromPath(path, parameters)))
            .filter(Objects::nonNull);
    }

    @Override
    public BinaryDetails getDocumentBinary(String docId, CustomParameters parameters) {
        Path path = Paths.get(docId);
        BinaryDetails bd = new BinaryDetails(docId, InputStream.nullInputStream(), RepositoryUtils.getMimeTypeForFileName(path.getFileName().toString()));
        try {
            bd.setInputStream(getFileInputStream(docId));
        } catch (FileNotFoundException e) {
            bd.setInputStream(InputStream.nullInputStream());
            getLogger().error("Error accessing directory " + docId + " when getting binary details. Setting InputStream to nullInputStream.", e);
        }
        return bd;
    }

    @Override
    public void deleteDocument(String docId, CustomParameters parameters) throws Exception {
        getLogger().info("Attempting to delete " + docId);
        //deleteAllVersions() reference to the allVersions parameter in 3Sixty's delete document REST API method.
        //Not very useful in a file system scenario, but it is always present for this method.
        getLogger().debug("Delete All Versions: " + parameters.deleteAllVersions());
        File document = new File(docId);

        try {
            Files.delete(document.toPath());
            getLogger().debug("Deleted " + docId);
        } catch (Exception e) {
            getLogger().error("Could not delete " + docId + ":\n" + e.getMessage(), e);
            throw e;
        }
    }

    private Document documentFromPath(Path path, CustomParameters parameters) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!inRange(attributes.lastModifiedTime().toMillis(), parameters)) {
            return null;
        }

        // remove drive letter from parent path. specific to Windows FileSystem implementations
        String root = Objects.toString(path.getRoot(), "");
        String parentPath = Objects.toString(path.getParent(), "");
        parentPath = parentPath.replace(root, "");

        if (!parentPath.startsWith(File.separator)) {
            parentPath = File.separator + parentPath;
        }

        String docId = path.toString();

        return Document.newBuilder()
            .setId(docId)
            .setName(path.getFileName().toString())
            .setCreatedDate(RepositoryUtils.fromInstant(attributes.creationTime().toInstant()))
            .setModifiedDate(RepositoryUtils.fromInstant(attributes.creationTime().toInstant()))
            .setMimeType(RepositoryUtils.getMimeTypeForFileName(path.getFileName().toString()))
            .setSize(attributes.size())
            .setParentPath(parentPath)
            .build();
    }

    private boolean inRange(long lastModifiedTime, CustomParameters parameters) {
        return lastModifiedTime >= parameters.getStartTimeOfDateFilter() && lastModifiedTime <= parameters.getEndTimeOfDateFilter();
    }

    @VisibleForTesting
    InputStream getFileInputStream(String docId) throws FileNotFoundException {
        return new FileInputStream(Path.of(docId).toFile());
    }
}
