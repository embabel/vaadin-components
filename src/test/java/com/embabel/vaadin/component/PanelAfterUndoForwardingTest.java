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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the setOnAfterUndo pass-through end to end:
 * MemorySection.setOnAfterUndo -> PropositionsPanel.setOnAfterUndo -> PropositionsPanel.createCard
 * wires it onto every PropositionCard -> PropositionCard.showLineageDialog hands it to LineageSection.setOnAfterUndo
 * -> LineageSection fires the callback after undo completes and fresh re-render is visible.
 *
 * Every step here runs real production code. The only seams added for testability are:
 * - PropositionCard is created by the panel (createCard method)
 * - LineageSection is created and shown in the dialog (real showLineageDialog path)
 * - Undo buttons are clicked to trigger the callback (real button click)
 */
class PanelAfterUndoForwardingTest {

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

    private Proposition survivorProp() {
        return Proposition.create(
                "survivor-1", "ctx-1", "Jim lives in Brisbane",
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
     * Drives the whole chain from MemorySection: an onAfterUndo callback is set only on the MemorySection,
     * never on any panel or card directly. If MemorySection.setOnAfterUndo didn't forward to PropositionsPanel,
     * or if PropositionsPanel.createCard stopped calling card.setOnAfterUndo(...), or if PropositionCard.showLineageDialog
     * stopped forwarding it to LineageSection.setOnAfterUndo, or if the undo button click didn't invoke the callback,
     * this test would fail.
     */
    @Test
    void memorySectionWiresAfterUndoCallbackThroughPanelAndCardIntoLineageDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var capturedSurvivorId = new AtomicReference<String>();
            var capturedRetiredId = new AtomicReference<String>();

            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            // Set the after-undo callback only on the MemorySection
            memorySection.setOnAfterUndo((survivorId, retiredId) -> {
                capturedSurvivorId.set(survivorId);
                capturedRetiredId.set(retiredId);
            });

            // Set up a lineage provider with a collapse explanation (so the lineage badge appears)
            var member = new CollapseExplanation.RetiredMember(
                    "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
            var lineage = new LineageProvider.Lineage(
                    List.of(),
                    List.of(),
                    Optional.of(explanation));

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a PropositionsPanel in MemorySection"));

            // Set up lineage provider so the lineage badge appears
            panel.setLineageProvider(id -> Optional.of(lineage));

            // Set up onUndoMember to simulate the backend restore
            panel.setOnUndoMember((survivorId, retiredId) -> {
                // Simulate successful restore; the lineage provider will return fresh data
            });

            ui.add(memorySection);

            var result = mockSimilarityResult(survivorProp(), 0.9);
            panel.showScoredPropositions(List.of(result));

            // Find the rendered card
            var card = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a rendered PropositionCard"));

            // Find and click the lineage badge (this is what the user clicks to see lineage)
            var lineageBadges = allComponents(card).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-badge"))
                    .map(c -> (Button) c)
                    .toList();
            assertEquals(1, lineageBadges.size(), "must have one lineage badge");

            var badge = lineageBadges.get(0);
            badge.click();
            // Dialog attachment is deferred to the "before client response" phase
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            // Find the lineage dialog that opened
            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected lineage dialog to open"));
            assertTrue(dialog.isOpened(), "lineage dialog must be open");

            // Find the undo button
            var undoButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-undo-member"))
                    .map(c -> (Button) c)
                    .toList();
            assertEquals(1, undoButtons.size(), "must have one undo button for the retired member");

            // Click the undo button
            var undoButton = undoButtons.get(0);
            undoButton.click();

            // Verify the callback was invoked with the correct IDs
            assertNotNull(capturedSurvivorId.get(), "survivor id must be captured");
            assertEquals("survivor-1", capturedSurvivorId.get(), "callback must receive the correct survivor id");
            assertNotNull(capturedRetiredId.get(), "retired id must be captured");
            assertEquals("retired-1", capturedRetiredId.get(), "callback must receive the correct retired member id");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Same chain, but with no callback set on the MemorySection. Confirms undo still works without NPE.
     */
    @Test
    void noCallbackMeansUndoStillWorksWithoutNpe() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);
            // No setOnAfterUndo call

            // Set up lineage provider with collapse explanation
            var member = new CollapseExplanation.RetiredMember(
                    "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
            var lineage = new LineageProvider.Lineage(
                    List.of(),
                    List.of(),
                    Optional.of(explanation));

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            panel.setLineageProvider(id -> Optional.of(lineage));
            panel.setOnUndoMember((survivorId, retiredId) -> {
                // Simulate successful restore
            });
            ui.add(memorySection);

            var result = mockSimilarityResult(survivorProp(), 0.9);
            panel.showScoredPropositions(List.of(result));

            var card = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow();

            var lineageBadges = allComponents(card).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-badge"))
                    .map(c -> (Button) c)
                    .toList();

            var badge = lineageBadges.get(0);
            badge.click();
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow();

            var undoButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-undo-member"))
                    .map(c -> (Button) c)
                    .toList();

            // Click the undo button — this must not throw NPE even though no callback is set
            var undoButton = undoButtons.get(0);
            undoButton.click();

            // If we got here without exception, the test passes
            assertTrue(true, "undo must work without NPE when no callback is set");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Proves the callback fires after the fresh re-render is visible
     * (by verifying undo button count changes by the time callback fires).
     */
    @Test
    void afterUndoCallbackFiresAfterFreshRerender() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            var undoButtonsVisibleWhenCallbackFires = new AtomicInteger(-1);

            // Set up a mutable list of retired members so the provider can return fresh data
            var member = new CollapseExplanation.RetiredMember(
                    "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
            var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member));

            LineageProvider provider = id -> {
                var explanation = new CollapseExplanation(
                        "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
                return Optional.of(new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation)));
            };

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            panel.setLineageProvider(provider);
            // When undo is clicked, simulate the backend restore by removing from retired list
            panel.setOnUndoMember((survivorId, retiredId) -> retired.removeIf(m -> m.propositionId().equals(retiredId)));

            // Set the after-undo callback to capture state at the moment it fires
            memorySection.setOnAfterUndo((survivorId, retiredId) -> {
                // By the time this fires, the fresh re-render must be visible
                // The retired member should be gone from the fresh lineage, so no undo buttons remain
                undoButtonsVisibleWhenCallbackFires.set(findButtonsByClass(allComponents(memorySection), "lineage-undo-member").size());
            });

            ui.add(memorySection);

            var result = mockSimilarityResult(survivorProp(), 0.9);
            panel.showScoredPropositions(List.of(result));

            var card = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow();

            var lineageBadges = allComponents(card).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-badge"))
                    .map(c -> (Button) c)
                    .toList();

            var badge = lineageBadges.get(0);
            badge.click();
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow();

            var undoButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-undo-member"))
                    .map(c -> (Button) c)
                    .toList();
            assertEquals(1, undoButtons.size(), "must have one undo button before clicking");

            var undoButton = undoButtons.get(0);
            undoButton.click();

            // The callback should have seen the re-rendered state with no undo buttons
            assertEquals(0, undoButtonsVisibleWhenCallbackFires.get(),
                    "by the time onAfterUndo fires, the re-render should show no undo buttons (retired member gone from fresh lineage)");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Set up a live {@link UI} so components that need one (dialogs) can attach.
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

    private static List<Button> findButtonsByClass(List<Component> components, String className) {
        var buttons = new ArrayList<Button>();
        for (var c : components) {
            if (c instanceof Button && c.hasClassName(className)) {
                buttons.add((Button) c);
            }
        }
        return buttons;
    }
}
