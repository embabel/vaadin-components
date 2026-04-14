/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.vaadin.component;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reusable form for defining an action's context: name, description,
 * input/output types, tools, and skill references.
 * <p>
 * This is the building block for the action compiler, cron scheduler,
 * and webhook configuration UIs. Callers compose this form with their
 * own extras (compile button, cron schedule, webhook source, etc.).
 * <p>
 * Maps directly to a {@code StepSpecContext} and the fields of a
 * {@code PromptedActionSpec}.
 */
public class ActionContextForm extends VerticalLayout {

    public record AvailableTool(String name, String description) {}
    public record AvailableReference(String name, String description) {}
    public record AvailableType(String name) {}

    /**
     * Snapshot of the current form values.
     */
    public record ActionContext(
            String name,
            String description,
            Set<String> inputTypes,
            String outputType,
            List<String> toolNames,
            List<String> referenceNames
    ) {}

    private final TextField nameField;
    private final TextArea descriptionField;
    private final MultiSelectComboBox<String> inputTypes;
    private final ComboBox<String> outputType;
    private final ItemPickerField toolPicker;
    private final ItemPickerField refPicker;

    public ActionContextForm(
            List<AvailableTool> availableTools,
            List<AvailableReference> availableReferences,
            List<AvailableType> availableTypes
    ) {
        setPadding(false);
        setSpacing(true);

        // Action name
        nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setPlaceholder("e.g., summarize-order");
        add(nameField);

        // Description
        descriptionField = new TextArea("What should this action do?");
        descriptionField.setWidthFull();
        descriptionField.addClassName("action-description-field");
        descriptionField.setPlaceholder("e.g., Summarize the order details and extract key fields.\n\nBe as detailed as you like — the more context, the better the generated action.");
        add(descriptionField);

        // Input types
        inputTypes = new MultiSelectComboBox<>("Input types");
        inputTypes.setItems(availableTypes.stream().map(AvailableType::name).toList());
        inputTypes.setWidthFull();
        inputTypes.setPlaceholder("Select input types...");
        add(inputTypes);

        // Output type
        outputType = new ComboBox<>("Output type");
        outputType.setItems(availableTypes.stream().map(AvailableType::name).toList());
        outputType.setClearButtonVisible(true);
        outputType.setPlaceholder("Free text response");
        outputType.setWidthFull();
        add(outputType);

        // Tools
        if (!availableTools.isEmpty()) {
            var toolOptions = availableTools.stream()
                    .map(t -> new ItemPickerField.Item(t.name(), t.description()))
                    .collect(Collectors.toList());
            toolPicker = new ItemPickerField("Tools", toolOptions);
            toolPicker.addClassName("action-context-tools");
            add(toolPicker);
        } else {
            toolPicker = null;
        }

        // References (skills)
        if (!availableReferences.isEmpty()) {
            var refOptions = availableReferences.stream()
                    .map(r -> new ItemPickerField.Item(r.name(), r.description()))
                    .collect(Collectors.toList());
            refPicker = new ItemPickerField("Skills", refOptions);
            refPicker.addClassName("action-context-refs");
            add(refPicker);
        } else {
            refPicker = null;
        }
    }

    /**
     * Get a snapshot of the current form values.
     */
    public ActionContext getValue() {
        return new ActionContext(
                nameField.getValue().trim(),
                descriptionField.getValue().trim(),
                inputTypes.getValue(),
                outputType.getValue(),
                toolPicker != null ? toolPicker.getValue().stream().toList() : List.of(),
                refPicker != null ? refPicker.getValue().stream().toList() : List.of()
        );
    }

    /**
     * Validate the form. Returns null if valid, or an error message.
     */
    public String validate() {
        if (nameField.getValue().isBlank()) return "Action name is required.";
        if (descriptionField.getValue().isBlank()) return "Description is required.";
        return null;
    }

    /**
     * Clear all form fields.
     */
    public void clear() {
        nameField.clear();
        descriptionField.clear();
        inputTypes.clear();
        outputType.clear();
        if (toolPicker != null) toolPicker.clear();
        if (refPicker != null) refPicker.clear();
    }

    public String getActionName() {
        return nameField.getValue().trim();
    }

    public String getDescription() {
        return descriptionField.getValue().trim();
    }
}
