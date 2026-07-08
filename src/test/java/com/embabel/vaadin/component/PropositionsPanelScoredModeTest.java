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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

/**
 * Proves that scored mode (showing search results with similarity scores) survives
 * refresh() by re-running the supplier, and that switcing back to contextId-mode
 * leaves scored mode behind.
 */
class PropositionsPanelScoredModeTest {

    private static final String CTX = "ctx-1";
    private static final String SUBJECT_TEXT = "Jim lives in Brisbane";

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, PropositionStatus status) {
        return Proposition.create(
                id,
                CTX,
                SUBJECT_TEXT,
                List.of(),
                0.9,
                0.0,
                0.5,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                status
        );
    }

    private PropositionRepository mockRepo() {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class)))
                .thenReturn(List.of());
        return repo;
    }

    @Test
    void scoredResultsShownThenRefreshWithSupplierReRendersAndCountUnchanged() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        var p1 = prop("p-1", PropositionStatus.ACTIVE);
        var p2 = prop("p-2", PropositionStatus.ACTIVE);
        var result1 = mockSimilarityResult(p1, 0.95);
        var result2 = mockSimilarityResult(p2, 0.87);
        var initialResults = List.of(result1, result2);

        // Show initial scored results
        panel.showScoredPropositions(initialResults);
        var initialCards = renderedCardIds(panel);
        assertEquals(List.of("p-1", "p-2"), initialCards, "must render the initial scored results");
        assertEquals("(2 relevant)", countBadgeText(panel), "count badge shows count of relevant results");

        // Set up supplier to re-render the same results
        panel.setScoredResultsSupplier(() -> initialResults);

        // Refresh should re-render via the supplier, not wipe the display
        panel.refresh();
        var afterRefreshCards = renderedCardIds(panel);
        assertEquals(List.of("p-1", "p-2"), afterRefreshCards,
                "refresh with supplier must re-render scored results, not wipe them");
        assertEquals("(2 relevant)", countBadgeText(panel),
                "count badge must remain the same after refresh");
    }

    @Test
    void refreshInScoredModeWithoutSupplierDoesNotClearDisplay() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        var p1 = prop("p-1", PropositionStatus.ACTIVE);
        var result1 = mockSimilarityResult(p1, 0.95);
        var initialResults = List.of(result1);

        // Show scored results without setting a supplier
        panel.showScoredPropositions(initialResults);
        var initialCards = renderedCardIds(panel);
        assertEquals(List.of("p-1"), initialCards, "must render the scored result");

        // Refresh without a supplier should not touch the display
        panel.refresh();
        var afterRefreshCards = renderedCardIds(panel);
        assertEquals(List.of("p-1"), afterRefreshCards,
                "refresh in scored mode without supplier must not clear existing children");
    }

    @Test
    void switchFromScoredModeToContextIdModeViaSetContextId() {
        var repo = mock(PropositionRepository.class);
        var p1 = prop("p-1", PropositionStatus.ACTIVE);
        when(repo.query(any(PropositionQuery.class)))
                .thenReturn(List.of(p1));

        var panel = new PropositionsPanel(repo, entityResolver);

        // Start in scored mode
        var result1 = mockSimilarityResult(p1, 0.95);
        var scoredResults = List.of(result1);
        panel.showScoredPropositions(scoredResults);
        assertEquals(List.of("p-1"), renderedCardIds(panel), "must show scored result initially");

        // Switch to context-id mode
        panel.setContextId(CTX);

        // Refresh should now use the contextId path, not scored mode
        panel.refresh();
        var afterContextRefresh = renderedCardIds(panel);
        assertEquals(List.of("p-1"), afterContextRefresh,
                "after setContextId, refresh must use the contextId path");
        // Count badge text differs: scored uses "relevant", context uses "memories"
        assertEquals("(1 memories)", countBadgeText(panel),
                "count badge reflects context-scoped query, not scored count");
    }

    // --- mock helpers ---------------------------------------------------------------------------

    private static SimilarityResult<Proposition> mockSimilarityResult(Proposition proposition, double score) {
        var mock = mock(SimilarityResult.class);
        doReturn(proposition).when(mock).getMatch();
        doReturn(score).when(mock).getScore();
        return mock;
    }

    // --- component-tree helpers (no Karibu needed — plain Vaadin component walk) -----------------

    private static List<String> renderedCardIds(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> ((PropositionCard) c).getProposition().getId())
                .toList();
    }

    private static String countBadgeText(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Span && ((Span) c).getClassNames().contains("panel-count"))
                .map(c -> ((Span) c).getText())
                .findFirst()
                .orElseThrow(() -> new AssertionError("count badge span not found"));
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
