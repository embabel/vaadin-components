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
 * Proves the setOnOpenRef and setOpenable pass-through end to end:
 * MemorySection.setOnOpenRef -&gt; PropositionsPanel.setOnOpenRef -&gt; PropositionsPanel.createCard
 * wires it onto every PropositionCard -&gt; PropositionCard.showLineageDialog hands it to LineageSection.setOnOpenRef
 * -&gt; LineageSection renders Open buttons and invokes the callback when clicked.
 *
 * Every step here runs real production code. The only seams added for testability are:
 * - PropositionCard is created by the panel (createCard method)
 * - LineageSection is created and shown in the dialog (real showLineageDialog path)
 * - Open buttons are clicked to trigger the callback (real button click)
 */
class PanelOpenRefForwardingTest {

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
     * Drives the whole chain from MemorySection: an onOpenRef callback is set only on the MemorySection,
     * never on any panel or card directly. If MemorySection.setOnOpenRef didn't forward to PropositionsPanel,
     * or if PropositionsPanel.createCard stopped calling card.setOnOpenRef(...), or if PropositionCard.showLineageDialog
     * stopped forwarding it to LineageSection.setOnOpenRef, or if the open button click didn't invoke the callback,
     * this test would fail.
     */
    @Test
    void memorySectionWiresOpenRefCallbackThroughPanelAndCardIntoLineageDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var capturedRef = new AtomicReference<String>();

            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            // Set the open-ref callback only on the MemorySection
            memorySection.setOnOpenRef(capturedRef::set);

            // Set up lineage provider so the lineage badge appears
            var lineage = new LineageProvider.Lineage(
                    List.of("email:u1:t1"),
                    List.of(),
                    Optional.empty());

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a PropositionsPanel in MemorySection"));

            panel.setLineageProvider(id -> Optional.of(lineage));
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

            // Find the grounding Open button
            var openButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("ref-go"))
                    .map(c -> (Button) c)
                    .toList();
            assertEquals(1, openButtons.size(), "must have one open button for the grounding ref");

            // Click the open button
            var openButton = openButtons.get(0);
            openButton.click();

            // Verify the callback was invoked with the correct ref
            assertNotNull(capturedRef.get(), "ref must be captured");
            assertEquals("email:u1:t1", capturedRef.get(), "callback must receive the correct ref");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Same chain, but with no callback set on the MemorySection. Confirms the open buttons are
     * genuinely conditional on the pass-through, not always rendered.
     */
    @Test
    void noCallbackMeansNoOpenButtonsInLineageDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);
            // No setOnOpenRef call

            // Set up lineage provider so the lineage badge appears
            var lineage = new LineageProvider.Lineage(
                    List.of("email:u1:t1"),
                    List.of(),
                    Optional.empty());

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            panel.setLineageProvider(id -> Optional.of(lineage));
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

            var openButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("ref-go"))
                    .map(c -> (Button) c)
                    .toList();

            assertTrue(openButtons.isEmpty(), "no open buttons when callback not set on memory section");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Test that the openable predicate is forwarded through the layers and filters refs.
     * Only email: refs should have open buttons when the predicate allows only them.
     */
    @Test
    void openablePredicateForwardedThroughMemorySectionAndPanel() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", null, null, null);

            var capturedRef = new AtomicReference<String>();
            memorySection.setOnOpenRef(capturedRef::set);
            // Only email: refs are openable
            memorySection.setOpenable(ref -> ref.startsWith("email:"));

            // Set up lineage with both email and content refs
            var lineage = new LineageProvider.Lineage(
                    List.of("email:u1:t1"),
                    List.of(new LineageProvider.Lineage.ProvenanceRef("CAL", "content:abc", "chunk 3")),
                    Optional.empty());

            var panel = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionsPanel)
                    .map(c -> (PropositionsPanel) c)
                    .findFirst()
                    .orElseThrow();

            panel.setLineageProvider(id -> Optional.of(lineage));
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

            lineageBadges.get(0).click();
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow();

            var openButtons = allComponents(dialog).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("ref-go"))
                    .map(c -> (Button) c)
                    .toList();

            // Only one open button: the email: grounding ref. content: ref should not have one.
            assertEquals(1, openButtons.size(), "only email: ref should have open button when predicate restricts to email:");
            openButtons.get(0).click();
            assertEquals("email:u1:t1", capturedRef.get(), "clicked button should pass email: ref to callback");
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
