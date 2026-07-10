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

    // F4: Dark-mode colors use CSS custom properties, with a real prefers-color-scheme override
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

        assertTrue(panel.getElement().getClassList().contains("entity-panel-360"),
                "Panel should have entity-panel-360 class for spec styling");

        // The panel must inject a real <style> element carrying both the light-mode custom
        // property definitions AND a prefers-color-scheme: dark override block that redefines
        // them — not just a client-only executeJs call that a unit test can't observe.
        var styleElements = panel.getElement().getChildren()
                .filter(el -> "style".equals(el.getTag()))
                .toList();
        assertTrue(!styleElements.isEmpty(), "Panel must inject a <style> element with dark-mode custom properties");

        var cssText = styleElements.get(0).getText();
        assertTrue(cssText.contains("--entity-amber"), "must define --entity-amber custom property");
        assertTrue(cssText.contains("--entity-violet"), "must define --entity-violet custom property");
        assertTrue(cssText.contains("--entity-green"), "must define --entity-green custom property");
        assertTrue(cssText.contains("@media (prefers-color-scheme: dark)"), "must have a prefers-color-scheme dark override block");
        // The dark block must redefine the same properties, not just repeat the light-mode selector
        var darkBlockStart = cssText.indexOf("@media (prefers-color-scheme: dark)");
        var darkBlock = cssText.substring(darkBlockStart);
        assertTrue(darkBlock.contains("--entity-amber"), "dark override block must redefine --entity-amber");
        assertTrue(darkBlock.contains("--entity-violet"), "dark override block must redefine --entity-violet");
        assertTrue(darkBlock.contains("--entity-green"), "dark override block must redefine --entity-green");

        // Edge-chip dots must reference the CSS custom properties, not a literal hex color
        var dots = allComponents(panel).stream()
                .filter(c -> c instanceof Div && c.getElement().getClassList().contains("entity-edge-chip-dot"))
                .map(c -> c.getElement().getStyle().get("background"))
                .filter(bg -> bg != null && !bg.isBlank())
                .toList();
        assertTrue(dots.stream().anyMatch(bg -> bg.startsWith("var(--entity-")),
                "at least one edge-chip dot must reference var(--entity-...) rather than a literal hex — got: " + dots);
        assertTrue(dots.stream().noneMatch(bg -> bg.matches("#[0-9a-fA-F]{3,8}")),
                "no dot should use a literal hex color — got: " + dots);
    }

    // F3: Section order — compare INDEX POSITIONS of top-level sections, not just presence
    @Test
    void sectionsRenderInSpecOrder() {
        var prop = createProposition("p1", "Memory");
        var records = new EntityPanel.RelatedRecords(
                List.of("fact1"),
                List.of(),
                List.of(new EntityPanel.RelatedItem("Acme Corp", "Client")),
                List.of(),
                List.of(),
                List.of("WORKS_FOR Acme")
        );

        var panel = new EntityPanel(createEntity(), id -> List.of(prop));
        panel.setRelatedRecords(id -> records);

        var children = panel.getChildren().toList();
        int contactIdx = indexOfChildContaining(children, "Contact Facts");
        int relationshipsIdx = indexOfChildContaining(children, "Relationships");
        int mentionedIdx = indexOfChildContaining(children, "Mentioned in");
        int relatedRecordsIdx = indexOfChildContaining(children, "Related records");

        assertTrue(contactIdx >= 0, "Contact Facts must render");
        assertTrue(relationshipsIdx >= 0, "Relationships must render");
        assertTrue(mentionedIdx >= 0, "Mentioned in memories must render");
        assertTrue(relatedRecordsIdx >= 0, "Related records must render");

        assertTrue(contactIdx < relationshipsIdx,
                "Contact Facts (" + contactIdx + ") must come before Relationships (" + relationshipsIdx + ")");
        assertTrue(relationshipsIdx < mentionedIdx,
                "Relationships (" + relationshipsIdx + ") must come before Mentioned in (" + mentionedIdx + ")");
        assertTrue(mentionedIdx < relatedRecordsIdx,
                "Mentioned in (" + mentionedIdx + ") must come before Related records (" + relatedRecordsIdx + ")");
    }

    /** Index of the first top-level child of {@code children} whose own subtree text contains {@code needle}. */
    private static int indexOfChildContaining(List<Component> children, String needle) {
        for (int i = 0; i < children.size(); i++) {
            if (allText(children.get(i)).contains(needle)) {
                return i;
            }
        }
        return -1;
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
