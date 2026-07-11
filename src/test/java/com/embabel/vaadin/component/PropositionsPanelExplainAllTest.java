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
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers M7(a): {@code refresh()} must resolve collapse explanations for a rendered batch with a
 * single {@link CollapseExplanationProvider#explainAll(Collection)} call, never one
 * {@link CollapseExplanationProvider#explain(String)} call per card — otherwise a big panel costs
 * one backend read per memory, per refresh.
 */
class PropositionsPanelExplainAllTest {

    private static final String CTX = "ctx-1";

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, String text) {
        var now = Instant.now();
        return Proposition.create(
                id, CTX, text, List.of(), 0.9, 0.0, 0.5, null, List.of(),
                now, now, PropositionStatus.ACTIVE);
    }

    private PropositionRepository repoWith(List<Proposition> pool) {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(pool);
        return repo;
    }

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

    /** Counts calls to both methods and records the id batch handed to explainAll, so tests can assert on shape. */
    private static final class CountingProvider implements CollapseExplanationProvider {
        final AtomicInteger explainCalls = new AtomicInteger();
        final AtomicInteger explainAllCalls = new AtomicInteger();
        List<String> lastExplainAllIds;
        final Map<String, CollapseExplanation> explanationsById;

        CountingProvider(Map<String, CollapseExplanation> explanationsById) {
            this.explanationsById = explanationsById;
        }

        @Override
        public Optional<CollapseExplanation> explain(String propositionId) {
            explainCalls.incrementAndGet();
            return Optional.ofNullable(explanationsById.get(propositionId));
        }

        @Override
        public Map<String, CollapseExplanation> explainAll(Collection<String> propositionIds) {
            explainAllCalls.incrementAndGet();
            lastExplainAllIds = new ArrayList<>(propositionIds);
            var result = new LinkedHashMap<String, CollapseExplanation>();
            propositionIds.forEach(id -> {
                var explanation = explanationsById.get(id);
                if (explanation != null) {
                    result.put(id, explanation);
                }
            });
            return result;
        }
    }

    private CollapseExplanation mergeExplanation(String survivorId, String survivorText, String retiredId) {
        var member = new CollapseExplanation.RetiredMember(
                retiredId, "retired text for " + retiredId, "ACTIVE", List.of(), List.of(), List.of());
        return new CollapseExplanation("component-1", survivorId, survivorText, "MERGE", List.of(member), List.of());
    }

    @Test
    void refreshCallsExplainAllOnceWithAllRenderedIdsAndNeverCallsExplain() {
        var one = prop("p1", "first memory");
        var two = prop("p2", "second memory");
        var three = prop("p3", "third memory");
        var provider = new CountingProvider(Map.of());

        var panel = new PropositionsPanel(repoWith(List.of(one, two, three)), entityResolver, provider);
        panel.setContextId(CTX);
        panel.refresh();

        assertEquals(1, provider.explainAllCalls.get(), "explainAll must be called exactly once per refresh");
        assertEquals(0, provider.explainCalls.get(), "explain must not be called from the render path");
        assertEquals(Set.of("p1", "p2", "p3"), Set.copyOf(provider.lastExplainAllIds),
                "explainAll must receive every id rendered in this batch");
    }

    @Test
    void mergedAndUnmergedCardsRenderSameAsBeforeViaExplainAll() {
        var ui = withUi();
        try {
            var survivor = prop("survivor-1", "Ben hiked the Tre Cime di Lavaredo loop");
            var plain = prop("plain-1", "Ben's old desk was in Building 2");
            var explanations = Map.of("survivor-1",
                    mergeExplanation("survivor-1", survivor.getText(), "retired-1"));
            var provider = new CountingProvider(explanations);

            var panel = new PropositionsPanel(repoWith(List.of(survivor, plain)), entityResolver, provider);
            panel.setContextId(CTX);
            ui.add(panel);
            panel.refresh();

            var cards = allComponents(panel).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .toList();
            assertEquals(2, cards.size());

            var mergedCard = cards.stream()
                    .filter(card -> allComponents(card).stream()
                            .anyMatch(c -> c instanceof Button && c.hasClassName("collapse-explanation-badge")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the survivor card to show the Merged badge"));

            var undoLink = allComponents(mergedCard).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("undo-merge-link"))
                    .findFirst();
            assertTrue(undoLink.isPresent(), "merged card must still show the visible Undo merge link");

            var plainCard = cards.stream()
                    .filter(card -> card != mergedCard)
                    .findFirst()
                    .orElseThrow();
            var plainBadges = allComponents(plainCard).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("collapse-explanation-badge"))
                    .toList();
            assertTrue(plainBadges.isEmpty(), "unmerged card must not show the collapse badge");
            var plainUndoLinks = allComponents(plainCard).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("undo-merge-link"))
                    .toList();
            assertTrue(plainUndoLinks.isEmpty(), "unmerged card must not show the undo-merge link");

            assertEquals(1, provider.explainAllCalls.get());
            assertEquals(0, provider.explainCalls.get());
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void defaultExplainAllFallsBackToLoopingExplainForProvidersThatOnlyImplementExplain() {
        var ui = withUi();
        try {
            var survivor = prop("survivor-1", "Ben hiked the Tre Cime di Lavaredo loop");
            var explanation = mergeExplanation("survivor-1", survivor.getText(), "retired-1");

            // Plain lambda: only implements explain(), relies on the interface's default explainAll().
            CollapseExplanationProvider provider = id ->
                    "survivor-1".equals(id) ? Optional.of(explanation) : Optional.empty();

            var panel = new PropositionsPanel(repoWith(List.of(survivor)), entityResolver, provider);
            panel.setContextId(CTX);
            ui.add(panel);
            panel.refresh();

            var badge = allComponents(panel).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("collapse-explanation-badge"))
                    .findFirst();
            assertTrue(badge.isPresent(), "default explainAll fallback must still surface the badge");

            assertEquals(Map.of("survivor-1", explanation), provider.explainAll(List.of("survivor-1", "other")));
            assertFalse(provider.explainAll(List.of("other")).containsKey("survivor-1"));
        } finally {
            UI.setCurrent(null);
        }
    }
}
