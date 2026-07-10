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
import com.embabel.agent.rag.service.Cluster;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.MentionRole;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyDownEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Conformance test for the redesigned Memories page per
 * {@code .claude/design294/previews/memories-page.html} (the approved mock): the header search
 * seam (L1 instant filter, Enter submit, results bar, operator popover), the clusters toggle
 * contract (light container + header when clusterable data is present), and the always-visible
 * "Undo merge" link on merged cards.
 *
 * <p>Not covered here: the "Sweep" link. It is not owned by this component — grep of
 * {@code src/main} in vaadin-components turns up no "Sweep"/"Preview sweep" button anywhere;
 * the mock's topbar Sweep link belongs to the "me" host's page chrome, outside this packet's
 * files (MemorySection.java, PropositionsPanel.java).
 */
class MemoriesPageConformanceTest {

    private static final String CTX = "ctx-1";

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, String text, Instant created, EntityMention... mentions) {
        return Proposition.create(
                id, CTX, text, List.of(mentions), 0.9, 0.0, 0.5, null, List.of(),
                created, created, PropositionStatus.ACTIVE);
    }

    private EntityMention mention(String span) {
        return new EntityMention(span, "Person", null, MentionRole.SUBJECT, Map.of());
    }

    private PropositionRepository repoWith(List<Proposition> pool) {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(pool);
        when(repo.findClusters(anyDouble(), anyInt(), any(PropositionQuery.class))).thenReturn(List.of());
        return repo;
    }

    // --- header search field: L1 instant filter ---------------------------------------------

    @Test
    void searchFieldPresentAndTypingFiltersCardsClientSideAndUpdatesCount() {
        var ben = prop("ben-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());
        var priya = prop("priya-1", "Priya organizes the guild lunch", Instant.now().minusSeconds(10));
        var panel = new PropositionsPanel(repoWith(List.of(ben, priya)), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var searchField = findSearchField(panel);
        assertNotNull(searchField, "header must carry a search field");
        assertEquals("Search memories... filter, entity:\"...\", or ask a question", searchField.getPlaceholder());

        assertEquals("(2 memories)", countText(panel));

        searchField.setValue("hiked");

        assertEquals("(1 memory)", countText(panel), "instant filter must update the count");
        var visibleCards = allComponents(panel).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .filter(Component::isVisible)
                .map(c -> c.getProposition().getId())
                .toList();
        assertEquals(List.of("ben-1"), visibleCards, "only the matching card stays visible");

        // Esc clears the field and the filter
        ComponentUtil.fireEvent(searchField, new KeyDownEvent(searchField, "Escape"));
        assertEquals("", searchField.getValue());
        assertEquals("(2 memories)", countText(panel), "Esc must restore the full count");
    }

    @Test
    void instantFilterAlsoMatchesEntityNames() {
        var priya = prop("priya-1", "Some unrelated text", Instant.now(), mention("Priya Sharma"));
        var other = prop("other-1", "Different unrelated text", Instant.now().minusSeconds(5));
        var panel = new PropositionsPanel(repoWith(List.of(priya, other)), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var searchField = findSearchField(panel);
        searchField.setValue("priya");

        var visibleIds = allComponents(panel).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .filter(Component::isVisible)
                .map(c -> c.getProposition().getId())
                .toList();
        assertEquals(List.of("priya-1"), visibleIds, "entity mention text must be searchable too");
    }

    @Test
    void instantFilterInNormalModeIsCaseInsensitive() {
        var mentor = prop("mentor-1", "Mountain Hiking Preferences and Mentorship", Instant.now());
        var other = prop("other-1", "Different unrelated text", Instant.now().minusSeconds(5));
        var panel = new PropositionsPanel(repoWith(List.of(mentor, other)), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var searchField = findSearchField(panel);
        searchField.setValue("mentor");

        var visibleIds = allComponents(panel).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .filter(Component::isVisible)
                .map(c -> c.getProposition().getId())
                .toList();
        assertEquals(List.of("mentor-1"), visibleIds,
                "lowercase query must match mixed-case text like 'Mentorship'");

        searchField.setValue("");
        var allVisible = allComponents(panel).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .allMatch(Component::isVisible);
        assertTrue(allVisible, "clearing the filter must show all cards again");
    }

    // --- Enter submits the raw query -------------------------------------------------------

    @Test
    void enterFiresOnSearchSubmitWithRawTextThroughMemorySection() {
        var repo = repoWith(List.of());
        Function<String, NamedEntity> resolver = id -> null;
        var memorySection = new MemorySection(repo, resolver, () -> CTX, null, null, null);

        var captured = new AtomicReference<String>();
        memorySection.setOnSearchSubmit(captured::set);

        var panel = findPanel(memorySection);
        var searchField = findSearchField(panel);
        searchField.setValue("hiking");
        ComponentUtil.fireEvent(searchField, new KeyDownEvent(searchField, "Enter"));

        assertEquals("hiking", captured.get(), "Enter must hand the raw query to the host callback");
    }

    @Test
    void leavingScoredModeRestoresStatusFilterAndClusterToggle() {
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var statusBefore = findByClass(panel, "status-filter");
        var toggleBefore = findByClass(panel, "cluster-toggle");
        assertTrue(statusBefore.isVisible(), "status filter starts visible");
        assertTrue(toggleBefore.isVisible(), "cluster toggle starts visible");

        panel.showScoredPropositions(List.of());
        assertFalse(findByClass(panel, "status-filter").isVisible(), "scored mode hides the status filter");
        assertFalse(findByClass(panel, "cluster-toggle").isVisible(), "scored mode hides the cluster toggle");

        // The Clear path: host resets context then refreshes.
        panel.setContextId(CTX);
        panel.refresh();
        assertTrue(findByClass(panel, "status-filter").isVisible(),
                "leaving scored mode must restore the status filter");
        assertTrue(findByClass(panel, "cluster-toggle").isVisible(),
                "leaving scored mode must restore the cluster toggle");
    }

    private Component findByClass(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof HasStyle hs && hs.getClassNames().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a component with class " + className));
    }

    // --- results bar shows and clears -------------------------------------------------------

    @Test
    void resultsBarShowsWithLabelAndClearsBothVisuallyAndViaCallback() {
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var barBefore = findResultsBar(panel);
        assertFalse(barBefore.isVisible(), "results bar starts hidden");

        var cleared = new AtomicReference<Boolean>(false);
        panel.setSearchResultsBar("hiking", () -> cleared.set(true));

        var bar = findResultsBar(panel);
        assertTrue(bar.isVisible(), "results bar must show once the host sets a label");
        var labelText = allComponents(bar).stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .filter(t -> t != null && t.contains("hiking"))
                .findFirst();
        assertTrue(labelText.isPresent(), "results bar must display the submitted query");

        var clearButton = allComponents(bar).stream()
                .filter(c -> c instanceof Button && "Clear".equals(((Button) c).getText()))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a Clear button on the results bar"));
        clearButton.click();

        assertFalse(findResultsBar(panel).isVisible(), "results bar must hide on Clear");
        assertTrue(cleared.get(), "Clear must invoke the host's onClear callback");
    }

    @Test
    void resultsBarTextVariantShowsLiteralTextWithoutSemanticWrapper() {
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        panel.setSearchResultsBarText("Ben's favorite range is the Rockies.", () -> { });

        var bar = findResultsBar(panel);
        assertTrue(bar.isVisible(), "results bar must show for literal answer text");
        var label = allComponents(bar).stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .filter(t -> t != null && t.contains("Rockies"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected the answer text on the bar"));
        assertFalse(label.contains("Semantic results for"),
                "literal text variant must not wrap the answer in the semantic-results phrasing");

        panel.setSearchResultsBarText(null, null);
        assertFalse(findResultsBar(panel).isVisible(), "null text must hide the bar");
    }

    // --- operator popover --------------------------------------------------------------------

    @Test
    void infoChipPopoverListsAllFourOperators() {
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var popover = allComponents(panel).stream()
                .filter(c -> c instanceof Popover)
                .map(c -> (Popover) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a search-help Popover"));

        var text = allComponents(popover).stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .filter(t -> t != null)
                .reduce("", (a, b) -> a + " | " + b);

        assertTrue(text.contains("entity:<name>"), "popover must document entity:");
        assertTrue(text.contains("status:active|stale|all"), "popover must document status:");
        assertTrue(text.contains("conf:>0.8"), "popover must document conf:");
        assertTrue(text.contains("merged:yes|no"), "popover must document merged:");
    }

    // --- clusters toggle contract --------------------------------------------------------------

    @Test
    void clustersToggleOnRendersContainerWithHeaderForClusterableData() {
        var anchor = prop("anchor-1", "Ben is learning to bake sourdough", Instant.now());
        var similarProp = prop("similar-1", "Ben's sourdough loaves keep collapsing", Instant.now().minusSeconds(30));
        var similarity = mockSimilarityResult(similarProp, 0.8);

        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(List.of(anchor, similarProp));
        when(repo.findClusters(anyDouble(), anyInt(), any(PropositionQuery.class)))
                .thenReturn(List.of(new Cluster<>(anchor, List.of(similarity))));

        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        // Flat (OFF) mode: no cluster containers.
        assertTrue(allComponents(panel).stream().noneMatch(c -> c.hasClassName("cluster-container")),
                "clusters OFF must render the flat list, no cluster containers");

        var toggle = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("cluster-toggle"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        toggle.click();

        var container = allComponents(panel).stream()
                .filter(c -> c.hasClassName("cluster-container"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("clusters ON must render a cluster-container"));

        var header = allComponents(container).stream()
                .filter(c -> c.hasClassName("cluster-head"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("cluster container must carry a cluster-head"));

        var headerText = allComponents(header).stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .filter(t -> t != null && t.startsWith("Cluster:"))
                .findFirst();
        assertTrue(headerText.isPresent(), "cluster header must read 'Cluster: N similar memories'");
        assertEquals("Cluster: 2 similar memories", headerText.get());

        var memberIds = allComponents(container).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> ((PropositionCard) c).getProposition().getId())
                .toList();
        assertEquals(List.of("anchor-1", "similar-1"), memberIds, "container must hold both member cards");
    }

    @SuppressWarnings("unchecked")
    private static SimilarityResult<Proposition> mockSimilarityResult(Proposition proposition, double score) {
        var mock = mock(SimilarityResult.class);
        doReturn(proposition).when(mock).getMatch();
        doReturn(score).when(mock).getScore();
        return mock;
    }

    // --- merged-card visible Undo-merge link ---------------------------------------------------

    @Test
    void mergedCardShowsVisibleUndoMergeLinkAndClickingFiresUndoPath() {
        var ui = withUi();
        try {
            var survivor = prop("survivor-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());

            CollapseExplanationProvider provider = id -> {
                if (!"survivor-1".equals(id)) {
                    return Optional.empty();
                }
                var member = new CollapseExplanation.RetiredMember(
                        "retired-1", "Ben went hiking near Lavaredo", "ACTIVE", List.of(), List.of(), List.of());
                return Optional.of(new CollapseExplanation(
                        "component-1", "survivor-1", survivor.getText(), "MERGE", List.of(member), List.of()));
            };

            var repo = mock(PropositionRepository.class);
            when(repo.query(any(PropositionQuery.class))).thenReturn(List.of(survivor));

            var panel = new PropositionsPanel(repo, entityResolver, provider);
            panel.setContextId(CTX);

            var capturedSurvivorId = new AtomicReference<String>();
            var capturedRetiredId = new AtomicReference<String>();
            panel.setOnUndoMember((survivorId, retiredId) -> {
                capturedSurvivorId.set(survivorId);
                capturedRetiredId.set(retiredId);
            });

            ui.add(panel);
            panel.refresh();

            var card = allComponents(panel).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the merged card to render"));

            var mergedBadge = allComponents(card).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("collapse-explanation-badge"))
                    .findFirst();
            assertTrue(mergedBadge.isPresent(), "card must show the Merged N duplicates badge");

            var undoLink = allComponents(card).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("undo-merge-link"))
                    .map(c -> (Button) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a visible Undo merge link next to the merged badge"));
            assertTrue(undoLink.isVisible(), "Undo merge link must be visible (not hover-only)");
            assertEquals("Undo merge", undoLink.getText());

            undoLink.click();

            assertEquals("survivor-1", capturedSurvivorId.get(), "undo link must fire the host callback with the survivor id");
            assertEquals("retired-1", capturedRetiredId.get(), "undo link must fire the host callback with the retired id");
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void nonMergedCardHasNoUndoMergeLink() {
        var plain = prop("plain-1", "Ben's old desk was in Building 2", Instant.now());
        CollapseExplanationProvider provider = id -> Optional.empty();

        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(List.of(plain));

        var panel = new PropositionsPanel(repo, entityResolver, provider);
        panel.setContextId(CTX);
        panel.refresh();

        var card = allComponents(panel).stream()
                .filter(c -> c instanceof PropositionCard)
                .findFirst()
                .orElseThrow();

        var undoLinks = allComponents(card).stream()
                .filter(c -> c instanceof Button && c.hasClassName("undo-merge-link"))
                .toList();
        assertTrue(undoLinks.isEmpty(), "cards with no merge history must not show the undo-merge link");
    }

    // --- helpers --------------------------------------------------------------------------------

    private static UI withUi() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
    }

    private static PropositionsPanel findPanel(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof PropositionsPanel)
                .map(c -> (PropositionsPanel) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a PropositionsPanel"));
    }

    private static TextField findSearchField(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof TextField && c.hasClassName("memory-search-field"))
                .map(c -> (TextField) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected the header search field"));
    }

    private static HorizontalLayout findResultsBar(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof HorizontalLayout && c.hasClassName("search-results-bar"))
                .map(c -> (HorizontalLayout) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected the search results bar"));
    }

    private static String countText(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Span && c.hasClassName("panel-count"))
                .map(c -> ((Span) c).getText())
                .findFirst()
                .orElseThrow(() -> new AssertionError("count badge span not found"));
    }

    private static List<Component> allComponents(Component root) {
        var out = new java.util.ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
