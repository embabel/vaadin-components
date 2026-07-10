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
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

/**
 * Pure Vaadin view for collector/dedup settings. Exposes the collector's real knobs:
 * enabled, dryRun, matcher, similarityThreshold, cron, and — when the matcher is
 * multi-signal — the five per-signal weights. A null weight means "use the collector's
 * own default"; polarity veto is a fixed gate, shown as an indicator rather than a slider.
 */
public class CollectorSettingsPanel extends VerticalLayout {

    public record CollectorSettings(
            boolean enabled,
            boolean dryRun,
            String matcher,
            double similarityThreshold,
            String cron,
            Double vector,
            Double lexical,
            Double entityOverlap,
            Double groundingOverlap,
            Double provenanceOverlap
    ) {}

    private Consumer<CollectorSettings> onSave;

    private final Checkbox enabledToggle;
    private final Checkbox dryRunToggle;
    private final RadioButtonGroup<String> matcherGroup;
    private final NumberField thresholdField;
    private final TextField cronField;
    private final NumberField vectorField;
    private final NumberField lexicalField;
    private final NumberField entityOverlapField;
    private final NumberField groundingOverlapField;
    private final NumberField provenanceOverlapField;

    public CollectorSettingsPanel() {
        addClassName("collector-settings-panel");
        getElement().getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "10px");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        // Header
        var header = new VerticalLayout();
        header.setPadding(true);
        header.setSpacing(false);
        header.addClassName("collector-panel-header");
        header.getStyle().set("border-bottom", "1px solid var(--lumo-divider-color, #e2e5ea)");
        var title = new H2("Collector Settings");
        title.getStyle().set("margin", "0 0 2px 0").set("font-size", "15px").set("font-weight", "600");
        var subtitle = new Paragraph("Controls how dice groups and collapses similar memories during the dedup sweep.");
        subtitle.getStyle().set("margin", "0").set("font-size", "12px").set("color", "var(--lumo-secondary-text-color)");
        header.add(title, subtitle);
        add(header);

        // Master section
        var masterSection = createSection("Master");
        enabledToggle = createToggleRow(masterSection, "Enable dedup sweep", "Master switch. Off = the collector never runs.");
        dryRunToggle = createToggleRow(masterSection, "Dry run", "Compute candidate merges without applying them. Applies immediately — no rebuild needed.");
        add(masterSection);

        // Clustering section
        var clusteringSection = createSection("Clustering");
        matcherGroup = new RadioButtonGroup<>();
        matcherGroup.setItems("cosine", "multi-signal");
        matcherGroup.addClassName("collector-matcher");
        matcherGroup.addClassName("collector-segmented");
        matcherGroup.getStyle()
                .set("padding", "2px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px");
        // Enable/disable weight sliders when matcher changes
        matcherGroup.addValueChangeListener(e -> updateWeightSliderEnableState());
        var matcherRow = new HorizontalLayout();
        matcherRow.addClassName("row");
        matcherRow.setAlignItems(Alignment.CENTER);
        matcherRow.setSpacing(true);
        matcherRow.setPadding(false);
        matcherRow.setWidthFull();
        var matcherLabelDiv = new VerticalLayout();
        matcherLabelDiv.addClassName("row-label");
        matcherLabelDiv.setPadding(false);
        matcherLabelDiv.setSpacing(false);
        var matcherLabel = new Span("Matcher");
        matcherLabel.addClassName("name");
        matcherLabel.getStyle().set("font-weight", "500");
        var matcherDesc = new Span("Which strategy scores candidate duplicates.");
        matcherDesc.addClassName("desc");
        matcherDesc.getStyle().set("color", "var(--lumo-secondary-text-color, #5a6270)").set("font-size", "11.5px");
        matcherLabelDiv.add(matcherLabel, matcherDesc);
        matcherRow.add(matcherLabelDiv, matcherGroup);
        matcherRow.setFlexGrow(1, matcherLabelDiv);
        clusteringSection.add(matcherRow);

        // Threshold slider using NumberField, with a live value readout matching the design spec's slider rows
        this.thresholdField = new NumberField();
        this.thresholdField.setMin(0.0);
        this.thresholdField.setMax(1.0);
        this.thresholdField.setStep(0.01);
        this.thresholdField.setValue(0.70);
        this.thresholdField.addClassName("collector-threshold");
        this.thresholdField.setWidthFull();

