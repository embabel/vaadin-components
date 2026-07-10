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
        var records = new EntityPanel.RelatedRecords(
                List.of("ben.blossom@photonix.example", "Robotics Engineer II"),
                List.of(
                        new EntityPanel.RelatedItem("Alice Chen", "Team lead"),
                        new EntityPanel.RelatedItem("Bob Smith", "Colleague")
                ),
                List.of(new EntityPanel.RelatedItem("Photonix Robotics", "Employer")),
                List.of(
                        new EntityPanel.RelatedItem("confirming: switching the team standup to async", "Jun 21"),
                        new EntityPanel.RelatedItem("floor relocation — new desk assignment", "Jun 10")
                ),
                List.of(new EntityPanel.RelatedItem("eng-robotics weekly standup", "Recurring")),
                List.of("WORKS_FOR Photonix Robotics", "HAS_EMAIL ben.blossom@photonix.example", "ATTENDS eng-robotics standup")
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        // Contact facts section
        assertTrue(text.contains("Contact Facts"), "must render Contact Facts section");
        assertTrue(text.contains("ben.blossom@photonix.example"), "must show contact fact");
        assertTrue(text.contains("Robotics Engineer II"), "must show job title");

        // People section (its own top-level section, per the spec's own layout)
        assertTrue(text.contains("People"), "must render People section");
        assertTrue(text.contains("Alice Chen"), "must show person 1");
        assertTrue(text.contains("Team lead"), "must show person 1 subtitle");
        assertTrue(text.contains("Bob Smith"), "must show person 2");

        // "Related records" umbrella section, containing Emails / Meetings / Orgs sub-groups
        assertTrue(text.contains("Related records"), "must render the Related records umbrella section");
        assertTrue(text.contains("Emails (2)"), "must show Emails sub-group header with count");
        assertTrue(text.contains("confirming: switching the team standup to async"), "must show email 1");
        assertTrue(text.contains("Jun 21"), "must show email 1 date");

        assertTrue(text.contains("Meetings (1)"), "must show Meetings sub-group header with count");
        assertTrue(text.contains("eng-robotics weekly standup"), "must show meeting");
        assertTrue(text.contains("Recurring"), "must show meeting recurrence");

        assertTrue(text.contains("Orgs (1)"), "must show Orgs sub-group header with count");
        assertTrue(text.contains("Photonix Robotics"), "must show org");
        assertTrue(text.contains("Employer"), "must show org subtitle");

        // Old flat top-level "Organizations"/"Emails"/"Meetings" Details sections are gone —
        // those record types now render only as sub-groups inside the umbrella.
        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .map(c -> (Details) c)
                .toList();
        var topLevelSummaries = entitySections.stream().map(Details::getSummaryText).toList();
        assertFalse(topLevelSummaries.contains("Organizations"), "must not have a standalone Organizations section — got: " + topLevelSummaries);
        assertFalse(topLevelSummaries.contains("Emails"), "must not have a standalone Emails section — got: " + topLevelSummaries);
        assertFalse(topLevelSummaries.contains("Meetings"), "must not have a standalone Meetings section — got: " + topLevelSummaries);

        // Edge chips section
        assertTrue(text.contains("Relationships"), "must render Relationships (edge chips) section");
        assertTrue(text.contains("WORKS_FOR Photonix Robotics"), "must show edge chip");
        assertTrue(text.contains("HAS_EMAIL"), "must show email edge");
        assertTrue(text.contains("ATTENDS"), "must show attends edge");

        // Contact Facts, Relationships, People, Related records(umbrella) — 4 top-level
        // entity-section Details (Emails/Meetings/Orgs now live INSIDE the umbrella, not as
        // separate top-level sections).
        assertTrue(entitySections.size() == 4,
                "must have 4 entity-section Details (facts, relationships, people, related-records-umbrella) — got: " + entitySections.size());

        var umbrella = entitySections.stream()
                .filter(c -> c.getElement().getClassList().contains("entity-related-records-section"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("must have an entity-related-records-section Details"));
        var groupHeaders = allComponents(umbrella).stream()
                .filter(c -> c instanceof Span && c.getElement().getClassList().contains("entity-related-group-header"))
                .map(c -> ((Span) c).getText())
                .toList();
        assertTrue(groupHeaders.size() == 3, "Related records must have 3 sub-groups (Emails, Meetings, Orgs) — got: " + groupHeaders);
    }

    @Test
    void rendersOnlyNonEmptySections() {
        // Only organizations, no facts, people, emails, meetings, or chips
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(new EntityPanel.RelatedItem("Acme Corp", "Client")),
                List.of(),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var text = allText(panel);

        // Only the Related records umbrella (with its Orgs sub-group) should be rendered
        assertTrue(text.contains("Related records"), "must render the Related records umbrella section");
        assertTrue(text.contains("Orgs (1)"), "must show Orgs sub-group header with count");
        assertTrue(text.contains("Acme Corp"), "must show org");

        // Other sections should NOT be rendered
        assertFalse(text.contains("Contact Facts"), "must not render empty Contact Facts section");
        assertFalse(text.contains("People"), "must not render empty People section");
        assertFalse(text.contains("Emails ("), "must not render an Emails sub-group when empty");
        assertFalse(text.contains("Meetings ("), "must not render a Meetings sub-group when empty");
        assertFalse(text.contains("Relationships"), "must not render empty Relationships section");

        var entitySections = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-section"))
                .toList();
        assertTrue(entitySections.size() == 1, "must have exactly 1 entity-section (the Related records umbrella) — got: " + entitySections.size());
    }

    @Test
    void rendersContactFactsAndEdgeChipsOnly() {
        var records = new EntityPanel.RelatedRecords(
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
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(new EntityPanel.RelatedItem("Alice", "Colleague")),
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

        assertTrue(text.contains("Alice Chen"), "must show title");
        assertTrue(text.contains("Senior Engineer"), "must show subtitle");
    }

    @Test
    void openableRelatedItemFiresOnOpenRelatedItemWithSourceKey() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(),
                List.of(new EntityPanel.RelatedItem("Pricing model", "Jun 21", "email:u1:thread-1")),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        var opened = new ArrayList<String>();
        panel.setOnOpenRelatedItem(opened::add);
        panel.setRelatedRecords(id -> records);

        var emailRow = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("entity-related-item-openable"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("must render an openable related-item row"));

        com.vaadin.flow.component.ComponentUtil.fireEvent(
                emailRow,
                new com.vaadin.flow.component.ClickEvent<>(emailRow));

        assertTrue(opened.contains("email:u1:thread-1"), "click must fire onOpenRelatedItem with the row's sourceKey");
    }

    @Test
    void relatedItemWithoutOnOpenRelatedItemCallbackIsNotOpenable() {
        var records = new EntityPanel.RelatedRecords(
                List.of(),
                List.of(),
                List.of(),
                List.of(new EntityPanel.RelatedItem("Pricing model", "Jun 21", "email:u1:thread-1")),
                List.of(),
                List.of()
        );

        var panel = new EntityPanel(createEntity());
        panel.setRelatedRecords(id -> records);

        var openable = allComponents(panel).stream()
                .anyMatch(c -> c.getElement().getClassList().contains("entity-related-item-openable"));
        assertFalse(openable, "row must not be openable without a registered onOpenRelatedItem callback");
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
