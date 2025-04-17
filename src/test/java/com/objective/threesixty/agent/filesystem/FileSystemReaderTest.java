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

import com.objective.threesixty.Document;
import com.objective.threesixty.MetadataType;
import com.objective.threesixty.Value;
import com.objective.threesixty.remoteagent.sdk.BinaryDetails;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import com.objective.threesixty.remoteagent.sdk.utils.ReservedIdentifier;
import com.objective.threesixty.remoteagent.sdk.utils.ValueUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

class FileSystemReaderTest {
    CustomParameters customParameters;
    FileSystemReader fileSystemReader;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        fileSystemReader = new FileSystemReader();
        customParameters = new CustomParameters(new HashMap<>());
    }

    @Test
    void testInit_exists() {
        assertDoesNotThrow(() -> fileSystemReader.init(customParameters));
    }

    @Test
    void testGetDocument() {
        File file = loadTestDoc();
        String docId = file.getPath();

        Document doc = fileSystemReader.getDocument(docId, customParameters);
        assertNotNull(doc);
        assertEquals(docId, doc.getId());
        assertEquals(file.getName(), doc.getName());
        assertEquals(TEXT_PLAIN_VALUE, doc.getMimeType());
        assertEquals(file.length(), doc.getSize());
    }

    @Test
    void testGetDocuments_directory() {
        File file = loadTestDoc();
        String docId = file.getPath();
        String filePath = file.getParent();
        customParameters.put("filePath", Value.newBuilder().setString(filePath).build());

        try {
            Stream<Document> docs = fileSystemReader.getDocuments(customParameters);
            List<Document> docsList = docs.toList();
            assertNotNull(docsList);
            assertEquals(1, docsList.size());
            Document doc = docsList.get(0);
            assertNotNull(doc);
            assertEquals(docId, doc.getId());
            assertEquals(file.getName(), doc.getName());
            assertEquals(TEXT_PLAIN_VALUE, doc.getMimeType());
            assertEquals(file.length(), doc.getSize());
        } catch (Exception e) {
            fail("Should not throw an exception", e);
        }
    }

    @Test
    void testGetDocuments_singleFile() {
        File file = loadTestDoc();
        String docId = file.getPath();
        customParameters.put("filePath", Value.newBuilder().setString(docId).build());

        try {
            Stream<Document> docs = fileSystemReader.getDocuments(customParameters);
            List<Document> docsList = docs.toList();
            assertNotNull(docsList);
            assertEquals(1, docsList.size());
            Document doc = docsList.get(0);
            assertNotNull(doc);
            assertEquals(docId, doc.getId());
            assertEquals(file.getName(), doc.getName());
            assertEquals(TEXT_PLAIN_VALUE, doc.getMimeType());
            assertEquals(file.length(), doc.getSize());
        } catch (Exception e) {
            fail("Should not throw an exception", e);
        }
    }

    @Test
    void testGetDocuments_singleFile_outside_date_range() {
        File file = loadTestDoc();
        String docId = file.getPath();
        customParameters.put("filePath", Value.newBuilder().setString(docId).build());
        // Mon Jul 05 2021 17:42:40.301
        customParameters.put(ReservedIdentifier.START_TIME.getName(), Value.newBuilder().setLong(1625506960301L).build());

        // Fri Oct 29 2021 11:29:20.301
        customParameters.put(ReservedIdentifier.END_TIME.getName(), Value.newBuilder().setLong(1635506960301L).build());

        try {
            Stream<Document> docs = fileSystemReader.getDocuments(customParameters);
            List<Document> docsList = docs.toList();
            assertNotNull(docsList);
            assertEquals(0, docsList.size());
        } catch (Exception e) {
            fail("Should not throw an exception", e);
        }
    }

    @Test
    void testGetDocumentMetadata() {
        File file = loadTestDoc();
        String docId = file.getPath();

        try {
            Map<String, MetadataType> actualMetadata = fileSystemReader.getDocumentMetadata(docId, customParameters);
            assertEquals("TestDoc.txt", actualMetadata.get("fileName").getString());
            assertEquals(5, actualMetadata.get("fileSize").getLong());
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    void testGetDocumentBinary_success() {
        File file = loadTestDoc();
        String docId = file.getPath();

        try {
            BinaryDetails bd = fileSystemReader.getDocumentBinary(docId, customParameters);
            assertEquals(docId, bd.getDocumentId());
            assertEquals(TEXT_PLAIN_VALUE, bd.getMimeType());
            assertEquals(5, bd.getInputStream().readAllBytes().length);
            bd.getInputStream().close();
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    void testGetDocumentBinary_fileNotFound() {
        String docId = "nonExistentFile.txt";

        try {
            BinaryDetails bd = fileSystemReader.getDocumentBinary(docId, customParameters);
            assertEquals(docId, bd.getDocumentId());
            assertEquals(TEXT_PLAIN_VALUE, bd.getMimeType());
            assertEquals(0, bd.getInputStream().readAllBytes().length);
            bd.getInputStream().close();
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    void testDeleteDocument_allVersionsFalse() {
        testDeleteDocument(false);
    }

    @Test
    void testDeleteDocument_allVersionsTrue() {
        testDeleteDocument(true);
    }

    @Test
    void testDeleteDocument_failsWith_NoSuchFileException() {
        String docId = "nonExistentFile.txt";

        Exception exception = assertThrows(NoSuchFileException.class, () -> fileSystemReader.deleteDocument(docId, customParameters));
        assertTrue(exception.getMessage().contains("nonExistentFile.txt"));
    }

    @Test
    void testDeleteDocument_failsWith_DirectoryNotEmptyException() throws IOException {
        Path tempFile = tempDir.resolve("nonEmptyDirectory");
        Files.createDirectory(tempFile);

        Files.createFile(tempFile.resolve("fileInsideDirectory.txt"));
        String docId = tempFile.toString();

        Exception exception = assertThrows(DirectoryNotEmptyException.class, () -> fileSystemReader.deleteDocument(docId, customParameters));
        assertTrue(exception.getMessage().contains("nonEmptyDirectory"));
    }

    @Test
    void testDeleteDocument_failsWith_IOException() throws IOException {
        Path tempFile = tempDir.resolve("fileInUse.txt");
        Files.createFile(tempFile);
        String docId = tempFile.toString();

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.delete(Paths.get(docId)))
                .thenThrow(new IOException("Fake file in use exception"));

            Exception exception = assertThrows(IOException.class, () -> fileSystemReader.deleteDocument(docId, customParameters));
            assertTrue(exception.getMessage().contains("Fake file in use exception"));
        }
    }

    @Test
    void testDeleteDocument_failsWith_SecurityException() throws IOException {
        Path tempFile = tempDir.resolve("restrictedFile.txt");
        Files.createFile(tempFile);
        String docId = tempFile.toString();

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.delete(Paths.get(docId)))
                .thenThrow(new SecurityException("Delete operation not allowed"));

            Exception exception = assertThrows(SecurityException.class, () -> fileSystemReader.deleteDocument(docId, customParameters));
            assertTrue(exception.getMessage().contains("Delete operation not allowed"));
        }
    }

    private void testDeleteDocument(boolean allVersions) {
        try {
            Path tempFile = Files.createFile(tempDir.resolve("toDelete.txt"));
            assertTrue(Files.exists(tempFile));
            String docId = tempFile.toFile().getPath();

            customParameters.put(ReservedIdentifier.ALL_VERSIONS_PARAM.getName(), ValueUtils.booleanValue(allVersions));
            fileSystemReader.deleteDocument(docId, customParameters);

            assertFalse(Files.exists(tempFile));
        } catch (Exception e) {
            fail("Should not throw an exception", e);
        }
    }

    private File loadTestDoc() {
        String fileName = "TestDoc.txt";
        URL fileAsUrl = getClass().getClassLoader().getResource("testFiles/" + fileName);
        assertNotNull(fileAsUrl, "Could not find file in resources folder: " + fileName);
        return new File(fileAsUrl.getFile());
    }
}
