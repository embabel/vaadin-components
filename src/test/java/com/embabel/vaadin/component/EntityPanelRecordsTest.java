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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@link EntityPanel} renders related-records sections (contact facts, people,
 * organizations, emails, meetings, edge chips) when a relatedRecordsLoader is provided
 * and returns non-empty sections. Empty categories render no section (scope guard).
 */
class EntityPanelRecordsTest {

    private static final String ENTITY_ID = "entity-1";
    private static final String ENTITY_NAME = "Ben Blossom";
    private static final String ENTITY_TYPE = "Person";

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(entity.getName()).thenReturn(ENTITY_NAME);
        when(entity.getDescription()).thenReturn("Robotics engineer");
        when(entity.labels()).thenReturn(Set.of(ENTITY_TYPE));
        return entity;
    }

    @Test
    void rendersAllNonEmptySectionsFromRelatedRecords() {
        var records = new RelatedRecords(
                List.of("ben.blossom@photonix.example", "Robotics Engineer II"),
                List.of(
                        new RelatedItem("Alice Chen", "Team lead"),
                        new RelatedItem("Bob Smith", "Colleague")
                ),
                List.of(new RelatedItem("Photonix Robotics", "Employer")),
                List.of(
                        new RelatedItem("confirming: switching the team standup to async", "Jun 21"),
                        new RelatedItem("floor relocation — new desk assignment", "Jun 10")
                ),
                List.of(new RelatedItem("eng-robotics weekly standup", "Recurring")),
                List.of("WORKS_FOR Photonix Robotics", "HAS_EMAIL ben.blossom@photonix.example", "ATTENDS eng-robotics standup")
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        // Contact facts section
        assertTrue(text.contains("Contact Facts"), "must render Contact Facts section");
        assertTrue(text.contains("ben.blossom@photonix.example"), "must show contact fact");
        assertTrue(text.contains("Robotics Engineer II"), "must show job title");

        // People section
        assertTrue(text.contains("People"), "must render People section");
        assertTrue(text.contains("Alice Chen"), "must show person 1");
        assertTrue(text.contains("Team lead"), "must show person 1 subtitle");
        assertTrue(text.contains("Bob Smith"), "must show person 2");

        // Organizations section
        assertTrue(text.contains("Organizations"), "must render Organizations section");
        assertTrue(text.contains("Photonix Robotics"), "must show org");
        assertTrue(text.contains("Employer"), "must show org subtitle");

        // Emails section
        assertTrue(text.contains("Emails"), "must render Emails section");
        assertTrue(text.contains("confirming: switching the team standup to async"), "must show email 1");
        assertTrue(text.contains("Jun 21"), "must show email 1 date");

        // Meetings section
        assertTrue(text.contains("Meetings"), "must render Meetings section");
        assertTrue(text.contains("eng-robotics weekly standup"), "must show meeting");
        assertTrue(text.contains("Recurring"), "must show meeting recurrence");

        // Edge chips section
        assertTrue(text.contains("Relationships"), "must render Relationships (edge chips) section");
        assertTrue(text.contains("WORKS_FOR Photonix Robotics"), "must show edge chip");
        assertTrue(text.contains("HAS_EMAIL"), "must show email edge");
        assertTrue(text.contains("ATTENDS"), "must show attends edge");

        // All sections should be Details components with entity-section class
        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.size() == 6, "must have 6 entity-section Details (facts, people, orgs, emails, meetings, edges) — got: " + entitySections.size());
    }

    @Test
    void rendersOnlyNonEmptySections() {
        // Only organizations, no facts, people, emails, meetings, or chips
        var records = new RelatedRecords(
                List.of(),
                List.of(),
                List.of(new RelatedItem("Acme Corp", "Client")),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        // Only organizations should be rendered
        assertTrue(text.contains("Organizations"), "must render Organizations section");
        assertTrue(text.contains("Acme Corp"), "must show org");

        // Other sections should NOT be rendered
        assertFalse(text.contains("Contact Facts"), "must not render empty Contact Facts section");
        assertFalse(text.contains("People"), "must not render empty People section");
        assertFalse(text.contains("Emails"), "must not render empty Emails section");
        assertFalse(text.contains("Meetings"), "must not render empty Meetings section");
        assertFalse(text.contains("Relationships"), "must not render empty Relationships section");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.size() == 1, "must have exactly 1 entity-section (orgs only) — got: " + entitySections.size());
    }

    @Test
    void rendersContactFactsAndEdgeChipsOnly() {
        var records = new RelatedRecords(
                List.of("fact1@example.com", "Fact 2"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("WORKS_FOR BigCorp")
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        assertTrue(text.contains("Contact Facts"), "must render Contact Facts");
        assertTrue(text.contains("fact1@example.com"), "must show fact 1");
        assertTrue(text.contains("Fact 2"), "must show fact 2");

        assertTrue(text.contains("Relationships"), "must render Relationships (edge chips)");
        assertTrue(text.contains("WORKS_FOR BigCorp"), "must show edge chip");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.size() == 2, "must have exactly 2 entity-section (facts + edges) — got: " + entitySections.size());
    }

    @Test
    void rendersNoSectionsWhenAllEmpty() {
        var records = new RelatedRecords(
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

        assertFalse(text.contains("Contact Facts"), "must not render any sections");
        assertFalse(text.contains("People"), "must not render any sections");
        assertFalse(text.contains("Organizations"), "must not render any sections");
        assertFalse(text.contains("Emails"), "must not render any sections");
        assertFalse(text.contains("Meetings"), "must not render any sections");
        assertFalse(text.contains("Relationships"), "must not render any sections");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.isEmpty(), "must have no entity-section when all empty");
    }

    @Test
    void rendersNoSectionsWhenLoaderReturnsNull() {
        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> null);

        var text = allText(panel);

        assertFalse(text.contains("Contact Facts"), "must not render sections when loader returns null");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.isEmpty(), "must have no entity-section when loader returns null");
    }

    @Test
    void rendersNoSectionsWhenLoaderIsNull() {
        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(null);

        var text = allText(panel);

        assertFalse(text.contains("Contact Facts"), "must not render sections when loader is null");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.isEmpty(), "must have no entity-section when loader is null");
    }

    @Test
    void relatedSectionsStartCollapsed() {
        var records = new RelatedRecords(
                List.of("fact1"),
                List.of(new RelatedItem("Alice", "Colleague")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .map(c -> (Details) c)
                .toList();

        assertTrue(!detailsComponents.isEmpty(), "must have Details sections");
        for (var details : detailsComponents) {
            assertFalse(details.isOpened(), "All Details must start collapsed — got opened: " + details.isOpened());
        }
    }

    @Test
    void relatedItemsRenderWithTitleAndSubtitle() {
        var records = new RelatedRecords(
                List.of(),
                List.of(new RelatedItem("Alice Chen", "Senior Engineer")),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        assertTrue(text.contains("Alice Chen"), "must show title");
        assertTrue(text.contains("Senior Engineer"), "must show subtitle");
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