        clusteringSection.add(createSliderRow("Similarity threshold", thresholdField));
        add(clusteringSection);

        // Signal weights section
        var weightsSection = createSection("Signal weights", "multi-signal only");

        // Dice's real defaults: vector 1.0, lexical 0.5, entityOverlap 1.0, groundingOverlap 0.5, provenanceOverlap 0.5
        this.vectorField = createWeightSlider("Vector", 1.0, "collector-weight-vector");
        this.lexicalField = createWeightSlider("Lexical", 0.5, "collector-weight-lexical");
        this.entityOverlapField = createWeightSlider("Entity overlap", 1.0, "collector-weight-entity-overlap");
        this.groundingOverlapField = createWeightSlider("Grounding overlap", 0.5, "collector-weight-grounding-overlap");
        this.provenanceOverlapField = createWeightSlider("Provenance overlap", 0.5, "collector-weight-provenance-overlap");

        weightsSection.add(createSliderRow("Vector", vectorField));
        weightsSection.add(createSliderRow("Lexical", lexicalField));
        weightsSection.add(createSliderRow("Entity overlap", entityOverlapField));
        weightsSection.add(createSliderRow("Grounding overlap", groundingOverlapField));
        weightsSection.add(createSliderRow("Provenance overlap", provenanceOverlapField));

        // Polarity veto is not a weight; it's a hardcoded veto gate in dice
        weightsSection.add(createPolarityVetoIndicator());

        var enabledNote = new Span("Enabled when Matcher is set to Multi-signal.");
        enabledNote.addClassName("disabled-note");
        enabledNote.getStyle()
                .set("font-size", "11px")
                .set("color", "var(--lumo-tertiary-text-color, #98a0ac)")
                .set("font-style", "italic")
                .set("margin-top", "8px")
                .set("display", "block");
        weightsSection.add(enabledNote);
        add(weightsSection);

        // Scheduling section
        var schedulingSection = createSection("Scheduling & eager recall");
        var fieldGrid = new Div();
        fieldGrid.addClassName("field-grid");
        fieldGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr")
                .set("gap", "12px")
                .set("width", "100%");
        cronField = new TextField("Sweep cron");
        cronField.addClassName("field");
        cronField.setWidthFull();
        cronField.getStyle().set("font-size", "12.5px");
        fieldGrid.add(cronField);
        schedulingSection.add(fieldGrid);
        add(schedulingSection);

        // Apply bar
        var applyBar = new HorizontalLayout();
        applyBar.addClassName("apply-bar");
        applyBar.setAlignItems(Alignment.CENTER);
        applyBar.setSpacing(true);
        applyBar.setWidthFull();
        applyBar.getStyle()
                .set("padding", "12px 20px")
                .set("background", "var(--lumo-contrast-5pct)");

        var applyNote = new Span("Threshold, matcher, cron, and signal weights apply on the next sweep. Dry-run applies immediately.");
        applyNote.addClassName("apply-note");
        applyNote.getStyle()
                .set("font-size", "11.5px")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("max-width", "380px");

