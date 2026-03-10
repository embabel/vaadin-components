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

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Function;

/**
 * A two-field cron expression editor with bidirectional compilation
 * and validation.
 * <p>
 * The top field accepts natural language (e.g., "Every day at 7 am")
 * and the bottom field holds the compiled cron expression (e.g., "0 0 7 * * *").
 * A compile button converts natural language to cron; the cron field is
 * validated on every change.
 * <p>
 * The compiler function is called with a prompt string and returns the result.
 * This component is LLM-agnostic -- the caller provides the compilation logic
 * (typically backed by a {@code PromptRunner}).
 */
public class CronExpressionField extends VerticalLayout {

    private final TextField naturalField;
    private final TextField cronField;
    private final Function<String, String> validator;

    private static final String TO_CRON_PROMPT = """
            Convert the following natural language schedule description to a Spring 6-field cron expression (seconds minutes hours day-of-month month day-of-week).
            If the input is NOT a valid schedule description, return ONLY a line starting with "ERROR:" followed by a helpful explanation of what a schedule description should look like, with examples.
            If the input IS a valid schedule description, return ONLY the cron expression, nothing else. No explanation, no backticks.

            Input: %s""";

    private static final String TO_NATURAL_PROMPT = """
            Convert the following cron expression to a concise, human-readable description.
            Return ONLY the description, nothing else. No explanation, no backticks.

            Cron expression: %s""";

    /**
     * @param compiler  function that takes a prompt string and returns the LLM response.
     *                  Called on a background thread to avoid blocking the UI.
     * @param validator function that validates a cron expression. Returns {@code null}
     *                  if valid, or an error message if invalid. May be {@code null}
     *                  to skip validation.
     */
    public CronExpressionField(Function<String, String> compiler, Function<String, String> validator) {
        this.validator = validator;
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        naturalField = new TextField("Schedule");
        naturalField.setWidthFull();
        naturalField.setPlaceholder("e.g., Every day at 7 am");
        naturalField.setHelperText("Describe when this should run, then click \u25B6 to compile");

        var compileButton = new Button(VaadinIcon.PLAY.create());
        compileButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        compileButton.getElement().setAttribute("title", "Compile to cron expression");
        compileButton.addClickListener(e -> compileToCron(compiler, compileButton));
        naturalField.addKeyPressListener(Key.ENTER, e -> compileToCron(compiler, compileButton));

        var naturalRow = new HorizontalLayout(naturalField, compileButton);
        naturalRow.setWidthFull();
        naturalRow.setAlignItems(Alignment.BASELINE);
        naturalRow.setFlexGrow(1, naturalField);
        naturalRow.setPadding(false);

        cronField = new TextField("Cron expression");
        cronField.setWidthFull();
        cronField.setPlaceholder("e.g., 0 0 7 * * *");
        cronField.setHelperText("6 fields: seconds minutes hours day-of-month month day-of-week");

        // Validate on every value change
        cronField.addValueChangeListener(e -> validateCronField());

        add(naturalRow, cronField);
    }

    /**
     * Convenience constructor without a validator.
     */
    public CronExpressionField(Function<String, String> compiler) {
        this(compiler, null);
    }

    public String getCronValue() {
        return cronField.getValue();
    }

    public void setCronValue(String value) {
        cronField.setValue(value != null ? value : "");
    }

    public String getNaturalValue() {
        return naturalField.getValue();
    }

    public void setNaturalValue(String value) {
        naturalField.setValue(value != null ? value : "");
    }

    public void clear() {
        naturalField.clear();
        cronField.clear();
        cronField.setInvalid(false);
    }

    /**
     * Check whether the current cron value is valid (or empty).
     */
    public boolean isValid() {
        var cron = cronField.getValue();
        if (cron == null || cron.isBlank()) return true;
        if (validator == null) return true;
        return validator.apply(cron) == null;
    }

    private void validateCronField() {
        var cron = cronField.getValue();
        if (cron == null || cron.isBlank()) {
            cronField.setInvalid(false);
            return;
        }
        if (validator == null) return;

        var error = validator.apply(cron.trim());
        if (error != null) {
            cronField.setErrorMessage(error.replace("\n", " "));
            cronField.setInvalid(true);
        } else {
            cronField.setInvalid(false);
        }
    }

    private void compileToCron(Function<String, String> compiler, Button button) {
        var text = naturalField.getValue();
        if (text == null || text.isBlank()) return;

        var ui = getUI().orElse(null);
        if (ui == null) return;

        button.setEnabled(false);
        naturalField.setHelperText("Compiling...");
        Thread.startVirtualThread(() -> {
            try {
                var result = compiler.apply(String.format(TO_CRON_PROMPT, text.trim())).trim();
                ui.access(() -> {
                    if (result.startsWith("ERROR:")) {
                        var message = result.substring("ERROR:".length()).trim();
                        naturalField.setInvalid(true);
                        naturalField.setErrorMessage(message);
                    } else {
                        naturalField.setInvalid(false);
                        cronField.setValue(result); // triggers ValueChangeListener → validate
                    }
                    naturalField.setHelperText("Describe when this should run, then click \u25B6 to compile");
                    button.setEnabled(true);
                });
            } catch (Exception ex) {
                ui.access(() -> {
                    naturalField.setInvalid(true);
                    naturalField.setErrorMessage("Compilation failed: " + ex.getMessage());
                    naturalField.setHelperText("Describe when this should run, then click \u25B6 to compile");
                    button.setEnabled(true);
                });
            }
        });
    }
}
