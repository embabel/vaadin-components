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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
 * Proves {@link CollectorSettingsPanel} follows the collector-settings design spec's section
 * structure (Master / Clustering / Signal weights / Scheduling), slider-row value readouts,
 * and that the mock's visual anatomy sits on top of the exact same wiring contract the older
 * tests already pin down.
 */
class CollectorSettingsPanelConformanceTest {

    @Test
    void fourSectionsRenderWithSpecClassNames() {
        var panel = new CollectorSettingsPanel();

        var sections = allComponents(panel).stream()
                .filter(c -> c instanceof VerticalLayout && c.getElement().getClassList().contains("collector-section"))
                .toList();

        assertEquals(4, sections.size(), "spec has four sections: Master, Clustering, Signal weights, Scheduling & eager recall");
    }

    @Test
    void masterAndClusteringRowsUseRowLabelAnatomy() {
        var panel = new CollectorSettingsPanel();

        var rows = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("row"))
                .toList();
        // enable dedup sweep, dry run, matcher row
        assertTrue(rows.size() >= 3, "expected at least 3 .row elements (2 toggles + matcher)");

        var rowLabels = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("row-label"))
                .toList();
        assertTrue(rowLabels.size() >= 3, "each .row should carry a .row-label with name+desc");
    }

    @Test
    void weightRowsShowLiveValueReadouts() {
        var panel = new CollectorSettingsPanel();
        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        matcherRadio.setValue("multi-signal");

        var vectorField = findNumberFieldByClass(panel, "collector-weight-vector");

        var valueSpanBefore = findValueSpanInSameSliderRow(panel, vectorField);
        assertEquals("1.00", valueSpanBefore.getText(), "readout should mirror the dice default 1.0 formatted to 2dp");

        vectorField.setValue(0.42);
        var valueSpanAfter = findValueSpanInSameSliderRow(panel, vectorField);
        assertEquals("0.42", valueSpanAfter.getText(), "readout should track live value changes");
    }

    @Test
    void signalWeightsSectionCarriesMultiSignalOnlyBadge() {
        var panel = new CollectorSettingsPanel();

        var badge = allComponents(panel).stream()
                .filter(c -> c instanceof Span && c.getElement().getClassList().contains("badge"))
                .map(c -> (Span) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("badge span not found"));

        assertEquals("multi-signal only", badge.getText());
    }

    @Test
    void matcherToggleStillEnablesAndDisablesFiveWeightInputs() {
        var panel = new CollectorSettingsPanel();
        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");

        var weightClasses = List.of(
                "collector-weight-vector", "collector-weight-lexical", "collector-weight-entity-overlap",
                "collector-weight-grounding-overlap", "collector-weight-provenance-overlap");

        matcherRadio.setValue("cosine");
        for (var cls : weightClasses) {
            assertFalse(findNumberFieldByClass(panel, cls).isEnabled(), cls + " should be disabled under cosine");
        }

        matcherRadio.setValue("multi-signal");
        for (var cls : weightClasses) {
            assertTrue(findNumberFieldByClass(panel, cls).isEnabled(), cls + " should be enabled under multi-signal");
        }
    }

    @Test
    void polarityIsNotAnInput() {
        var panel = new CollectorSettingsPanel();

        var polarityInputExists = allComponents(panel).stream()
                .anyMatch(c -> (c instanceof NumberField || c instanceof Checkbox)
                        && c.getElement().getClassList().stream().anyMatch(cls -> cls.toLowerCase().contains("polarity")));

        assertFalse(polarityInputExists, "polarity veto must never be a bindable input");
    }

    @Test
    void fullRoundTripCarriesEveryRecordFieldExactly() {
        var panel = new CollectorSettingsPanel();
        var initial = new CollectorSettingsPanel.CollectorSettings(
                false, true, "cosine", 0.33, "0 0 2 * * *",
                0.11, 0.22, 0.33, 0.44, 0.55
        );
        panel.setValue(initial);

        // user edits every field
        findCheckboxByClass(panel, "collector-enabled-toggle").setValue(true);
        findCheckboxByClass(panel, "dry-run").setValue(false);
        var matcherRadio = findRadioGroupByClass(panel, "collector-matcher");
        matcherRadio.setValue("multi-signal");
        findNumberFieldByClass(panel, "collector-threshold").setValue(0.91);
        findCronTextField(panel).setValue("0 0 6 * * *");
        findNumberFieldByClass(panel, "collector-weight-vector").setValue(0.61);
        findNumberFieldByClass(panel, "collector-weight-lexical").setValue(0.62);
        findNumberFieldByClass(panel, "collector-weight-entity-overlap").setValue(0.63);
        findNumberFieldByClass(panel, "collector-weight-grounding-overlap").setValue(0.64);
        findNumberFieldByClass(panel, "collector-weight-provenance-overlap").setValue(0.65);

        var saved = new AtomicReference<CollectorSettingsPanel.CollectorSettings>();
        panel.setOnSave(saved::set);
        findButton(panel, "collector-save").click();

        var s = saved.get();
        assertNotNull(s);
        assertTrue(s.enabled());
        assertFalse(s.dryRun());
        assertEquals("multi-signal", s.matcher());
        assertEquals(0.91, s.similarityThreshold(), 0.001);
        assertEquals("0 0 6 * * *", s.cron());
        assertEquals(0.61, s.vector(), 0.001);
        assertEquals(0.62, s.lexical(), 0.001);
        assertEquals(0.63, s.entityOverlap(), 0.001);
        assertEquals(0.64, s.groundingOverlap(), 0.001);
        assertEquals(0.65, s.provenanceOverlap(), 0.001);
    }

    // ============ helpers ============

    private static Span findValueSpanInSameSliderRow(Component root, NumberField field) {
        var parent = field.getParent().orElseThrow(() -> new AssertionError("field has no parent"));
        return allComponents(parent).stream()
                .filter(c -> c instanceof Span && c.getElement().getClassList().contains("value"))
                .map(c -> (Span) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("value readout span not found in slider-row"));
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
    private static <T> RadioButtonGroup<T> findRadioGroupByClass(Component root, String className) {
        return (RadioButtonGroup<T>) allComponents(root).stream()
                .filter(c -> c instanceof RadioButtonGroup<?>)
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

    private static com.vaadin.flow.component.textfield.TextField findCronTextField(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof com.vaadin.flow.component.textfield.TextField && !(c instanceof NumberField))
                .map(c -> (com.vaadin.flow.component.textfield.TextField) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("cron text field not found"));
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
