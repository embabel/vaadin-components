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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves that scored display collapses near-identical texts (differing only by case,
 * whitespace, or trailing period) and renders dedup badges for collapsed results.
 */
class PropositionsPanelDedupTest {

    private static final String CTX = "ctx-1";
    private static final String SUBJECT_TEXT = "subject";

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, String text, PropositionStatus status) {
        return Proposition.create(
                id,
                CTX,
                text,
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
    void dedupCollapsesTwoTextsWithDifferentCaseAndWhitespace() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        // Two texts that normalize to the same form: one with extra spaces and capital letters,
        // the other with consistent lowercase and single spaces.
        var p1 = prop("p-1", "Jim  lives in Brisbane", PropositionStatus.ACTIVE);
        var p2 = prop("p-2", "jim lives in brisbane", PropositionStatus.ACTIVE);

        // p2 has higher score, so it should be the survivor
        var result1 = mockSimilarityResult(p1, 0.85);
        var result2 = mockSimilarityResult(p2, 0.92);
        var results = List.of(result1, result2);

        panel.showScoredPropositions(results);

        var renderedCards = renderedCardIds(panel);
        var dedupBadges = dedupBadgeTexts(panel);

        assertEquals(List.of("p-2"), renderedCards,
                "must render only the survivor (highest score)");
        assertEquals(List.of("+1 similar"), dedupBadges,
                "must show dedup badge with count of collapsed results");
    }

    @Test
    void dedupCollapseTextDifferingOnlyByTrailingPeriod() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        var p1 = prop("p-1", "Jim lives in Brisbane", PropositionStatus.ACTIVE);
        var p2 = prop("p-2", "Jim lives in Brisbane.", PropositionStatus.ACTIVE);

        var result1 = mockSimilarityResult(p1, 0.90);
        var result2 = mockSimilarityResult(p2, 0.88);
        var results = List.of(result1, result2);

        panel.showScoredPropositions(results);

        var renderedCards = renderedCardIds(panel);
        var dedupBadges = dedupBadgeTexts(panel);

        assertEquals(List.of("p-1"), renderedCards,
                "must render only the survivor");
        assertEquals(List.of("+1 similar"), dedupBadges,
                "must show dedup badge for trailing period difference");
    }

    @Test
    void allDistinctTextsRendersAllCardsWithNoBadges() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        var p1 = prop("p-1", "Alice works in Sydney", PropositionStatus.ACTIVE);
        var p2 = prop("p-2", "Bob works in Melbourne", PropositionStatus.ACTIVE);
        var p3 = prop("p-3", "Charlie works in Perth", PropositionStatus.ACTIVE);

        var result1 = mockSimilarityResult(p1, 0.95);
        var result2 = mockSimilarityResult(p2, 0.87);
        var result3 = mockSimilarityResult(p3, 0.82);
        var results = List.of(result1, result2, result3);

        panel.showScoredPropositions(results);

        var renderedCards = renderedCardIds(panel);
        var dedupBadges = dedupBadgeTexts(panel);

        assertEquals(List.of("p-1", "p-2", "p-3"), renderedCards,
                "must render all distinct cards");
        assertEquals(List.of(), dedupBadges,
                "no dedup badges when all texts are distinct");
    }

    @Test
    void multipleDedupsShowsCorrectCounts() {
        var repo = mockRepo();
        var panel = new PropositionsPanel(repo, entityResolver);

        // Group 1: three variants collapse to one
        var p1 = prop("p-1", "The quick brown fox", PropositionStatus.ACTIVE);
        var p2 = prop("p-2", "the  quick  brown  fox", PropositionStatus.ACTIVE);
        var p3 = prop("p-3", "The quick brown fox.", PropositionStatus.ACTIVE);

        // Group 2: one unique
        var p4 = prop("p-4", "jumps over the lazy dog", PropositionStatus.ACTIVE);

        var result1 = mockSimilarityResult(p1, 0.90);
        var result2 = mockSimilarityResult(p2, 0.88);
        var result3 = mockSimilarityResult(p3, 0.85);
        var result4 = mockSimilarityResult(p4, 0.80);
        var results = List.of(result1, result2, result3, result4);

        panel.showScoredPropositions(results);

        var renderedCards = renderedCardIds(panel);
        var dedupBadges = dedupBadgeTexts(panel);

        assertEquals(List.of("p-1", "p-4"), renderedCards,
                "must render survivors from each group");
        assertEquals(List.of("+2 similar"), dedupBadges,
                "must show correct count for group with 3 items (survivor + 2 collapsed)");
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

    private static List<String> dedupBadgeTexts(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Span && ((Span) c).getClassNames().contains("dedup-collapsed-badge"))
                .map(c -> ((Span) c).getText())
                .toList();
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
