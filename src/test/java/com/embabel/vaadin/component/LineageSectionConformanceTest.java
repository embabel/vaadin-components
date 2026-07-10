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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link LineageSection} renders with the structure and styling
 * specified in the lineage-section design spec: three connected-step cards
 * (Grounding, Provenance, Collapse history) with icon chips, count badges,
 * connector elements, and the collapse history RETIRED → SURVIVOR flow.
 */
class LineageSectionConformanceTest {

    @Test
    void groundingCardHasSpecStructure() {
        var grounding = List.of("Slack · Jun 19", "Email · Jun 21");
        var prov = List.of(new LineageProvider.Lineage.ProvenanceRef(
                "run_1", "2026-01-01T10:00:00Z", "extracted"));
        var lineage = new LineageProvider.Lineage(grounding, prov, Optional.empty());

        var section = new LineageSection(id -> Optional.of(lineage));
        section.show("prop-1");

        // Grounding card exists with proper class structure
        var cards = findDivsByClass(section, "lineage-card");
        assertTrue(cards.size() >= 2, "must have at least grounding + provenance cards");

        var groundingCard = cards.get(0);

        // Header has icon class, title, and count badge
        var header = findDivsByClass(groundingCard, "lineage-card-header");
        assertEquals(1, header.size(), "grounding card must have header");

        var icons = findDivsByClass(header.get(0), "icon-grounding");
        assertEquals(1, icons.size(), "grounding card header must have icon-grounding class");

        var allText = allText(groundingCard);
        assertTrue(allText.contains("Grounding"), "header must contain Grounding title");
        assertTrue(allText.contains("2 refs"), "header must show count badge with 'refs'");

        // Body has ref list container with ref items
        var body = findDivsByClass(groundingCard, "lineage-card-body");
        assertEquals(1, body.size(), "card must have body");

        var refList = findDivsByClass(body.get(0), "lineage-grounding");
        assertEquals(1, refList.size(), "grounding body must have lineage-grounding container");

        var refs = findDivsByClass(refList.get(0), "lineage-ref");
        assertEquals(2, refs.size(), "grounding card must render 2 ref items");
    }

    @Test
    void provenanceCardHasConnectedStepStructure() {
        var provenance = List.of(
                new LineageProvider.Lineage.ProvenanceRef("run_1", "2026-01-01T10:00:00Z", "extracted"),
                new LineageProvider.Lineage.ProvenanceRef("run_2", "2026-01-02T14:00:00Z", "corroborated"));
        var lineage = new LineageProvider.Lineage(List.of(), provenance, Optional.empty());

        var section = new LineageSection(id -> Optional.of(lineage));
        section.show("prop-1");

        var cards = findDivsByClass(section, "lineage-card");
        var provenanceCard = cards.get(1);

        // Header has icon, title, count badge
        var header = findDivsByClass(provenanceCard, "lineage-card-header");
        assertEquals(1, header.size(), "provenance card must have header");

        var icons = findDivsByClass(header.get(0), "icon-provenance");
        assertEquals(1, icons.size(), "provenance card header must have icon-provenance class");

        var headerText = allText(header.get(0));
        assertTrue(headerText.contains("Provenance"), "header must contain Provenance title");
        assertTrue(headerText.contains("2 events"), "header must show count badge with 'events'");

        // Body has steps with connectors (dots and lines)
        var body = findDivsByClass(provenanceCard, "lineage-card-body");
        assertEquals(1, body.size(), "provenance must have body");

        var stepsContainers = findDivsByClass(body.get(0), "lineage-steps");
        assertEquals(1, stepsContainers.size(), "provenance body must have steps container");

        var steps = findDivsByClass(stepsContainers.get(0), "lineage-step");
        assertEquals(2, steps.size(), "provenance must render 2 steps");

        // Each step has rail with dot and line
        for (var step : steps) {
            var rails = findDivsByClass(step, "lineage-rail");
            assertEquals(1, rails.size(), "each step must have rail");

            var dots = findDivsByClass(rails.get(0), "lineage-node-dot");
            assertEquals(1, dots.size(), "rail must have node dot");

            var lines = findDivsByClass(rails.get(0), "lineage-line");
            assertEquals(1, lines.size(), "rail must have connector line");

            var stepBodies = findDivsByClass(step, "lineage-step-body");
            assertEquals(1, stepBodies.size(), "step must have body with t1/t2 text");
        }
    }

    @Test
    void collapseHistoryHasRetiredToSurvivorFlow() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "prefers async", "TENTATIVE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "prop-1", "likes async standups", "MERGE", List.of(member), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var section = new LineageSection(id -> Optional.of(lineage));
        section.show("prop-1");

        var cards = findDivsByClass(section, "lineage-card");
        assertTrue(cards.size() >= 3, "must have grounding + provenance + collapse cards");
        var collapseCard = cards.get(2);

