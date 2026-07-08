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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link LineageSection} renders a proposition's grounding, provenance, and (when present)
 * collapse history from whatever a {@link LineageProvider} hands back, and shows a plain empty
 * state when the provider has nothing on record.
 */
class LineageSectionTest {

    @Test
    void rendersGroundingAndProvenanceWithoutCollapse() {
        var lineage = new LineageProvider.Lineage(
                List.of("doc-1#p2", "doc-3#p9"),
                List.of(new LineageProvider.Lineage.ProvenanceRef("email-42", "2026-01-01T10:00:00Z", "extracted by nlp")),
                Optional.empty());

        var section = new LineageSection(id -> Optional.of(lineage));
        section.show("prop-1");

        var text = allText(section);
        assertTrue(text.contains("doc-1#p2"), "must show grounding ref — got: " + text);
        assertTrue(text.contains("doc-3#p9"), "must show grounding ref — got: " + text);
        assertTrue(text.contains("email-42"), "must show provenance source — got: " + text);
        assertTrue(text.contains("2026-01-01T10:00:00Z"), "must show provenance ref — got: " + text);
        assertTrue(text.contains("extracted by nlp"), "must show provenance detail — got: " + text);
        assertFalse(text.contains("Collapse history"), "no collapse section when collapse is empty — got: " + text);
    }

    @Test
    void showsEmptyStateWhenProviderHasNothing() {
        var section = new LineageSection(id -> Optional.empty());
        section.show("prop-unknown");

        var text = allText(section);
        assertTrue(text.contains("No lineage available"), "must show empty state — got: " + text);
    }

    @Test
    void rendersCollapseHistoryWhenPresent() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "prop-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var section = new LineageSection(id -> Optional.of(lineage));
        section.show("prop-1");

        var text = allText(section);
        assertTrue(text.contains("Collapse history"), "must show collapse heading — got: " + text);
        assertTrue(text.contains("Kept: Jim lives in Brisbane"), "must show survivor text — got: " + text);
        assertTrue(text.contains("Folded in: Jim works in Melbourne"), "must show retired member text — got: " + text);
        assertTrue(text.contains("(was STALE)"), "must show prior status — got: " + text);
    }

    @Test
    void clearsPreviousRenderOnRepeatedShow() {
        var lineageA = new LineageProvider.Lineage(List.of("doc-a"), List.of(), Optional.empty());
        var lineageB = new LineageProvider.Lineage(List.of("doc-b"), List.of(), Optional.empty());

        var section = new LineageSection(id -> "prop-a".equals(id) ? Optional.of(lineageA) : Optional.of(lineageB));
        section.show("prop-a");
        section.show("prop-b");

        var text = allText(section);
        assertFalse(text.contains("doc-a"), "previous render must be cleared — got: " + text);
        assertTrue(text.contains("doc-b"), "must show the latest render — got: " + text);
    }

    private static String allText(Component root) {
        return collectAllText(root);
    }

    private static String collectAllText(Component c) {
        var sb = new StringBuilder();
        if (c instanceof Span span) {
            sb.append(span.getText()).append(" ");
        }
        // Recursively collect from all children
        c.getChildren().forEach(child -> sb.append(collectAllText(child)));
        return sb.toString();
    }
}
