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

import com.objective.threesixty.Field;
import com.objective.threesixty.FormConfigRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileSystemConnectorFormTest {

    @Test
    void getSourceRepositoryFields() {
        FileSystemConnectorForm fileSystemConnectorForm = new FileSystemConnectorForm();
        List<Field> fields = fileSystemConnectorForm.getSourceRepositoryFields();
        FormConfigRequest formConfig = FormConfigRequest.newBuilder().addAllRepoFields(fields).build();

        assertNotNull(fields);
        assertNotNull(formConfig);
        assertNotEquals(0, formConfig.getRepoFieldsList().size());
        Field field = formConfig.getRepoFields(0);
        assertEquals("File Path", field.getLabel());
        assertEquals("filePath", field.getId());
    }

    @Test
    void getOutputRepositoryFields() {
        FileSystemConnectorForm fileSystemConnectorForm = new FileSystemConnectorForm();
        List<Field> fields = fileSystemConnectorForm.getOutputRepositoryFields();
        FormConfigRequest formConfig = FormConfigRequest.newBuilder().addAllOutputFields(fields).build();

        assertNotNull(fields);
        assertNotNull(formConfig);
        assertNotEquals(0, formConfig.getOutputFieldsList().size());
        Field field = formConfig.getOutputFields(0);
        assertEquals("Output File Path", field.getLabel());
        assertEquals("filePath", field.getId());
    }

    @Test
    void getContentServiceConnectorFields() {
        FileSystemConnectorForm fileSystemConnectorForm = new FileSystemConnectorForm();
        List<Field> fields = fileSystemConnectorForm.getContentServiceFields();
        FormConfigRequest formConfig = FormConfigRequest.newBuilder().addAllContentServiceFields(fields).build();

        assertNotNull(fields);
        assertNotNull(formConfig);
        assertNotEquals(0, formConfig.getContentServiceFieldsList().size());
        Field field = formConfig.getContentServiceFields(0);
        assertEquals("toggleCheckbox", field.getId());
    }
}
