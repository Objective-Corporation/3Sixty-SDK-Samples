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

import com.objective.threesixty.MetadataType;
import com.objective.threesixty.Value;
import com.objective.threesixty.remoteagent.sdk.BinaryDetails;
import com.objective.threesixty.remoteagent.sdk.utils.CustomParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class FileSystemReaderTest {
    CustomParameters customParameters;

    @BeforeEach
    void setup() {
        customParameters = new CustomParameters(new HashMap<>());
        customParameters.put("filePath", Value.newBuilder().setString("filePath").build());
    }

    @Test
    @DisplayName("Test metadata is returned")
    void getDocumentMetadata() {
        FileSystemReader fileSystemReader = new FileSystemReader();
        String docId = "1";
        Map<String, MetadataType> docMetadata = new HashMap<>();
        Map<String, Map<String, MetadataType>> filePathMap = new HashMap<>();
        filePathMap.put(docId, docMetadata);

        Map<String, Map<String, Map<String, MetadataType>>> docMetadataByPath = new HashMap<>();
        docMetadataByPath.put("filePath", filePathMap);
        ReflectionTestUtils.setField(fileSystemReader, "docMetadataByPath", docMetadataByPath);

        Map<String, MetadataType> actualMetadata = fileSystemReader.getDocumentMetadata(docId, customParameters);
        assertEquals(docMetadata, actualMetadata);
        assertFalse(filePathMap.containsKey(docId));
    }

    @Test
    @DisplayName("Test binary is returned")
    void getDocumentBinary() throws FileNotFoundException {
        FileSystemReader fileSystemReader = spy(new FileSystemReader());
        String docId = "myDocId";

        BinaryDetails fakeBinaryDetails = new BinaryDetails(docId, null, "fakeType");
        Map<String, BinaryDetails> binaryDetailsMap = new HashMap<>();
        binaryDetailsMap.put(docId, fakeBinaryDetails);
        Map<String, Map<String, BinaryDetails>> docBinaryByPath = new HashMap<>();
        docBinaryByPath.put("filePath", binaryDetailsMap);
        ReflectionTestUtils.setField(fileSystemReader, "docBinaryByPath", docBinaryByPath);

        byte[] data = "file".getBytes();
        InputStream expectedInputStream = new ByteArrayInputStream(data);
        doReturn(expectedInputStream).when(fileSystemReader).getFileInputStream(docId);
        BinaryDetails actualBinaryDetails = fileSystemReader.getDocumentBinary(docId, customParameters);

        assertNotNull(actualBinaryDetails);
        assertEquals(docId, actualBinaryDetails.getDocumentId());
        assertNotNull(actualBinaryDetails.getInputStream());
        assertEquals(expectedInputStream, actualBinaryDetails.getInputStream(), "Input stream should be not be null");
    }
}
