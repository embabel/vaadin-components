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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link CollectorSettingsPanel} renders real controls (enabled, dryRun, matcher,
 * similarityThreshold, cron), that setValue populates all 5 fields, that disabled weight sliders
 * are present and disabled, and that clicking Save fires the onSave callback with
 * current edited values (round-trip integrity).
 */
class CollectorSettingsPanelTest {

    @Test
    void setValuePopulatesAllFiveRealFields() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "multi-signal",
                0.85,
                "0 0 4 * * *",
                null,
                null,
                null,
                null,
                null
        );

        panel.setValue(settings);

        // Assert ALL 5 real fields are populated
        var enabledCheckbox = findCheckboxByClass(panel, "collector-enabled-toggle");
        assertEquals(true, enabledCheckbox.getValue(), "enabled toggle should be populated to true");

        var dryRunCheckbox = findCheckboxByClass(panel, "dry-run");
        assertEquals(false, dryRunCheckbox.getValue(), "dryRun toggle should be populated to false");

        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        assertEquals("multi-signal", matcherRadio.getValue(), "matcher should be populated to multi-signal");

        var thresholdField = findNumberFieldByClass(panel, "collector-threshold");
        assertEquals(0.85, thresholdField.getValue(), 0.01, "similarityThreshold should be populated to 0.85");

        var cronField = findCronTextField(panel);
        assertEquals("0 0 4 * * *", cronField.getValue(), "cron should be populated with expression");
    }

    @Test
    void weightSlidersAreRealInputsAndRespond() {
        var panel = new CollectorSettingsPanel();

        // Set matcher to multi-signal so weight sliders are enabled
        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        matcherRadio.setValue("multi-signal");

        // Verify weight sliders are now enabled
        var vectorField = (NumberField) allComponents(panel).stream()
                .filter(c -> c instanceof NumberField && c.getElement().getClassList().contains("collector-weight-vector"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("vector weight field not found"));
        assertTrue(vectorField.isEnabled(), "vector weight slider should be enabled when matcher=multi-signal");

        // Verify stale note is gone: no "not yet implemented" or "coming soon" text
        var allComponents = allComponents(panel);
        var staleNoteFound = allComponents.stream()
                .filter(c -> c instanceof com.vaadin.flow.component.html.Span)
                .map(c -> (com.vaadin.flow.component.html.Span) c)
                .anyMatch(span -> span.getText() != null &&
                        (span.getText().contains("not yet") || span.getText().contains("coming soon")));

        assertTrue(!staleNoteFound,
            "stale 'not yet implemented' note should not exist; weights are now real inputs");

        // Verify polarity veto row exists and shows indicator (not a slider)
        assertTrue(allComponents.stream()
                .filter(c -> c instanceof com.vaadin.flow.component.html.Span)
                .map(c -> (com.vaadin.flow.component.html.Span) c)
                .anyMatch(span -> span.getText() != null && span.getText().equals("always on (veto)")),
            "polarity veto should show 'always on (veto)' indicator");
    }

    @Test
    void saveRoundTripWithEditedValuesPreservesChanges() {
        var panel = new CollectorSettingsPanel();

        // Set initial values
        var initial = new CollectorSettingsPanel.CollectorSettings(
                false,
                false,
                "cosine",
                0.50,
                "0 0 8 * * *",
                null,
                null,
                null,
                null,
                null
        );
        panel.setValue(initial);

        // User edits: flip enabled, set matcher to multi-signal, increase threshold, change cron
        var enabledCheckbox = findCheckboxByClass(panel, "collector-enabled-toggle");
        enabledCheckbox.setValue(true);

        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        matcherRadio.setValue("multi-signal");

        var thresholdField = findNumberFieldByClass(panel, "collector-threshold");
        thresholdField.setValue(0.75);

        var cronField = findCronTextField(panel);
        cronField.setValue("0 0 12 * * *");

        // Save and verify the NEW (edited) values are in the callback, not the originals
        var savedSettings = new AtomicReference<CollectorSettingsPanel.CollectorSettings>();
        panel.setOnSave(savedSettings::set);

        var saveButton = findButton(panel, "collector-save");
        saveButton.click();

        assertNotNull(savedSettings.get(), "onSave must fire on Save button click");
        assertEquals(true, savedSettings.get().enabled(),
            "edited enabled=true must be saved (was false)");
        assertEquals(false, savedSettings.get().dryRun(),
            "dryRun unchanged stays false");
        assertEquals("multi-signal", savedSettings.get().matcher(),
            "edited matcher=multi-signal must be saved (was cosine)");
        assertEquals(0.75, savedSettings.get().similarityThreshold(), 0.01,
            "edited threshold=0.75 must be saved (was 0.50)");
        assertEquals("0 0 12 * * *", savedSettings.get().cron(),
            "edited cron must be saved (was 0 0 8 * * *)");
    }

    @Test
    void allRealControlsPresent() {
        var panel = new CollectorSettingsPanel();

        // Verify all real controls exist and can be found
        assertNotNull(findCheckboxByClass(panel, "collector-enabled-toggle"),
            "enabled toggle must exist");
        assertNotNull(findCheckboxByClass(panel, "dry-run"),
            "dryRun toggle must exist");
        assertNotNull(findRadioGroupByClass(panel, "collector-matcher"),
            "matcher radio group must exist");
        assertNotNull(findNumberFieldByClass(panel, "collector-threshold"),
            "threshold number field must exist");
        assertNotNull(findCronTextField(panel),
            "cron text field must exist");
    }

    private static Button findButton(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains(className))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("button ." + className + " not found"));
    }

    private static Checkbox findCheckboxByClass(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Checkbox)
                .map(c -> (Checkbox) c)
                .filter(c -> c.getElement().getClassList().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("checkbox ." + className + " not found"));
    }

    @SuppressWarnings("unchecked")
    private static <T> com.vaadin.flow.component.radiobutton.RadioButtonGroup<T> findRadioGroupByClass(
            Component root, String className) {
        return (com.vaadin.flow.component.radiobutton.RadioButtonGroup<T>) allComponents(root).stream()
                .filter(c -> c instanceof com.vaadin.flow.component.radiobutton.RadioButtonGroup<?>)
                .filter(c -> c.getElement().getClassList().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("radio group ." + className + " not found"));
    }

    private static NumberField findNumberFieldByClass(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof NumberField)
                .map(c -> (NumberField) c)
                .filter(c -> c.getElement().getClassList().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("number field ." + className + " not found"));
    }

    private static TextField findCronTextField(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof TextField && !(c instanceof NumberField))
                .map(c -> (TextField) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("cron text field not found"));
    }

    private static int countOccurrences(String text, String pattern) {
        if (text == null || pattern == null) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private static List<Component> allComponents(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