        // Header has icon, title, count badge
        var header = findDivsByClass(collapseCard, "lineage-card-header");
        assertEquals(1, header.size(), "collapse card must have header");

        var icons = findDivsByClass(header.get(0), "icon-collapse");
        assertEquals(1, icons.size(), "collapse card header must have icon-collapse class");

        var headerText = allText(header.get(0));
        assertTrue(headerText.contains("Collapse history"), "header must contain Collapse history title");
        assertTrue(headerText.contains("1 merged in"), "header must show merged count");

        // Body has merge chain with nodes
        var body = findDivsByClass(collapseCard, "lineage-card-body");
        assertEquals(1, body.size(), "collapse must have body");

        var chains = findDivsByClass(body.get(0), "lineage-merge-chain");
        assertEquals(1, chains.size(), "collapse body must have merge chain");

        var chain = chains.get(0);
        var nodes = findDivsByClass(chain, "lineage-merge-node");
        assertEquals(2, nodes.size(), "chain must have retired + survivor nodes");

        // Retired nodes have "Retired" tag
        var retiredNodes = new java.util.ArrayList<Div>();
        for (var node : nodes) {
            if (!node.getClassNames().contains("merge-survivor")) {
                retiredNodes.add(node);
            }
        }
        assertEquals(1, retiredNodes.size(), "must have 1 retired node");

        // Survivor node has "Survivor" tag and merge-survivor class
        var survivorNodes = new java.util.ArrayList<Div>();
        for (var node : nodes) {
            if (node.getClassNames().contains("merge-survivor")) {
                survivorNodes.add(node);
            }
        }
        assertEquals(1, survivorNodes.size(), "must have 1 survivor node");
        assertTrue(survivorNodes.get(0).getClassNames().contains("merge-survivor"),
                "survivor must have merge-survivor class for green styling");

        // Arrows between nodes
        var arrows = findDivsByClass(chain, "lineage-merge-arrow");
        assertEquals(1, arrows.size(), "chain with 1 retired should have 1 arrow");
    }

    @Test
    void undoButtonRenderInMergeNodeWhenHandlerSet() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "stale memory", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "prop-1", "active memory", "MERGE", List.of(member), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var section = new LineageSection(id -> Optional.of(lineage));
        section.setOnUndoMember((survivorId, retiredId) -> {
        });
        section.show("prop-1");

        // Verify undo affordance renders in collapse card
        var cards = findDivsByClass(section, "lineage-card");
        var collapseCard = cards.get(2);
        var allText = allText(collapseCard);
        assertTrue(allText.contains("Undo"), "undo button must render when handler is set");
    }

    @Test
    void openRefButtonsRenderInGroundingAndProvenanceWhenHandlerSet() {
        var grounding = List.of("ref-1");
        var provenance = List.of(new LineageProvider.Lineage.ProvenanceRef("run", "2026-01-01T10:00:00Z", "detail"));
        var lineage = new LineageProvider.Lineage(grounding, provenance, Optional.empty());

        var section = new LineageSection(id -> Optional.of(lineage));
        section.setOnOpenRef(ref -> {
        });
        section.show("prop-1");

        // Grounding card has open button affordance
        var cards = findDivsByClass(section, "lineage-card");
        var groundingCard = cards.get(0);
        var groundingText = allText(groundingCard);
        assertTrue(groundingText.contains("Open →"), "grounding open button must render when handler set");

        // Provenance card has open button affordance
        var provenanceCard = cards.get(1);
        var provenanceText = allText(provenanceCard);
        assertTrue(provenanceText.contains("Open →"), "provenance open button must render when handler set");
    }

    private static String allText(Component root) {
        var sb = new StringBuilder();
        if (root instanceof com.vaadin.flow.component.html.Span span) {
            sb.append(span.getText()).append(" ");
        } else if (root instanceof com.vaadin.flow.component.html.Div div) {
            sb.append(div.getText()).append(" ");
        } else if (root instanceof com.vaadin.flow.component.html.H3 h3) {
            sb.append(h3.getText()).append(" ");
        } else if (root instanceof com.vaadin.flow.component.button.Button button) {
            sb.append(button.getText()).append(" ");
        }
        root.getChildren().forEach(child -> sb.append(allText(child)));
        return sb.toString();
    }

    private static java.util.List<Div> findDivsByClass(Component root, String className) {
        var result = new java.util.ArrayList<Div>();
        findDivsByClassRecursive(root, className, result);
        return result;
    }

    private static void findDivsByClassRecursive(Component c, String className, java.util.List<Div> result) {
        if (c instanceof Div div && div.getClassNames().contains(className)) {
            result.add(div);
        }
        c.getChildren().forEach(child -> findDivsByClassRecursive(child, className, result));
    }
}
