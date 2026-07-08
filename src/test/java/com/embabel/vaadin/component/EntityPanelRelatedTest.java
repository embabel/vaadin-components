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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves {@link EntityPanel} renders a "Mentioned in N memories" section when a
 * relatedPropositionsLoader is provided and returns non-empty results. Loader returns
 * an empty list or null → no section.
 */
class EntityPanelRelatedTest {

    private static final String ENTITY_ID = "entity-1";
    private static final String ENTITY_NAME = "Jim";
    private static final String CONTEXT = "ctx-1";

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(entity.getName()).thenReturn(ENTITY_NAME);
        when(entity.getDescription()).thenReturn("A person");
        when(entity.labels()).thenReturn(Set.of("Person"));
        return entity;
    }

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

    @Test
    void rendersRelatedSectionWhenLoaderReturnsTwoPropositions() {
        var prop1 = createProposition("prop-1", "Jim lives in Brisbane");
        var prop2 = createProposition("prop-2", "Jim works as an engineer");

        var panel = new EntityPanel(createEntity(), id -> List.of(prop1, prop2));

        var text = allText(panel);
        assertTrue(text.contains("Mentioned in 2 memories"), "must show count summary — got: " + text);
        assertTrue(text.contains("Jim lives in Brisbane"), "must show proposition 1 text — got: " + text);
        assertTrue(text.contains("Jim works as an engineer"), "must show proposition 2 text — got: " + text);

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(!detailsComponents.isEmpty(), "must have a Details with class 'entity-related-section'");

        var relatedItems = allComponents(panel).stream()
                .filter(c -> c instanceof Span && c.getElement().getClassList().contains("entity-related-item"))
                .toList();
        assertTrue(relatedItems.size() == 2, "must have exactly 2 Spans with class 'entity-related-item' — got: " + relatedItems.size());
    }

    @Test
    void rendersRelatedSectionWithSingularWhenLoaderReturnsOneProposition() {
        var prop1 = createProposition("prop-1", "Jim lives in Brisbane");

        var panel = new EntityPanel(createEntity(), id -> List.of(prop1));

        var text = allText(panel);
        assertTrue(text.contains("Mentioned in 1 memory"), "must use singular 'memory' — got: " + text);
    }

    @Test
    void rendersNoSectionWhenLoaderReturnsEmptyList() {
        var panel = new EntityPanel(createEntity(), id -> List.of());

        var text = allText(panel);
        assertFalse(text.contains("Mentioned in"), "must not show section when empty — got: " + text);

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(detailsComponents.isEmpty(), "must have no entity-related-section when empty");
    }

    @Test
    void rendersNoSectionWhenLoaderReturnsNull() {
        var panel = new EntityPanel(createEntity(), id -> null);

        var text = allText(panel);
        assertFalse(text.contains("Mentioned in"), "must not show section when null — got: " + text);

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(detailsComponents.isEmpty(), "must have no entity-related-section when null");
    }

    @Test
    void rendersNoSectionWhenLoaderIsNull() {
        var panel = new EntityPanel(createEntity());

        var text = allText(panel);
        assertFalse(text.contains("Mentioned in"), "must not show section when no loader — got: " + text);

        var detailsComponents = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .toList();
        assertTrue(detailsComponents.isEmpty(), "must have no entity-related-section when no loader");
    }

    @Test
    void relatedSectionStartsCollapsed() {
        var prop1 = createProposition("prop-1", "Jim lives in Brisbane");

        var panel = new EntityPanel(createEntity(), id -> List.of(prop1));

        var detailsComponent = allComponents(panel).stream()
                .filter(c -> c instanceof Details && c.getElement().getClassList().contains("entity-related-section"))
                .map(c -> (Details) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("entity-related-section not found"));

        assertFalse(detailsComponent.isOpened(), "Details must start collapsed");
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
