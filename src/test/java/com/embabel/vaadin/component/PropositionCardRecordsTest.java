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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the real related-records loader pass-through end to end:
 * PropositionsPanel.setRelatedRecordsLoader -&gt; PropositionsPanel.createCard wires it onto
 * every PropositionCard it builds -&gt; PropositionCard.showEntityDialog (the same method the
 * mention-badge click listener calls) hands it to EntityPanel.setRelatedRecords -&gt; EntityPanel
 * renders the related-records sections.
 *
 * Every step here runs real production code. The only seam added for testability is that
 * showEntityDialog is package-visible instead of private, so the test can trigger it the way
 * the badge click listener does, without a browser to fire a DOM click event. Nothing here
 * hand-builds an EntityPanel or calls setRelatedRecords itself.
 */
class PropositionCardRecordsTest {

    private static final String ENTITY_ID = "alice-1";
    private static final String ENTITY_NAME = "Alice Chen";

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(entity.getName()).thenReturn(ENTITY_NAME);
        when(entity.getDescription()).thenReturn("Senior Engineer");
        when(entity.labels()).thenReturn(Set.of("Person"));
        return entity;
    }

    private PropositionRepository mockRepo() {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(List.of());
        return repo;
    }

    private Proposition prop(String id) {
        return Proposition.create(
                id, "ctx-1", "Some memory",
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
     * Drives the whole chain: a loader is set only on the panel, never on any card directly.
     * If PropositionsPanel.createCard stopped calling card.setRelatedRecordsLoader(...), or if
     * PropositionCard.showEntityDialog stopped forwarding it to EntityPanel.setRelatedRecords,
     * this test would fail — the sections below simply would not render.
     */
    @Test
    void panelWiresLoaderThroughCardIntoEntityDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var records = new EntityPanel.RelatedRecords(
                    List.of("alice.chen@acme.example"),
                    List.of(),
                    List.of(new EntityPanel.RelatedItem("Acme Corp", "Employer")),
                    List.of(),
                    List.of(),
                    List.of());

            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;
            var panel = new PropositionsPanel(repo, entityResolver);
            panel.setRelatedRecordsLoader(id -> records);
            ui.add(panel);

            var result = mockSimilarityResult(prop("p-1"), 0.9);
            panel.showScoredPropositions(List.of(result));

            var card = allComponents(panel).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a rendered PropositionCard"));

            // This is the exact method the mention-badge click listener invokes; we call it
            // directly only because firing a real DOM click needs a browser. Everything it
            // does from here on (reading the card's stored loader, wiring EntityPanel, opening
            // the dialog) is unmodified production code.
            card.showEntityDialog(entity);
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected showEntityDialog to open a dialog"));
            assertTrue(dialog.isOpened(), "dialog must be open");

            var panelText = allText(dialog);
            assertTrue(panelText.contains("alice.chen@acme.example"),
                    "EntityPanel must render the contact fact supplied by the panel-level loader");
            assertTrue(panelText.contains("Acme Corp"),
                    "EntityPanel must render the organization supplied by the panel-level loader");
            assertTrue(panelText.contains("Organizations"),
                    "EntityPanel must render the Organizations section header");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Same chain, but with no loader set anywhere. Confirms the related-records sections are
     * genuinely conditional on the pass-through, not always rendered.
     */
    @Test
    void noLoaderMeansNoRelatedRecordsSectionsInDialog() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;
            var panel = new PropositionsPanel(repo, entityResolver);
            // No setRelatedRecordsLoader call.
            ui.add(panel);

            var result = mockSimilarityResult(prop("p-2"), 0.9);
            panel.showScoredPropositions(List.of(result));

            var card = allComponents(panel).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a rendered PropositionCard"));

            card.showEntityDialog(entity);
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected showEntityDialog to open a dialog"));

            var text = allText(dialog);
            assertFalse(text.contains("Organizations"), "must not render related records without a loader");
            assertFalse(text.contains("Contact Facts"), "must not render related records without a loader");
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

    private static String allText(Component root) {
        return allComponents(root).stream()
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
