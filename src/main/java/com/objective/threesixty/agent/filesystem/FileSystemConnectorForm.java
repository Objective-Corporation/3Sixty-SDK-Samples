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

import com.objective.threesixty.CheckboxField;
import com.objective.threesixty.Field;
import com.objective.threesixty.NumberField;
import com.objective.threesixty.TextField;
import com.objective.threesixty.remoteagent.sdk.agent.ConnectorForm;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileSystemConnectorForm implements ConnectorForm {
    @Override
    public List<Field> getSourceRepositoryFields() {
        // File Path
        Field filePath = Field.newBuilder()
            .setLabel("File Path")
            .setId("filePath")
            .setTextField(TextField.newBuilder().build())
            .build();

        return List.of(filePath);
    }

    @Override
    public List<Field> getOutputRepositoryFields() {
        // File Path
        Field filePath = Field.newBuilder()
            .setLabel("Output File Path")
            .setId("filePath")
            .setTextField(TextField.newBuilder().build())
            .build();
        Field metadataAsXml = Field.newBuilder()
            .setLabel("Output Metadata as XML")
            .setId("metadataAsXml")
            .setCheckboxField(CheckboxField.newBuilder().setValue(true).build())
            .build();
        return List.of(filePath, metadataAsXml);
    }

    @Override
    public List<Field> getContentServiceFields() {
        Field toggleCheckbox = Field.newBuilder()
            .setLabel("Show/Hide Text field")
            .setDescription("Toggles Text field")
            .setId("toggleCheckbox")
            .setCheckboxField(CheckboxField.newBuilder().setValue(false).build())
            .build();
        Field textField = Field.newBuilder()
            .setLabel("Text field")
            .setId("textField")
            .setDependsOn(toggleCheckbox.getId())
            .setTextField(TextField.newBuilder().build())
            .build();
        Field numberField = Field.newBuilder()
            .setLabel("Number field")
            .setId("numberField")
            .setNumberField(NumberField.newBuilder().build())
            .setDependsOn("noMatchingId")
            .build();
        return List.of(toggleCheckbox, textField, numberField);
    }
}
