package com.objective.threesixty.agent.filesystem;

/*-
 * %%
 * 3Sixty Remote Agent Example
 * -
 * Copyright (C) 2024 Objective Corporation Limited.
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
import com.objective.threesixty.remoteagent.sdk.agent.Reader;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import com.objective.threesixty.remoteagent.sdk.utils.ReaderUtils;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class FileSystemReader implements Reader {
    private final Map<String, Map<String, Map<String, MetadataType>>> docMetadataByPath = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BinaryDetails>> docBinaryByPath = new ConcurrentHashMap<>();

    //Not needed for this implementation
    @Override
    public void init(CustomParameters parameters) {
    }

    @Override
    public Map<String, MetadataType> getDocumentMetadata(String docId, CustomParameters parameters) {
        String filePath = parameters.get("filePath").getString();
        return docMetadataByPath.get(filePath).remove(docId);
    }

    @Override
    public Stream<Document> getDocuments(CustomParameters parameters) throws Exception {
        String filePath = parameters.get("filePath").getString();
        Path directory = Paths.get(filePath);

        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory);
        Stream<Path> pathStream = StreamSupport.stream(directoryStream.spliterator(), false);

        return pathStream
                .onClose(() -> { // close the directoryStream when all the paths have been processed
                    try {
                        directoryStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(path -> {
                    BasicFileAttributes attributes;
                    try {
                        attributes = Files.readAttributes(path, BasicFileAttributes.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // remove drive letter from parent path. specific to Windows FileSystem implementations
                    String root = null != path.getRoot() ? path.getRoot().toString() : "";
                    String parentPath = null != path.getParent() ? path.getParent().toString() : "";
                    parentPath = parentPath.replace(root, "");
                    if (!parentPath.startsWith("\\")) {
                        parentPath = "\\" + parentPath;
                    }

                    Document doc = Document.newBuilder()
                            .setId(path.toString())
                            .setName(path.getFileName().toString())
                            .setCreatedDate(ReaderUtils.fromInstant(attributes.creationTime().toInstant()))
                            .setModifiedDate(ReaderUtils.fromInstant(attributes.creationTime().toInstant()))
                            .setMimeType(ReaderUtils.getMimeTypeForFileName(path.getFileName().toString()))
                            .setSize(attributes.size())
                            .setParentPath(parentPath)
                            .build();
                    try {
                        if (parameters.getIncludeBinaries()) {
                            generateBinaryMap(filePath, path);
                        }

                        generateMetadataMap(filePath, path);
                    } catch (Exception e) {
                        getLogger().error("Error accessing directory", e);
                    }

                    return doc;
                });
    }

    @Override
    public BinaryDetails getDocumentBinary(String docId, CustomParameters parameters) {
        String filePath = parameters.get("filePath").getString();
        BinaryDetails bd = docBinaryByPath.get(filePath).remove(docId);

        try {
            bd.setInputStream(getFileInputStream(bd.getDocumentId()));
        } catch (FileNotFoundException e) {
            bd.setInputStream(InputStream.nullInputStream());
            getLogger().error("Error accessing directory " + docId + " when getting binary details. Setting InputStream to nullInputStream.", e);
        }

        return bd;
    }

    @VisibleForTesting
    InputStream getFileInputStream(String docId) throws FileNotFoundException {
        return new FileInputStream(Path.of(docId).toFile());
    }

    private void generateMetadataMap(String filePath, Path file) throws IOException {
        Map<String, MetadataType> metadata = new ConcurrentHashMap<>();
        metadata.put("fileName", MetadataType.newBuilder().setString(file.getFileName().toString()).build());
        metadata.put("fileSize", MetadataType.newBuilder().setLong(Files.size(file)).build());

        Map<String, Map<String, MetadataType>> metadataByDocId = docMetadataByPath.computeIfAbsent(filePath, v -> new ConcurrentHashMap<>());
        metadataByDocId.put(file.toString(), metadata);
    }

    private void generateBinaryMap(String filePath, Path path) {
        BinaryDetails binaryDetails = new BinaryDetails(path.toString(), InputStream.nullInputStream(), ReaderUtils.getMimeTypeForFileName(path.getFileName().toString()));
        Map<String, BinaryDetails> binaryDetailsByDocId = docBinaryByPath.computeIfAbsent(filePath, v -> new ConcurrentHashMap<>());
        binaryDetailsByDocId.put(path.toString(), binaryDetails);
    }
}
