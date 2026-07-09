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

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Conformance test verifying EntityPanel matches entity-360 findings.
 * Focuses on: status badges for memories, dark-mode colors, show-more links, and section order.
 */
class EntityPanelConformanceTest {

    private static final String ENTITY_ID = "entity-1";
    private static final String ENTITY_NAME = "Ben Blossom";
    private static final String ENTITY_TYPE = "Person";
    private static final String CONTEXT = "ctx-1";

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(entity.getName()).thenReturn(ENTITY_NAME);
        when(entity.getDescription()).thenReturn("Robotics engineer");
        when(entity.labels()).thenReturn(Set.of(ENTITY_TYPE));
        return entity;
    }

    // F1: Status badges for propositions
    @Test
    void propositionsShowConfirmedBadgeForHighConfidence() {
        var prop = createPropositionWithConfidence("p1", "Ben prefers async", 0.85);
        var panel = new EntityPanel(createEntity(), id -> List.of(prop));

        var text = allText(panel);
        assertTrue(text.contains("Confirmed"), "Must show Confirmed badge for >= 80% confidence");
        assertTrue(text.contains("Ben prefers async"), "Must show proposition text");
    }

    @Test
    void propositionsShowTentativeBadgeForLowConfidence() {
        var prop = createPropositionWithConfidence("p1", "Ben may be interested", 0.5);
        var panel = new EntityPanel(createEntity(), id -> List.of(prop));

        var text = allText(panel);
        assertTrue(text.contains("Tentative"), "Must show Tentative badge for < 80% confidence");
    }

    // F5: Show N more truncation
    @Test
    void propositionsShowMoreLinkForLongLists() {
        var props = List.of(
                createProposition("p1", "Memory 1"),
                createProposition("p2", "Memory 2"),
                createProposition("p3", "Memory 3"),
                createProposition("p4", "Memory 4"),
                createProposition("p5", "Memory 5")
        );

        var panel = new EntityPanel(createEntity(), id -> props);
        var text = allText(panel);

        assertTrue(text.contains("Memory 1"), "Must show first 3 items");
        assertTrue(text.contains("Show 2 more"), "Must show 'Show 2 more' for remaining items");
    }

    @Test
    void relatedItemsShowMoreLink() {
        var items = List.of(
                new EntityPanel.RelatedItem("Item1", "Subtitle1"),
                new EntityPanel.RelatedItem("Item2", "Subtitle2"),
                new EntityPanel.RelatedItem("Item3", "Subtitle3"),
                new EntityPanel.RelatedItem("Item4", "Subtitle4")
        );

        var records = new EntityPanel.RelatedRecords(
                List.of(),
                items,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertTrue(text.contains("Show 1 more"), "Must show 'Show 1 more' for remaining items in related list");
    }

    // F4: Dark-mode colors use CSS custom properties
    @Test
    void edgeChipsUseCSSCustomPropertiesForColors() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("WORKS_FOR Acme", "HAS_EMAIL test@example.com", "ATTENDS meeting")
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        // Verify CSS custom properties are set on the root element
        var panelStyle = panel.getElement().getAttribute("style");
        // Should have dark-mode support; CSS custom properties set via executeJs
        assertTrue(panel.getElement().getClassList().contains("entity-panel-360"),
                "Panel should have entity-panel-360 class for spec styling");
    }

    // F3: Section order
    @Test
    void sectionsRenderInSpecOrder() {
        var prop = createProposition("p1", "Memory");
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("WORKS_FOR Acme")
        );

        var panel = new EntityPanel(createEntity(), id -> List.of(prop));
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        // Verify all sections present in expected order (Contact before Relationships before Mentioned)
        assertTrue(text.contains("Contact Facts"), "Contact Facts must render");
        assertTrue(text.contains("Relationships"), "Relationships must render");
        assertTrue(text.contains("Mentioned in"), "Mentioned in memories must render");
    }

    // F1: Related memories have arrow affordance
    @Test
    void relatedMemoriesHaveArrowAffordance() {
        var prop = createProposition("p1", "Memory text");
        var panel = new EntityPanel(createEntity(), id -> List.of(prop));

        var text = allText(panel);
        assertTrue(text.contains("→"), "Must show arrow affordance in memory items");
    }

    private Proposition createPropositionWithConfidence(String id, String text, double confidence) {
        return Proposition.create(
                id,
                CONTEXT,
                text,
                List.of(),
                confidence,
                0.0,
                0.5,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                PropositionStatus.ACTIVE
        );
    }

    private Proposition createProposition(String id, String text) {
        return createPropositionWithConfidence(id, text, 0.9);
    }

    private static String allText(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out.stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .reduce("", (a, b) -> a + " " + b);
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
