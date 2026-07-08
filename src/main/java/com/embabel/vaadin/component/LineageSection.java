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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Section showing a single proposition's lineage: what it's grounded on, its provenance trail,
 * and (if it was ever involved in a collapse) the merge history behind it. Data comes from a
 * {@link LineageProvider} supplied by the host, so this component doesn't know or care where the
 * trace is stored.
 */
public class LineageSection extends VerticalLayout {

    private final LineageProvider lineageProvider;

    /**
     * @param lineageProvider looks up lineage for a proposition id
     */
    public LineageSection(LineageProvider lineageProvider) {
        this.lineageProvider = lineageProvider;
        addClassName("lineage-section");
        setPadding(false);
        setSpacing(true);
    }

    /**
     * Renders the lineage for the given proposition, replacing whatever was shown before.
     *
     * @param propositionId id of the proposition to trace
     */
    public void show(String propositionId) {
        removeAll();

        var lineage = lineageProvider.lineageFor(propositionId);
        if (lineage.isEmpty()) {
            var empty = new Span("No lineage available for this memory.");
            empty.addClassName("lineage-empty");
            add(empty);
            return;
        }

        var data = lineage.get();
        add(createGroundingSection(data.grounding()));
        add(createProvenanceSection(data.provenance()));
        data.collapse().ifPresent(explanation -> add(createCollapseSection(explanation)));
    }

    private Div createGroundingSection(java.util.List<String> grounding) {
        var section = new Div();
        section.addClassName("lineage-grounding");

        var heading = new Span("Grounding");
        heading.addClassName("lineage-section-heading");
        section.add(heading);

        if (grounding.isEmpty()) {
            var none = new Span("No grounding references.");
            none.addClassName("lineage-grounding-empty");
            section.add(none);
        } else {
            for (var ref : grounding) {
                var refSpan = new Span(ref);
                refSpan.addClassName("lineage-grounding-ref");
                section.add(refSpan);
            }
        }
        return section;
    }

    private Div createProvenanceSection(java.util.List<LineageProvider.Lineage.ProvenanceRef> provenance) {
        var section = new Div();
        section.addClassName("lineage-provenance");

        var heading = new Span("Provenance");
        heading.addClassName("lineage-section-heading");
        section.add(heading);

        if (provenance.isEmpty()) {
            var none = new Span("No provenance recorded.");
            none.addClassName("lineage-provenance-empty");
            section.add(none);
        } else {
            for (var entry : provenance) {
                var text = entry.source() + " (" + entry.ref() + ")"
                        + (entry.detail() != null && !entry.detail().isBlank() ? " — " + entry.detail() : "");
                var entrySpan = new Span(text);
                entrySpan.addClassName("lineage-provenance-entry");
                section.add(entrySpan);
            }
        }
        return section;
    }

    private Div createCollapseSection(CollapseExplanation explanation) {
        var section = new Div();
        section.addClassName("lineage-collapse");

        var heading = new Span("Collapse history");
        heading.addClassName("lineage-section-heading");
        section.add(heading);

        var survivorSpan = new Span("Kept: " + explanation.survivorText());
        survivorSpan.addClassName("lineage-collapse-survivor");
        section.add(survivorSpan);

        for (var member : explanation.retired()) {
            var text = member.text() != null ? member.text() : "(memory " + member.propositionId() + ")";
            var displayText = "Folded in: " + text;
            if (member.priorStatus() != null && !member.priorStatus().isBlank()) {
                displayText += "  (was " + member.priorStatus() + ")";
            }
            var memberSpan = new Span(displayText);
            memberSpan.addClassName("lineage-collapse-retired");
            section.add(memberSpan);
        }

        return section;
    }
}
