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
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the openEditor(propositionId) pass-through end to end:
 * MemorySection.openEditor -> PropositionsPanel.openEditor -> PropositionCard.openEditor
 * with real component rendering and state verification.
 *
 * Confirms that:
 * - A card with matching ID is found and its editor opens
 * - Other cards do NOT enter edit state
 * - Nonexistent IDs return false without exception or state change
 */
class PanelOpenEditorTest {

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn("alice-1");
        when(entity.getName()).thenReturn("Alice Chen");
        when(entity.getDescription()).thenReturn("Senior Engineer");
        when(entity.labels()).thenReturn(Set.of("Person"));
        return entity;
    }

    private PropositionRepository mockRepo() {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(List.of());
        return repo;
    }

    private Proposition createProposition(String id, String text) {
        return Proposition.create(
                id, "ctx-1", text,
                List.of(), 0.9, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
    }

    private static SimilarityResult<Proposition> mockSimilarityResult(Proposition proposition, double score) {
        var mock = mock(SimilarityResult.class);
        doReturn(proposition).when(mock).getMatch();
        doReturn(score).when(mock).getScore();
        return mock;
    }

    /**
     * Calls openEditor on the second proposition and verifies only that card enters edit state.
     * Other cards must remain in display state (header visible, no edit area).
     */
    @Test
    void openEditorOpensSpecificCardByPropositionId() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            ui.add(memorySection);

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a PropositionsPanel in MemorySection"));

            // Create and render 3 propositions in scored mode
            var prop1 = createProposition("prop-1", "Jim lives in Brisbane");
            var prop2 = createProposition("prop-2", "Alice works in Melbourne");
            var prop3 = createProposition("prop-3", "Bob studies in Sydney");

            var results = List.of(
                    mockSimilarityResult(prop1, 0.95),
                    mockSimilarityResult(prop2, 0.90),
                    mockSimilarityResult(prop3, 0.85)
            );
            panel.showScoredPropositions(results);

            // Find all rendered cards
            var allCards = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .toList();
            assertEquals(3, allCards.size(), "must have 3 rendered cards");

            // Find the second card (prop-2)
            var card2 = allCards.stream()
                    .filter(c -> c.getProposition().getId().equals("prop-2"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected to find card for prop-2"));

            // Before opening editor, no card should have edit state
            assertNoCardInEditState(memorySection);

            // Open editor for the second proposition
            var result = memorySection.openEditor("prop-2");
            assertTrue(result, "openEditor must return true when ID is found");

            // Verify only the second card is in edit state
            var editAreas = allComponents(card2).stream()
                    .filter(c -> c instanceof TextArea && c.hasClassName("proposition-edit-area"))
                    .toList();
            assertEquals(1, editAreas.size(), "the target card must have an edit area visible");

            // Verify other cards are NOT in edit state
            var otherCards = allCards.stream()
                    .filter(c -> !c.getProposition().getId().equals("prop-2"))
                    .toList();
            for (var otherCard : otherCards) {
                var otherEditAreas = allComponents(otherCard).stream()
                        .filter(c -> c instanceof TextArea && c.hasClassName("proposition-edit-area"))
                        .toList();
                assertEquals(0, otherEditAreas.size(),
                        "other cards must NOT have edit area: " + otherCard.getProposition().getId());
            }
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Nonexistent proposition ID returns false, does not throw, does not change card state.
     */
    @Test
    void openEditorNonexistentIdReturnsFalseAndChangesNothing() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            ui.add(memorySection);

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            // Create and render 2 propositions
            var prop1 = createProposition("prop-1", "Jim lives in Brisbane");
            var prop2 = createProposition("prop-2", "Alice works in Melbourne");

            var results = List.of(
                    mockSimilarityResult(prop1, 0.95),
                    mockSimilarityResult(prop2, 0.90)
            );
            panel.showScoredPropositions(results);

            assertNoCardInEditState(memorySection);

            // Attempt to open editor for nonexistent ID
            var result = memorySection.openEditor("nonexistent");
            assertFalse(result, "openEditor must return false when ID not found");

            // State must not have changed: no card in edit state
            assertNoCardInEditState(memorySection);
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Direct call on PropositionCard and layer-forwarding through PropositionsPanel
     * both work correctly and return true.
     */
    @Test
    void openEditorForwardingThroughPanelLayer() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            ui.add(memorySection);

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            // Render a single proposition
            var prop1 = createProposition("the-id", "Test memory");
            var results = List.of(mockSimilarityResult(prop1, 0.95));
            panel.showScoredPropositions(results);

            var card = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow();

            // Call through PropositionsPanel
            var panelResult = panel.openEditor("the-id");
            assertTrue(panelResult, "panel.openEditor must return true for existing ID");

            var editAreas = allComponents(card).stream()
                    .filter(c -> c instanceof TextArea && c.hasClassName("proposition-edit-area"))
                    .toList();
            assertEquals(1, editAreas.size(), "panel.openEditor must open edit area");

            // Verify the edit area has the correct text
            var editArea = (TextArea) editAreas.get(0);
            assertEquals("Test memory", editArea.getValue(), "edit area must contain the proposition text");
        } finally {
            UI.setCurrent(null);
        }
    }

    private void assertNoCardInEditState(Component root) {
        var editAreas = allComponents(root).stream()
                .filter(c -> c instanceof TextArea && c.hasClassName("proposition-edit-area"))
                .toList();
        assertEquals(0, editAreas.size(), "no card should be in edit state");
    }

    /**
     * Set up a live {@link UI} so components that need one can attach.
     */
    private static UI withUi() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
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
