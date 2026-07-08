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
 * Proves the setOnUndoMember pass-through end to end:
 * PropositionsPanel.setOnUndoMember -&gt; PropositionsPanel.createCard wires it onto
 * every PropositionCard it builds -&gt; PropositionCard.showLineageDialog (called when the lineage
 * badge is clicked) hands it to LineageSection.setOnUndoMember -&gt; LineageSection renders undo
 * buttons and invokes the callback when clicked.
 *
 * Every step here runs real production code. The only seams added for testability are:
 * - PropositionCard is created by the panel (createCard method)
 * - LineageSection is created and shown in the dialog (real showLineageDialog path)
 * - Undo buttons are clicked to trigger the callback (real button click)
 */
class PropositionsPanelUndoMemberTest {

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
     * Drives the whole chain: an onUndoMember callback is set only on the panel, never on any card
     * directly. If PropositionsPanel.createCard stopped calling card.setOnUndoMember(...), or if
     * PropositionCard.showLineageDialog stopped forwarding it to LineageSection.setOnUndoMember,
     * or if the undo button click didn't invoke the callback, this test would fail.
     */
    @Test
    void panelWiresUndoCallbackThroughCardIntoLineageDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var capturedSurvivorId = new AtomicReference<String>();
            var capturedRetiredId = new AtomicReference<String>();

            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;
            var panel = new PropositionsPanel(repo, entityResolver);

            // Set the undo callback only on the panel
            panel.setOnUndoMember((survivorId, retiredId) -> {
                capturedSurvivorId.set(survivorId);
                capturedRetiredId.set(retiredId);
            });

            // Set up lineage provider so the lineage badge appears
            var member = new CollapseExplanation.RetiredMember(
                    "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
            var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

            panel.setLineageProvider(id -> Optional.of(lineage));
            ui.add(panel);

            var result = mockSimilarityResult(survivorProp(), 0.9);
            panel.showScoredPropositions(List.of(result));

            // Find the rendered card
            var card = allComponents(panel).stream()
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

            // Find the undo button in the lineage section
            var undoButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("lineage-undo-member"))
                    .map(c -> (Button) c)
                    .toList();
            assertEquals(1, undoButtons.size(), "must have one undo button for the retired member");

            // Click the undo button
            var undoButton = undoButtons.get(0);
            undoButton.click();

            // Verify the callback was invoked with the correct ids
            assertNotNull(capturedSurvivorId.get(), "survivor id must be captured");
            assertNotNull(capturedRetiredId.get(), "retired id must be captured");
            assertEquals("survivor-1", capturedSurvivorId.get(), "callback must receive correct survivor id");
            assertEquals("retired-1", capturedRetiredId.get(), "callback must receive correct retired member id");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Same chain, but with no callback set on the panel. Confirms the undo buttons are
     * genuinely conditional on the pass-through, not always rendered.
     */
    @Test
    void noCallbackMeansNoUndoButtonsInLineageDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;
            var panel = new PropositionsPanel(repo, entityResolver);
            // No setOnUndoMember call

            // Set up lineage provider so the lineage badge appears
            var member = new CollapseExplanation.RetiredMember(
                    "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
            var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

            panel.setLineageProvider(id -> Optional.of(lineage));
            ui.add(panel);

            var result = mockSimilarityResult(survivorProp(), 0.9);
            panel.showScoredPropositions(List.of(result));

            var card = allComponents(panel).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a rendered PropositionCard"));

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

            assertTrue(undoButtons.isEmpty(), "no undo buttons when callback not set on panel");
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
}
