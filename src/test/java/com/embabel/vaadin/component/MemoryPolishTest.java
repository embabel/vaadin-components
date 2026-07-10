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
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.MentionRole;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers three user-review fixes on top of the redesigned Memories page:
 * <ol>
 *   <li>No bare percentage text anywhere in scored-results mode — only the labeled
 *       "NN% confidence" badge is allowed.</li>
 *   <li>A "Sweep" button in the actions row (Learn / Analyze / Sweep / Clear All).</li>
 *   <li>{@code MemorySection#setSearchQuery} and entity-pill click forwarding via
 *       {@code setOnEntityPillClick}.</li>
 * </ol>
 */
class MemoryPolishTest {

    private static final String CTX = "ctx-1";
    // Matches a bare percentage text node like "82%" or a bare decimal like "0.82" — but not
    // a labeled string such as "82% confidence".
    private static final Pattern BARE_PERCENT = Pattern.compile("^\\d{1,3}%$");
    private static final Pattern BARE_DECIMAL = Pattern.compile("^0\\.\\d+$");

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, String text, Instant created, EntityMention... mentions) {
        return Proposition.create(
                id, CTX, text, List.of(mentions), 0.9, 0.0, 0.5, null, List.of(),
                created, created, PropositionStatus.ACTIVE);
    }

    private PropositionRepository repoWith(List<Proposition> pool) {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class))).thenReturn(pool);
        return repo;
    }

    private SimilarityResult<Proposition> mockSimilarityResult(Proposition proposition, double score) {
        var mock = mock(SimilarityResult.class);
        doReturn(proposition).when(mock).getMatch();
        doReturn(score).when(mock).getScore();
        return mock;
    }

    // --- 1. no stray percentage in scored mode ------------------------------------------------

    /**
     * Sweep every rendered text node in scored mode and assert none of them is a bare
     * percentage or bare decimal. This is deliberately a full sweep, not a check for one named
     * element, because two prior passes each killed a single offending element and missed
     * another leftover — a regex sweep can't have the same blind spot.
     */
    @Test
    void scoredModeHasNoBarePercentageAnywhere() {
        var ben = prop("ben-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());
        var priya = prop("priya-1", "Priya organizes the guild lunch", Instant.now().minusSeconds(10));

        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);

        panel.showScoredPropositions(List.of(
                mockSimilarityResult(ben, 0.82),
                mockSimilarityResult(priya, 0.47)));

        var texts = allTextNodes(panel);

        var offenders = texts.stream()
                .filter(t -> BARE_PERCENT.matcher(t.trim()).matches() || BARE_DECIMAL.matcher(t.trim()).matches())
                .toList();
        assertTrue(offenders.isEmpty(), "no bare percentage/decimal text nodes allowed in scored mode, found: " + offenders);

        var hasLabeledConfidence = texts.stream().anyMatch(t -> t.trim().matches("^\\d{1,3}% confidence$"));
        assertTrue(hasLabeledConfidence, "the labeled 'NN% confidence' badge must still be present");
    }

    /**
     * Self-falsification: re-introduce a bare percentage element and confirm the sweep goes red.
     * This proves the sweep test actually detects the class of bug it's meant to catch.
     */
    @Test
    void selfFalsify_sweepCatchesAReintroducedBarePercentage() {
        var ben = prop("ben-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.showScoredPropositions(List.of(mockSimilarityResult(ben, 0.82)));

        // Simulate the leftover bare-percentage span the fix removed.
        var rogueBadge = new Span("82%");
        rogueBadge.addClassName("similarity-badge");
        panel.getElement().appendChild(rogueBadge.getElement());

        var texts = allTextNodes(panel);
        var offenders = texts.stream()
                .filter(t -> BARE_PERCENT.matcher(t.trim()).matches() || BARE_DECIMAL.matcher(t.trim()).matches())
                .toList();
        assertFalse(offenders.isEmpty(), "sweep must catch a reintroduced bare percentage");
    }

    // --- 2. Sweep button lives in the actions row, next to Analyze ----------------------------

    @Test
    void sweepButtonExistsOnceInActionsRowAdjacentToAnalyzeAndOldPlacementIsGone() {
        var ui = withUi();
        try {
            var repo = repoWith(List.of());
            var memorySection = new MemorySection(
                    repo, entityResolver, () -> CTX,
                    () -> {},  // onAnalyze, so Analyze button exists
                    null,      // onRemember
                    null);     // onClearContext
            memorySection.setOnSweep(() -> {});
            ui.add(memorySection);

            var sweepButtons = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && "Sweep".equals(((Button) c).getText()))
                    .toList();
            assertEquals(1, sweepButtons.size(), "Sweep must exist exactly once");
            var sweepButton = sweepButtons.get(0);
            assertTrue(sweepButton.isVisible(), "Sweep must be visible once a handler is set");

            var analyzeButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && "Analyze".equals(((Button) c).getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected an Analyze button"));

            assertTrue(sweepButton.getParent().isPresent() && analyzeButton.getParent().isPresent(),
                    "both buttons must be attached");
            assertEquals(analyzeButton.getParent().get(), sweepButton.getParent().get(),
                    "Sweep must share the same parent (actions row) as Analyze");
            assertTrue(sweepButton.hasClassName("memory-sweep"), "Sweep button carries its own class name");

            // Old top-left placement is gone: no standalone Sweep link/button outside the actions row.
            assertTrue(allComponents(memorySection).stream()
                            .noneMatch(c -> c.hasClassName("panel-top-sweep") || c.hasClassName("sweep-link")),
                    "no leftover top-left Sweep placement");
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void sweepButtonHiddenUntilHandlerIsSetAndClickInvokesCallback() {
        var ui = withUi();
        try {
            var repo = repoWith(List.of());
            var memorySection = new MemorySection(repo, entityResolver, () -> CTX, null, null, null);
            ui.add(memorySection);

            var sweepButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && "Sweep".equals(((Button) c).getText()))
                    .map(c -> (Button) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a Sweep button in the tree"));
            assertFalse(sweepButton.isVisible(), "Sweep stays hidden until a handler is wired");

            var invoked = new AtomicBoolean(false);
            memorySection.setOnSweep(() -> invoked.set(true));
            assertTrue(sweepButton.isVisible());
            sweepButton.click();
            assertTrue(invoked.get(), "Sweep click must invoke the host callback");
        } finally {
            UI.setCurrent(null);
        }
    }

    // --- 3. setSearchQuery + entity pill click hook --------------------------------------------

    @Test
    void setSearchQueryFiltersLikeTypingDoes() {
        var ui = withUi();
        try {
            var ben = prop("ben-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());
            var priya = prop("priya-1", "Priya organizes the guild lunch", Instant.now().minusSeconds(10));
            var repo = repoWith(List.of(ben, priya));
            var memorySection = new MemorySection(repo, entityResolver, () -> CTX, null, null, null);
            ui.add(memorySection);
            memorySection.refresh();

            assertEquals("(2 memories)", countText(memorySection));

            memorySection.setSearchQuery("hiked");

            assertEquals("(1 memory)", countText(memorySection), "setSearchQuery must filter like typing does");
            var searchField = allComponents(memorySection).stream()
                    .filter(c -> c instanceof TextField && c.hasClassName("memory-search-field"))
                    .map(c -> (TextField) c)
                    .findFirst()
                    .orElseThrow();
            assertEquals("hiked", searchField.getValue(), "setSearchQuery must also set the field's value");
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void entityPillClickFiresCallbackWithEntityName() {
        var ui = withUi();
        try {
            var entity = mock(NamedEntity.class);
            when(entity.getId()).thenReturn("ent-1");
            when(entity.getName()).thenReturn("Priya Sharma");
            when(entity.getDescription()).thenReturn("desc");
            when(entity.labels()).thenReturn(Set.of("Person"));
            Function<String, NamedEntity> resolver = id -> entity;

            var mention = new EntityMention("Priya", "Person", "ent-1", MentionRole.SUBJECT, Map.of());
            var priya = prop("priya-1", "Priya organizes the guild lunch", Instant.now(), mention);
            var repo = repoWith(List.of(priya));

            var memorySection = new MemorySection(repo, resolver, () -> CTX, null, null, null);
            var captured = new AtomicReference<String>();
            memorySection.setOnEntityPillClick(captured::set);
            ui.add(memorySection);
            memorySection.refresh();

            var card = allComponents(memorySection).stream()
                    .filter(c -> c instanceof PropositionCard)
                    .map(c -> (PropositionCard) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a rendered PropositionCard"));
            var pill = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Span && c.hasClassName("mention-badge"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected an entity pill on the card"));
            assertTrue(pill.hasClassName("clickable"), "pill must be clickable once a handler is set");

            // Same seam PropositionCardRecordsTest uses for showEntityDialog: call the exact
            // method the click listener invokes, since there's no browser here to fire a real
            // DOM click event.
            card.handlePillClick("Priya Sharma");

            assertEquals("Priya Sharma", captured.get(), "pill click must fire the callback with the entity's display name");
        } finally {
            UI.setCurrent(null);
        }
    }

    @Test
    void entityPillsStayNonClickableWithNoHandler() {
        var ui = withUi();
        try {
            var mention = new EntityMention("Unresolved Co", "Org", null, MentionRole.SUBJECT, Map.of());
            var prop = prop("prop-1", "Some company did a thing", Instant.now(), mention);
            var repo = repoWith(List.of(prop));

            var memorySection = new MemorySection(repo, entityResolver, () -> CTX, null, null, null);
            // No setOnEntityPillClick call.
            ui.add(memorySection);
            memorySection.refresh();

            var pill = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Span && c.hasClassName("mention-badge"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected an entity pill on the card"));

            assertFalse(pill.hasClassName("clickable"), "unresolved pill with no handler must not gain the clickable cursor class");
        } finally {
            UI.setCurrent(null);
        }
    }

    // --- A. search field is the dominant control of the header row -----------------------------

    /**
     * Mechanism test for "make the search field dominant": it must carry a flex-grow-friendly
     * min/max width band (320px-480px) so it visually leads the header row instead of shrinking
     * to content, while still leaving room for status/clusters/refresh.
     */
    @Test
    void searchFieldHasDominantWidthBand() {
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var searchField = allComponents(panel).stream()
                .filter(c -> c instanceof TextField && c.hasClassName("memory-search-field"))
                .map(c -> (TextField) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected the header search field"));

        assertEquals("320px", searchField.getMinWidth(), "search field must not shrink below 320px");
        assertEquals("480px", searchField.getMaxWidth(), "search field must not grow past 480px");
    }

    // --- B. icon-only controls carry title/aria-label -------------------------------------------

    @Test
    void searchHelpChipRefreshAndSweepCarryTooltipsAndAriaLabels() {
        var ui = withUi();
        try {
            var repo = repoWith(List.of());
            var memorySection = new MemorySection(repo, entityResolver, () -> CTX, null, null, null);
            memorySection.setOnSweep(() -> {});
            ui.add(memorySection);
            memorySection.refresh();

            var infoChip = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("search-info-chip"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the '?' search-help chip"));
            assertEquals("Search help: operators and tips", infoChip.getElement().getAttribute("title"));
            assertEquals("Search help: operators and tips", infoChip.getElement().getAttribute("aria-label"));

            var refreshButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && "Refresh memories".equals(c.getElement().getAttribute("title")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the refresh button"));
            assertEquals("Refresh memories", refreshButton.getElement().getAttribute("aria-label"));

            var sweepButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button && "Sweep".equals(((Button) c).getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the Sweep button"));
            assertEquals("Run a dedup sweep", sweepButton.getElement().getAttribute("title"));
            assertEquals("Run a dedup sweep", sweepButton.getElement().getAttribute("aria-label"));
        } finally {
            UI.setCurrent(null);
        }
    }

    // --- C. scored-card-wrapper full width, via the theme stylesheet --------------------------

    /**
     * Proves the width rules live in the theme stylesheet (not component-injected virtual-child
     * CSS, which never renders on the page) and target the wrapper and the card inside it.
     */
    @Test
    void themeStylesheetSetsScoredWrapperAndCardToFullWidth() throws java.io.IOException {
        var cssPath = java.nio.file.Path.of(
                "src/main/resources/META-INF/resources/themes/embabel-base/styles.css");
        var css = java.nio.file.Files.readString(cssPath);

        var wrapperRule = Pattern.compile(
                "\\.scored-card-wrapper\\s*\\{[^}]*width:\\s*100%[^}]*\\}", Pattern.DOTALL);
        assertTrue(wrapperRule.matcher(css).find(),
                "theme stylesheet must set .scored-card-wrapper to width: 100%");

        var cardRule = Pattern.compile(
                "\\.scored-card-wrapper\\s+\\.proposition-card\\s*\\{[^}]*width:\\s*100%[^}]*\\}", Pattern.DOTALL);
        assertTrue(cardRule.matcher(css).find(),
                "theme stylesheet must set .scored-card-wrapper .proposition-card to width: 100%");
    }

    // --- wrapper-level bare-percentage coverage (explicit) --------------------------------------

    /**
     * The stray percentage the live app showed sat directly in .scored-card-wrapper, outside the
     * card itself. This proves the sweep test's text-node walk covers that wrapper level too,
     * not just inside the card — same mechanism as the self-falsification test above, but with
     * the rogue badge placed exactly where it was live: as a direct child of the wrapper.
     */
    @Test
    void sweepCatchesABarePercentageAtTheWrapperLevelOutsideTheCard() {
        var ben = prop("ben-1", "Ben hiked the Tre Cime di Lavaredo loop", Instant.now());
        var panel = new PropositionsPanel(repoWith(List.of()), entityResolver);
        panel.setContextId(CTX);
        panel.showScoredPropositions(List.of(mockSimilarityResult(ben, 0.82)));

        var wrapper = allComponents(panel).stream()
                .filter(c -> c.hasClassName("scored-card-wrapper"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a .scored-card-wrapper"));

        var rogueBadge = new Span("82%");
        rogueBadge.addClassName("similarity-badge");
        wrapper.getElement().appendChild(rogueBadge.getElement());

        var texts = allTextNodes(panel);
        var offenders = texts.stream()
                .filter(t -> BARE_PERCENT.matcher(t.trim()).matches() || BARE_DECIMAL.matcher(t.trim()).matches())
                .toList();
        assertFalse(offenders.isEmpty(), "sweep must catch a bare percentage sitting directly in the wrapper");
    }

    // --- helpers ---------------------------------------------------------------------------

    private static UI withUi() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
    }

    private static String countText(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Span && c.hasClassName("panel-count"))
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

    /** Walk the whole element/text-node tree under a component and collect every text node's content. */
    private static List<String> allTextNodes(Component root) {
        var out = new ArrayList<String>();
        collectElementText(root.getElement(), out);
        return out;
    }

    private static void collectElementText(com.vaadin.flow.dom.Element element, List<String> out) {
        if (element.isTextNode()) {
            out.add(element.getText());
            return;
        }
        element.getChildren().forEach(child -> collectElementText(child, out));
    }
}
