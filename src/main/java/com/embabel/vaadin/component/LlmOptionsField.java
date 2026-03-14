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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.List;

/**
 * Reusable component for selecting an LLM and configuring temperature.
 * <p>
 * Provides a model dropdown populated with available LLM names from the platform,
 * and a temperature control defaulting to 0.0.
 * <p>
 * The value is exposed as an {@link LlmSelection} record.
 */
public class LlmOptionsField extends VerticalLayout {

    /**
     * The selected LLM configuration.
     *
     * @param model    the selected model name, or null for platform default
     * @param temperature the temperature setting (0.0 = deterministic)
     */
    public record LlmSelection(String model, double temperature) {}

    private final ComboBox<String> modelCombo;
    private final NumberField temperatureField;

    /**
     * Create an LLM options field.
     *
     * @param availableModels list of available LLM model names from the platform
     * @param defaultModel    the platform's default model name (pre-selected)
     */
    public LlmOptionsField(List<String> availableModels, String defaultModel) {
        setPadding(false);
        setSpacing(false);

        var label = new Span("LLM");
        label.addClassName("llm-options-label");

        modelCombo = new ComboBox<>();
        modelCombo.setItems(availableModels);
        modelCombo.setPlaceholder("Platform default");
        modelCombo.setClearButtonVisible(true);
        modelCombo.setWidth("220px");
        if (defaultModel != null && availableModels.contains(defaultModel)) {
            modelCombo.setValue(defaultModel);
        }

        temperatureField = new NumberField();
        temperatureField.setLabel("Temperature");
        temperatureField.setValue(0.0);
        temperatureField.setMin(0.0);
        temperatureField.setMax(2.0);
        temperatureField.setStep(0.1);
        temperatureField.setWidth("110px");
        temperatureField.setStepButtonsVisible(true);

        var row = new HorizontalLayout(modelCombo, temperatureField);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setSpacing(true);
        row.setPadding(false);

        add(label, row);
    }

    /**
     * Get the current LLM selection.
     */
    public LlmSelection getValue() {
        var model = modelCombo.getValue();
        var temp = temperatureField.getValue();
        return new LlmSelection(model, temp != null ? temp : 0.0);
    }

    /**
     * Get the selected model name, or null for platform default.
     */
    public String getModel() {
        return modelCombo.getValue();
    }

    /**
     * Get the temperature setting.
     */
    public double getTemperature() {
        var v = temperatureField.getValue();
        return v != null ? v : 0.0;
    }

    /**
     * Clear the selection back to defaults.
     */
    public void clear() {
        modelCombo.clear();
        temperatureField.setValue(0.0);
    }
}
