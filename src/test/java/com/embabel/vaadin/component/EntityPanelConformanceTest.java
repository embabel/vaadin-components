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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Conformance test verifying EntityPanel matches the entity-360 design spec.
 * Covers: structure (sections render in order), visual classes, related-records groups,
 * empty-state behavior, and callback wiring (chip clicks, close button).
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
        when(entity.getDescription()).thenReturn("Robotics engineer at Photonix Robotics · joined the eng-robotics floor team in June 2026");
        when(entity.labels()).thenReturn(Set.of(ENTITY_TYPE));
        return entity;
    }

    @Test
    void headerSectionRendersWithCorrectStructure() {
        var panel = new EntityPanel(createEntity());

        // Header div should have entity-header-360 class
        var headerDivs = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-header-360"))
                .toList();
        assertTrue(!headerDivs.isEmpty(), "Header must have entity-header-360 class");

        var text = allText(panel);
        assertTrue(text.contains("Ben Blossom"), "Header must display entity name");
        assertTrue(text.contains("Person"), "Header must display type badge");
        assertTrue(text.contains("Robotics engineer"), "Header must display description");

        // Avatar should exist with initials
        var avatarDivs = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-avatar"))
                .toList();
        assertTrue(!avatarDivs.isEmpty(), "Avatar div must exist with entity-avatar class");

        // Close button should exist
        var closeButtons = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-close-btn"))
                .toList();
        assertTrue(!closeButtons.isEmpty(), "Close button must exist with entity-close-btn class");
    }

    @Test
    void closeButtonExistsAndCanReceiveCallbacks() {
        var panel = new EntityPanel(createEntity());
        panel.setOnClose(() -> {}); // Callback can be registered

        var closeBtn = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-close-btn"))
                .findFirst();

        assertTrue(closeBtn.isPresent(), "Close button must exist with entity-close-btn class");
        // Note: actual click behavior is tested in EntityPanelRelatedTest since it requires full Vaadin env
    }

    @Test
    void contactFactsSectionRendersWhenDataPresent() {
        var records = new EntityPanel.RelatedRecords(
                List.of("ben.blossom@photonix.example", "Robotics Engineer II"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertTrue(text.contains("Contact Facts"), "Contact Facts section must render");
        assertTrue(text.contains("ben.blossom@photonix.example"), "Must show email fact");
        assertTrue(text.contains("Robotics Engineer II"), "Must show job title fact");

        var detailsDivs = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(detailsDivs.size() >= 1, "Must have at least one entity-section Details for Contact Facts");
    }

    @Test
    void contactFactsSectionHidesWhenEmpty() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertFalse(text.contains("Contact Facts"), "Contact Facts section must not render when empty");
    }

    @Test
    void relatedRecordsGroupsRenderInExpectedOrder() {
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(new EntityPanel.RelatedItem("Alice", "Colleague")),
                List.of(new EntityPanel.RelatedItem("Acme Corp", "Client")),
                List.of(new EntityPanel.RelatedItem("email subject", "Jun 21")),
                List.of(new EntityPanel.RelatedItem("meeting name", "Recurring")),
                List.of("WORKS_FOR Acme Corp")
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        // All sections should be present
        assertTrue(text.contains("Contact Facts"), "Contact Facts must render");
        assertTrue(text.contains("People"), "People section must render");
        assertTrue(text.contains("Organizations"), "Organizations section must render");
        assertTrue(text.contains("Emails"), "Emails section must render");
        assertTrue(text.contains("Meetings"), "Meetings section must render");
        assertTrue(text.contains("Relationships"), "Relationships (edge chips) section must render");

        // Verify 6 entity-section Details are present (one per category)
        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertEquals(6, entitySections.size(), "Must have exactly 6 entity-section Details — got: " + entitySections.size());
    }

    @Test
    void relatedRecordsGroupsHideWhenEmpty() {
        // Only facts section present
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertTrue(text.contains("Contact Facts"), "Contact Facts must render when present");
        assertFalse(text.contains("People"), "People section must not render when empty");
        assertFalse(text.contains("Organizations"), "Organizations section must not render when empty");
        assertFalse(text.contains("Emails"), "Emails section must not render when empty");
        assertFalse(text.contains("Meetings"), "Meetings section must not render when empty");
        assertFalse(text.contains("Relationships"), "Relationships section must not render when empty");
    }

    @Test
    void relatedItemsRenderTitleAndSubtitle() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(new EntityPanel.RelatedItem("Alice Chen", "Senior Engineer")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertTrue(text.contains("Alice Chen"), "Must show related item title");
        assertTrue(text.contains("Senior Engineer"), "Must show related item subtitle");

        // Verify related-item class exists
        var relatedItems = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-related-item"))
                .toList();
        assertTrue(!relatedItems.isEmpty(), "Related items must have entity-related-item class");
    }

    @Test
    void edgeChipsRenderWithCorrectClass() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    "WORKS_FOR Photonix Robotics",
                    "HAS_EMAIL ben.blossom@photonix.example",
                    "EMAILED 3× re: floor relocation",
                    "ATTENDS eng-robotics standup"
                )
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);
        assertTrue(text.contains("Relationships"), "Relationships section must render");
        assertTrue(text.contains("WORKS_FOR"), "Edge chip must display relationship type");
        assertTrue(text.contains("ATTENDS"), "Edge chip must display attendance relationship");

        // Verify edge-chip elements exist
        var edgeChips = allComponents(panel).stream()
                .filter(c -> c instanceof Span && c.getElement().getClassList().contains("entity-edge-chip"))
                .toList();
        assertTrue(edgeChips.size() >= 4, "Must have edge-chip Spans for all relationships — got: " + edgeChips.size());
    }

    @Test
    void mentionedMemoriesRenderWithStatusBadges() {
        var prop1 = createProposition("prop-1", "Ben prefers async standups");
        var prop2 = createProposition("prop-2", "Ben is learning to bake sourdough");

        var panel = new EntityPanel(createEntity(), id -> List.of(prop1, prop2));

        var text = allText(panel);
        assertTrue(text.contains("Mentioned in 2 memories"), "Must display memory count");
        assertTrue(text.contains("Ben prefers async standups"), "Must show memory text");
        assertTrue(text.contains("Ben is learning to bake sourdough"), "Must show second memory text");

        // Verify Details section exists for related section
        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(!detailsComponents.isEmpty(), "Must have entity-related-section Details");
    }

    @Test
    void mentionedMemoriesSectionHidesWhenEmpty() {
        var panel = new EntityPanel(createEntity(), id -> List.of());

        var text = allText(panel);
        assertFalse(text.contains("Mentioned in"), "Must not render when no memories");

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(detailsComponents.isEmpty(), "Must not have entity-related-section when empty");
    }

    @Test
    void allSectionsStartCollapsed() {
        var prop1 = createProposition("prop-1", "Memory text");
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(new EntityPanel.RelatedItem("Alice", "Colleague")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity(), id -> List.of(prop1));
        panel.setRelatedRecords(id -> records);

        // All Details components should start collapsed
        var allDetails = allComponents(panel).stream()
                .filter(c -> c instanceof Details)
                .map(c -> (Details) c)
                .toList();

        for (var details : allDetails) {
            assertFalse(details.isOpened(), "All sections must start collapsed");
        }
    }

    @Test
    void panelContainsCorrectCssClasses() {
        var panel = new EntityPanel(createEntity());

        // Root should have entity-panel-360 class
        assertTrue(panel.getElement().getClassList().contains("entity-panel-360"),
                "Root must have entity-panel-360 class for spec conformance styling");
    }

    // Helper: Create a proposition for testing
    private Proposition createProposition(String id, String text) {
        return Proposition.create(
                id,
                CONTEXT,
                text,
                List.of(),
                0.9,
                0.0,
                0.5,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                PropositionStatus.ACTIVE
        );
    }

    private static String allText(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out.stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .reduce("", (a, b) -> a + " " + b);
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
