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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for signal weight sliders as real inputs on the CollectorSettings record.
 * Covers: loading weights from settings, server-side mutations, enable/disable based on matcher,
 * extraction on save, polarity veto as static indicator, and stale notes removed.
 */
class CollectorSettingsPanelWeightsTest {

    @Test
    void constructPanelWithNullWeightsShowsDiceDefaults() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "multi-signal",
                0.85,
                "0 0 4 * * *",
                null,  // vector: null → show 1.0 (dice default)
                null,  // lexical: null → show 0.5
                null,  // entityOverlap: null → show 1.0
                null,  // groundingOverlap: null → show 0.5
                null   // provenanceOverlap: null → show 0.5
        );

        panel.setValue(settings);

        // Verify slider values are loaded from dice defaults
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertEquals(1.0, vectorField.getValue(), 0.01, "null vector should load dice default 1.0");

        var lexicalField = findNumberFieldByClass(panel, "collector-weight-lexical");
        assertEquals(0.5, lexicalField.getValue(), 0.01, "null lexical should load dice default 0.5");

        var entityOverlapField = findNumberFieldByClass(panel, "collector-weight-entity-overlap");
        assertEquals(1.0, entityOverlapField.getValue(), 0.01, "null entityOverlap should load dice default 1.0");

        var groundingOverlapField = findNumberFieldByClass(panel, "collector-weight-grounding-overlap");
        assertEquals(0.5, groundingOverlapField.getValue(), 0.01, "null groundingOverlap should load dice default 0.5");

        var provenanceOverlapField = findNumberFieldByClass(panel, "collector-weight-provenance-overlap");
        assertEquals(0.5, provenanceOverlapField.getValue(), 0.01, "null provenanceOverlap should load dice default 0.5");
    }

    @Test
    void constructPanelWithNonNullWeightsShowsStoredValues() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "multi-signal",
                0.85,
                "0 0 4 * * *",
                0.8,   // vector: stored value
                0.3,   // lexical: stored value
                0.9,   // entityOverlap: stored value
                0.4,   // groundingOverlap: stored value
                0.6    // provenanceOverlap: stored value
        );

        panel.setValue(settings);

        // Verify slider values are loaded from stored settings
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertEquals(0.8, vectorField.getValue(), 0.01, "vector should load stored value 0.8");

        var lexicalField = findNumberFieldByClass(panel, "collector-weight-lexical");
        assertEquals(0.3, lexicalField.getValue(), 0.01, "lexical should load stored value 0.3");

        var entityOverlapField = findNumberFieldByClass(panel, "collector-weight-entity-overlap");
        assertEquals(0.9, entityOverlapField.getValue(), 0.01, "entityOverlap should load stored value 0.9");

        var groundingOverlapField = findNumberFieldByClass(panel, "collector-weight-grounding-overlap");
        assertEquals(0.4, groundingOverlapField.getValue(), 0.01, "groundingOverlap should load stored value 0.4");

        var provenanceOverlapField = findNumberFieldByClass(panel, "collector-weight-provenance-overlap");
        assertEquals(0.6, provenanceOverlapField.getValue(), 0.01, "provenanceOverlap should load stored value 0.6");
    }

    @Test
    void setSliderValueServerSideIsExtractedOnSave() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "multi-signal",
                0.85,
                "0 0 4 * * *",
                0.5,
                0.5,
                0.5,
                0.5,
                0.5
        );
        panel.setValue(settings);

        // User mutates one slider server-side
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        vectorField.setValue(0.75);

        // Save and verify the new weight is extracted
        var savedSettings = new AtomicReference<CollectorSettingsPanel.CollectorSettings>();
        panel.setOnSave(savedSettings::set);

        var saveButton = findButton(panel, "collector-save");
        saveButton.click();

        assertNotNull(savedSettings.get(), "onSave must fire on Save button click");
        assertEquals(0.75, savedSettings.get().vector(), 0.01,
            "mutated vector weight should be extracted on save");
        assertEquals(0.5, savedSettings.get().lexical(), 0.01,
            "unchanged lexical should remain 0.5");
    }

    @Test
    void matcherCosineDisablesWeightSliders() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "cosine",
                0.85,
                "0 0 4 * * *",
                0.8,
                0.3,
                0.9,
                0.4,
                0.6
        );
        panel.setValue(settings);

        // Verify weight sliders are disabled when matcher == "cosine"
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertFalse(vectorField.isEnabled(), "vector weight should be disabled when matcher=cosine");

        var lexicalField = findNumberFieldByClass(panel, "collector-weight-lexical");
        assertFalse(lexicalField.isEnabled(), "lexical weight should be disabled when matcher=cosine");

        var entityOverlapField = findNumberFieldByClass(panel, "collector-weight-entity-overlap");
        assertFalse(entityOverlapField.isEnabled(), "entityOverlap weight should be disabled when matcher=cosine");

        var groundingOverlapField = findNumberFieldByClass(panel, "collector-weight-grounding-overlap");
        assertFalse(groundingOverlapField.isEnabled(), "groundingOverlap weight should be disabled when matcher=cosine");

        var provenanceOverlapField = findNumberFieldByClass(panel, "collector-weight-provenance-overlap");
        assertFalse(provenanceOverlapField.isEnabled(), "provenanceOverlap weight should be disabled when matcher=cosine");
    }

    @Test
    void matcherMultiSignalEnablesWeightSliders() {
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

        // Verify weight sliders are enabled when matcher == "multi-signal"
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertTrue(vectorField.isEnabled(), "vector weight should be enabled when matcher=multi-signal");

        var lexicalField = findNumberFieldByClass(panel, "collector-weight-lexical");
        assertTrue(lexicalField.isEnabled(), "lexical weight should be enabled when matcher=multi-signal");

        var entityOverlapField = findNumberFieldByClass(panel, "collector-weight-entity-overlap");
        assertTrue(entityOverlapField.isEnabled(), "entityOverlap weight should be enabled when matcher=multi-signal");

        var groundingOverlapField = findNumberFieldByClass(panel, "collector-weight-grounding-overlap");
        assertTrue(groundingOverlapField.isEnabled(), "groundingOverlap weight should be enabled when matcher=multi-signal");

        var provenanceOverlapField = findNumberFieldByClass(panel, "collector-weight-provenance-overlap");
        assertTrue(provenanceOverlapField.isEnabled(), "provenanceOverlap weight should be enabled when matcher=multi-signal");
    }

    @Test
    void matcherChangeLiveTogglesSlidersEnableState() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "cosine",
                0.85,
                "0 0 4 * * *",
                0.8,
                0.3,
                0.9,
                0.4,
                0.6
        );
        panel.setValue(settings);

        // Start with cosine: sliders are disabled
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertFalse(vectorField.isEnabled(), "vector weight should start disabled (cosine matcher)");

        // Switch to multi-signal: sliders become enabled
        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        matcherRadio.setValue("multi-signal");

        assertTrue(vectorField.isEnabled(), "vector weight should become enabled (multi-signal matcher)");

        // Switch back to cosine: sliders become disabled again
        matcherRadio.setValue("cosine");

        assertFalse(vectorField.isEnabled(), "vector weight should become disabled again (cosine matcher)");
    }

    @Test
    void matcherCosinePreservesWeightValuesOnExtraction() {
        var panel = new CollectorSettingsPanel();
        var settings = new CollectorSettingsPanel.CollectorSettings(
                true,
                false,
                "cosine",
                0.85,
                "0 0 4 * * *",
                0.8,
                0.3,
                0.9,
                0.4,
                0.6
        );
        panel.setValue(settings);

        // Sliders are disabled but values are loaded
        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");
        assertEquals(0.8, vectorField.getValue(), 0.01, "vector weight should be loaded even if disabled");

        // Save while cosine: weights should be extracted and preserved
        var savedSettings = new AtomicReference<CollectorSettingsPanel.CollectorSettings>();
        panel.setOnSave(savedSettings::set);

        var saveButton = findButton(panel, "collector-save");
        saveButton.click();

        assertNotNull(savedSettings.get(), "onSave must fire on Save button click");
        assertEquals(0.8, savedSettings.get().vector(), 0.01, "vector weight must be preserved on save (cosine matcher)");
        assertEquals(0.3, savedSettings.get().lexical(), 0.01, "lexical weight must be preserved on save (cosine matcher)");
    }

    @Test
    void polarityVetoRowHasNoSliderShowsStaticIndicator() {
        var panel = new CollectorSettingsPanel();

        var allComponents = allComponents(panel);

        // Verify no input element for polarity veto (no slider)
        var polaritySliderExists = allComponents.stream()
                .filter(c -> c instanceof NumberField)
                .map(c -> (NumberField) c)
                .anyMatch(f -> f.getElement().getClassList().contains("collector-weight-polarity-veto"));

        assertFalse(polaritySliderExists,
            "polarity veto should NOT have a slider (it's a hardcoded veto gate, not a weight)");

        // Verify static indicator text is present
        var indicatorFound = allComponents.stream()
                .filter(c -> c instanceof com.vaadin.flow.component.html.Span)
                .map(c -> (com.vaadin.flow.component.html.Span) c)
                .anyMatch(span -> span.getText() != null && span.getText().equals("always on (veto)"));

        assertTrue(indicatorFound,
            "polarity veto row should show static 'always on (veto)' indicator");
    }

    @Test
    void staleNoteAboutNotYetImplementedIsGone() {
        var panel = new CollectorSettingsPanel();

        var allComponents = allComponents(panel);

        // Verify no stale note text
        var staleNoteFound = allComponents.stream()
                .filter(c -> c instanceof com.vaadin.flow.component.html.Span)
                .map(c -> (com.vaadin.flow.component.html.Span) c)
                .anyMatch(span -> span.getText() != null &&
                        (span.getText().contains("not yet implemented") ||
                         span.getText().contains("coming soon")));

        assertFalse(staleNoteFound,
            "stale note 'Individual signal properties are not yet implemented in dice' should be gone");

        // Verify new note is present
        var correctNoteFound = allComponents.stream()
                .filter(c -> c instanceof com.vaadin.flow.component.html.Span)
                .map(c -> (com.vaadin.flow.component.html.Span) c)
                .anyMatch(span -> span.getText() != null &&
                        span.getText().equals("Enabled when Matcher is set to Multi-signal."));

        assertTrue(correctNoteFound,
            "correct note 'Enabled when Matcher is set to Multi-signal.' should be present");
    }

    // ============ Helper methods ============

    private static Button findButton(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains(className))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("button ." + className + " not found"));
    }

    private static NumberField findNumberFieldByClass(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof NumberField)
                .map(c -> (NumberField) c)
                .filter(c -> c.getElement().getClassList().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("number field ." + className + " not found"));
    }

    @SuppressWarnings("unchecked")
    private static <T> RadioButtonGroup<T> findRadioGroupByClass(Component root, String className) {
        return (RadioButtonGroup<T>) allComponents(root).stream()
                .filter(c -> c instanceof RadioButtonGroup<?>)
                .filter(c -> c.getElement().getClassList().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("radio group ." + className + " not found"));
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
