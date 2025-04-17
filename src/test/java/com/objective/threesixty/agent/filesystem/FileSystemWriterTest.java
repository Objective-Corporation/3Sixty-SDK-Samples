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

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.objective.threesixty.Document;
import com.objective.threesixty.MetadataType;
import com.objective.threesixty.StringArray;
import com.objective.threesixty.Value;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileSystemWriterTest {
    FileSystemWriter writer;
    CustomParameters customParameters;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        writer = new FileSystemWriter();
        Map<String, Value> valueMap = new HashMap<>();
        Path sourcePath = tempDir.resolve("sourceDir");
        Files.createDirectory(sourcePath);

        valueMap.put("filePath", Value.newBuilder().setId("filePath").setString(sourcePath.getParent().toString()).build());
        customParameters = new CustomParameters(valueMap);
    }

    @Test
    void testWriteDocument_success() throws IOException {
        Flux<DataBuffer> dataBufferFlux = Flux.just(
            new DefaultDataBufferFactory().wrap("abc".getBytes(StandardCharsets.UTF_8))
        );
        Document sourceDoc = createDocument();
        Document result = writer.writeDocument(sourceDoc, createMetadata(), dataBufferFlux, customParameters).block();
        String newPath = Paths.get(
            customParameters.get("filePath").getString(),
            writer.sanitizePath(sourceDoc.getParentPath())
        ).toString();
        assertEquals(sourceDoc.toBuilder()
                .setId(newPath + File.separator + sourceDoc.getName())
                .setParentPath(newPath)
                .build(),
            result);
    }

    @Test
    void extractMetadataStringValue() {
        MetadataType.Builder metadata = MetadataType.newBuilder();

        metadata.setArray(StringArray.newBuilder()
            .addValues("val1")
            .addValues("val2"));
        assertEquals("values: \"val1\"\nvalues: \"val2\"", writer.extractMetadataStringValue("arrayKey", metadata.build()).trim());

        metadata.clear();
        ByteString bs = ByteString.fromHex("ac89");
        metadata.setBinary(bs);
        assertEquals("ac89", writer.extractMetadataStringValue("binaryKey", metadata.build()));

        metadata.clear();
        metadata.setBoolean(true);
        assertEquals("true", writer.extractMetadataStringValue("booleanKey", metadata.build()));

        metadata.clear();
        metadata.setDouble(123.456);
        assertEquals("123.456", writer.extractMetadataStringValue("doubleKey", metadata.build()));

        metadata.clear();
        metadata.setDecimal(789.01f);
        assertEquals("789.01", writer.extractMetadataStringValue("decimalKey", metadata.build()));

        metadata.clear();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1633046400).setNanos(0).build();
        metadata.setDateTime(timestamp);
        assertEquals("2021-10-01T00:00:00Z", writer.extractMetadataStringValue("dateTimeKey", metadata.build()));

        metadata.clear();
        metadata.setInteger(42);
        assertEquals("42", writer.extractMetadataStringValue("integerKey", metadata.build()));

        metadata.clear();
        metadata.setLargeString("This is a large string");
        assertEquals("This is a large string", writer.extractMetadataStringValue("largeStringKey", metadata.build()));

        metadata.clear();
        metadata.setLong(123456789L);
        assertEquals("123456789", writer.extractMetadataStringValue("longKey", metadata.build()));

        metadata.clear();
        metadata.setString("simpleString");
        assertEquals("simpleString", writer.extractMetadataStringValue("stringKey", metadata.build()));

        metadata.clear();
        assertNull(writer.extractMetadataStringValue("emptyKey", metadata.build()));
    }

    @Test
    void sanitizePath() {
        assertEquals("a/b/c", writer.sanitizePath("a/b/c/"));
        assertEquals("a/b/c", writer.sanitizePath("a:b:c"));
        assertEquals("a/b/c/d.jpg", writer.sanitizePath("a:b/c/d.jpg/"));
        assertEquals("a/b/c", writer.sanitizePath("a/b/c"));
        assertEquals("", writer.sanitizePath(""));
        assertEquals("", writer.sanitizePath(":////"));
    }

    private Document createDocument() throws IOException {
        Path targetPath = tempDir.resolve("targetDir");
        Files.createDirectory(targetPath);

        return Document.newBuilder()
            .setId("fileId")
            .setSize(3)
            .setParentPath(targetPath.getParent().toString())
            .setName("someFile.txt")
            .setMimeType(MediaType.TEXT_PLAIN_VALUE)
            .build();
    }

    private Map<String, MetadataType> createMetadata() {
        Map<String, MetadataType> metadata = new HashMap<>();
        metadata.put("fileNumber", MetadataType.newBuilder().setInteger(5).build());
        metadata.put("fileCreator", MetadataType.newBuilder().setString("user1").build());
        return metadata;
    }
}