        var saveButton = new Button("Save");
        saveButton.addClassName("collector-save");
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> fireSave());

        var actions = new HorizontalLayout(saveButton);
        actions.addClassName("apply-actions");
        actions.setSpacing(true);
        applyBar.add(applyNote, actions);
        applyBar.setFlexGrow(1, applyNote);
        add(applyBar);
    }

    private VerticalLayout createSection(String title) {
        return createSection(title, null);
    }

    /**
     * A titled section, matching the spec's SECTION-TITLE row. When a badge text is given
     * (e.g. "multi-signal only") it's rendered as a small pill next to the title, like the
     * Signal weights section in the mock.
     */
    private VerticalLayout createSection(String title, String badgeText) {
        var section = new VerticalLayout();
        section.addClassName("collector-section");
        section.setPadding(true);
        section.setSpacing(false);
        section.setWidthFull();
        section.getStyle().set("border-bottom", "1px solid var(--lumo-divider-color, #e2e5ea)");

        var titleRow = new HorizontalLayout();
        titleRow.addClassName("section-title");
        titleRow.setAlignItems(Alignment.CENTER);
        titleRow.setSpacing(true);
        titleRow.setPadding(false);
        titleRow.getStyle().set("margin-bottom", "12px");

        var sectionTitle = new Span(title.toUpperCase());
        sectionTitle.getStyle()
                .set("font-size", "11px")
                .set("font-weight", "600")
                .set("letter-spacing", "0.04em")
                .set("text-transform", "uppercase")
                .set("color", "var(--lumo-tertiary-text-color, #98a0ac)");
        titleRow.add(sectionTitle);

        if (badgeText != null) {
            var badge = new Span(badgeText);
            badge.addClassName("badge");
            badge.getStyle()
                    .set("font-size", "10px")
                    .set("padding", "1px 6px")
                    .set("border-radius", "10px")
                    .set("background", "var(--lumo-primary-color-10pct)")
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-weight", "600")
                    .set("text-transform", "none")
                    .set("letter-spacing", "normal");
            titleRow.add(badge);
        }

        section.add(titleRow);

        return section;
    }

    private Checkbox createToggleRow(VerticalLayout section, String label, String description) {
        var row = new HorizontalLayout();
        row.addClassName("row");
        row.setAlignItems(Alignment.CENTER);
        row.setSpacing(true);
        row.setWidthFull();
        row.setPadding(false);
        row.getStyle().set("padding", "8px 0").set("border-top", "1px solid var(--lumo-divider-color, #e2e5ea)");

        var labelDiv = new VerticalLayout();
        labelDiv.addClassName("row-label");
        labelDiv.setPadding(false);
        labelDiv.setSpacing(false);
        var name = new Span(label);
        name.addClassName("name");
        name.getStyle().set("font-weight", "500");
        var desc = new Span(description);
        desc.addClassName("desc");
        desc.getStyle()
                .set("color", "var(--lumo-secondary-text-color, #5a6270)")
                .set("font-size", "11.5px")
                .set("margin-top", "2px")
                .set("display", "block");
        labelDiv.add(name, desc);

        var checkbox = new Checkbox();
        checkbox.addClassName(label.toLowerCase().replace(" ", "-"));
        if (label.equals("Enable dedup sweep")) {
            checkbox.addClassName("collector-enabled-toggle");
        }
        checkbox.getStyle().set("flex-shrink", "0");

        row.add(labelDiv, checkbox);
        row.setFlexGrow(1, labelDiv);
        section.add(row);

        return checkbox;
    }

    /**
     * Create a weight slider as a NumberField (range input) between 0 and 1 with 0.01 step.
     * Disabled by default; enabled when matcher == "multi-signal".
     */
    private NumberField createWeightSlider(String label, double diceDefault, String className) {
        var field = new NumberField();
        field.setMin(0.0);
        field.setMax(1.0);
        field.setStep(0.01);
        field.setValue(diceDefault);
        field.addClassName(className);
        field.setWidthFull();
        field.setEnabled(false); // Initially disabled; enabled when matcher == "multi-signal"
        return field;
    }

    /**
     * Wrap a 0-1 NumberField in the spec's "slider-row" anatomy: a name + live value readout on
     * top, the input underneath. NumberField (not a bare HTML range input) stays the actual
     * control because it's what the round-trip tests drive and what the framework binds to;
     * the readout span mirrors the mock's tabular-nums blue value label.
     */
    private Component createSliderRow(String label, NumberField field) {
        var row = new VerticalLayout();
        row.addClassName("slider-row");
        row.setPadding(false);
        row.setSpacing(false);
        row.getStyle().set("padding", "10px 0").set("border-top", "1px solid var(--lumo-divider-color, #e2e5ea)").set("gap", "6px");

        var top = new HorizontalLayout();
        top.addClassName("top");
        top.setWidthFull();
        top.setPadding(false);
        top.setSpacing(true);
        top.setAlignItems(Alignment.BASELINE);
        top.setJustifyContentMode(JustifyContentMode.BETWEEN);

        var name = new Span(label);
        name.addClassName("name");
        name.getStyle().set("font-weight", "500");

        var value = new Span(formatWeight(field.getValue()));
        value.addClassName("value");
        value.getStyle()
                .set("font-variant-numeric", "tabular-nums")
                .set("color", "var(--lumo-primary-color)")
                .set("font-weight", "600")
                .set("font-size", "12px");
        field.addValueChangeListener(e -> value.setText(formatWeight(e.getValue())));

        top.add(name, value);
        row.add(top, field);
        return row;
    }

    private static String formatWeight(Double value) {
        return value == null ? "—" : String.format("%.2f", value);
    }

    /**
     * Create the polarity veto indicator row (static, not a weight slider).
     * Polarity veto is hardcoded as a pure veto gate in dice, not a configurable weight.
     */
    private Component createPolarityVetoIndicator() {
        var indicatorRow = new VerticalLayout();
        indicatorRow.addClassName("slider-row");
        indicatorRow.addClassName("collector-polarity-row");
        indicatorRow.setPadding(false);
        indicatorRow.setSpacing(false);
        indicatorRow.getStyle()
                .set("padding", "10px 0")
                .set("border-top", "1px solid var(--lumo-divider-color, #e2e5ea)");

        var top = new HorizontalLayout();
        top.addClassName("top");
        top.setAlignItems(Alignment.BASELINE);
        top.setWidthFull();
        top.setPadding(false);
        top.setSpacing(false);

        var name = new Span("Polarity veto");
        name.addClassName("name");
        name.getStyle().set("font-weight", "500");
        var indicator = new Span("always on (veto)");
        indicator.addClassName("value");
        indicator.getStyle()
                .set("color", "var(--lumo-tertiary-text-color, #98a0ac)")
                .set("font-weight", "600")
                .set("font-size", "12px");
        top.add(name, indicator);
        top.setFlexGrow(1, name);

        indicatorRow.add(top);
        return indicatorRow;
    }

    /**
     * Enable weight sliders only when matcher == "multi-signal"; disable otherwise.
     */
    private void updateWeightSliderEnableState() {
        boolean isMultiSignal = "multi-signal".equals(matcherGroup.getValue());
        vectorField.setEnabled(isMultiSignal);
        lexicalField.setEnabled(isMultiSignal);
        entityOverlapField.setEnabled(isMultiSignal);
        groundingOverlapField.setEnabled(isMultiSignal);
        provenanceOverlapField.setEnabled(isMultiSignal);
    }

    /**
     * Populate the panel with the given settings.
     * Dice's defaults are: vector=1.0, lexical=0.5, entityOverlap=1.0, groundingOverlap=0.5, provenanceOverlap=0.5.
     * If a weight field is null, load the dice default; if set, load the stored value.
     */
    public void setValue(CollectorSettings settings) {
        if (settings != null) {
            enabledToggle.setValue(settings.enabled());
            dryRunToggle.setValue(settings.dryRun());
            matcherGroup.setValue(settings.matcher());
            thresholdField.setValue(settings.similarityThreshold());
            cronField.setValue(settings.cron() != null ? settings.cron() : "");

            // Load weight values: null → dice default, otherwise → stored value
            vectorField.setValue(settings.vector() != null ? settings.vector() : 1.0);
            lexicalField.setValue(settings.lexical() != null ? settings.lexical() : 0.5);
            entityOverlapField.setValue(settings.entityOverlap() != null ? settings.entityOverlap() : 1.0);
            groundingOverlapField.setValue(settings.groundingOverlap() != null ? settings.groundingOverlap() : 0.5);
            provenanceOverlapField.setValue(settings.provenanceOverlap() != null ? settings.provenanceOverlap() : 0.5);

            // Update weight slider enable state based on matcher
            updateWeightSliderEnableState();
        }
    }

    /**
     * Set the callback fired when the Save button is clicked.
     */
    public void setOnSave(Consumer<CollectorSettings> callback) {
        this.onSave = callback;
    }

    private void fireSave() {
        if (onSave == null) return;

        var threshold = getThresholdValue();
        var settings = new CollectorSettings(
                enabledToggle.getValue(),
                dryRunToggle.getValue(),
                matcherGroup.getValue() != null ? matcherGroup.getValue() : "cosine",
                threshold,
                cronField.getValue(),
                vectorField.getValue(),
                lexicalField.getValue(),
                entityOverlapField.getValue(),
                groundingOverlapField.getValue(),
                provenanceOverlapField.getValue()
        );
        onSave.accept(settings);
    }

    private double getThresholdValue() {
        var val = thresholdField.getValue();
        return val != null ? val : 0.7;
    }
}
