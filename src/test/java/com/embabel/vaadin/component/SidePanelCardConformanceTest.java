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
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test that side-panel memory cards conform to the design spec and user amendments:
 * - no numeric score text in scored mode
 * - timestamp with relative time at bottom-right
 * - correct card anatomy order
 * - full-width tight cards with text clamping
 * - confidence badge unchanged
 */
class SidePanelCardConformanceTest {

    private static final String CTX = "ctx-1";
    private final Function<String, NamedEntity> entityResolver = id -> null;

    private static EntityMention mention(String span) {
        return new EntityMention(span, "Person", null, com.embabel.dice.proposition.MentionRole.OTHER, java.util.Map.of());
    }

    private static List<EntityMention> mentions(int count) {
        var out = new ArrayList<EntityMention>();
        for (int i = 0; i < count; i++) {
            out.add(mention("Entity" + i));
        }
        return out;
    }

    private Proposition propWithMentions(String id, List<EntityMention> mentions) {
        return Proposition.create(
                id,
                CTX,
                "Memory text " + id,
                mentions,
                0.9,
                0.0,
                0.5,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                PropositionStatus.ACTIVE
        );
    }

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

    @Test
    void testBasicCardStructure() {
        var prop = prop("p1", "Memory about Ben's hobbies", PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);

        // Card should have proposition-text, proposition-meta, and proposition-relative-time
        var textElements = findByClassName(card, "proposition-text");
        assertFalse(textElements.isEmpty(), "Card should have proposition-text element");

        var metaElements = findByClassName(card, "proposition-meta");
        assertFalse(metaElements.isEmpty(), "Card should have proposition-meta row");

        var timeElements = findByClassName(card, "proposition-relative-time");
        assertFalse(timeElements.isEmpty(), "Card should have relative-time element");
    }

    @Test
    void testCardHasFullWidthStyling() {
        var prop = prop("p1", "Test", PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);

        // Card should have full-width class
        assertTrue(card.getElement().getClassList().contains("proposition-card-full-width"),
                   "Card should have proposition-card-full-width class");
    }

    @Test
    void testCardTextIsClampedViaClass() {
        // Create a proposition with very long text
        var longText = "This is a very long memory text that should be clamped to two lines " +
                      "and any additional content beyond that should be hidden with an ellipsis " +
                      "to maintain uniform card heights across the panel";
        var prop = Proposition.create(
                "long1", CTX, longText, List.of(), 0.8, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);

        // Card should have full-width styling which includes text clamping
        assertTrue(card.getElement().getClassList().contains("proposition-card-full-width"),
                   "Card with long text should use clamping via proposition-card-full-width class");
    }

    @Test
    void testConfidenceBadgePresent() {
        var prop = prop("p1", "Test", PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);

        var confidenceElements = findByClassName(card, "proposition-confidence");
        assertFalse(confidenceElements.isEmpty(), "Confidence badge should be present");

        var confidenceElement = (Span) confidenceElements.get(0);
        var text = confidenceElement.getText();
        assertTrue(text.contains("90%"), "Confidence badge should show percentage");
        assertTrue(text.contains("confidence"), "Confidence badge should say 'confidence'");
    }

    @Test
    void testRelativeTimeFormatting() {
        // Test "just now" case
        var nowProp = Proposition.create(
                "r1", CTX, "Just created memory", List.of(), 0.8, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
        var card = new PropositionCard(nowProp, entityResolver);
        var timeElements = findByClassName(card, "proposition-relative-time");
        assertFalse(timeElements.isEmpty());
        var timeSpan = (Span) timeElements.get(0);
        var timeText = timeSpan.getText();
        assertTrue(timeText.contains("ago") || timeText.equals("just now"),
                   "Time should be relative format, got: " + timeText);

        // Test "2h ago" case
        var twoHoursAgo = Instant.now().minusSeconds(7200);
        var twohoursProp = Proposition.create(
                "r2", CTX, "Memory from 2h ago", List.of(), 0.85, 0.0, 0.5, null, List.of(),
                twoHoursAgo, twoHoursAgo, PropositionStatus.ACTIVE);
        var card2 = new PropositionCard(twohoursProp, entityResolver);
        var timeElements2 = findByClassName(card2, "proposition-relative-time");
        var timeSpan2 = (Span) timeElements2.get(0);
        var timeText2 = timeSpan2.getText();
        assertTrue(timeText2.contains("h ago") || timeText2.equals("just now"),
                   "Should show hours ago for 2-hour-old memory, got: " + timeText2);
    }

    @Test
    void testRelativeTimeTooltipHasAbsoluteTime() {
        var prop = prop("p1", "Test", PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);
        var timeElements = findByClassName(card, "proposition-relative-time");
        assertFalse(timeElements.isEmpty());

        var titleAttr = timeElements.get(0).getElement().getAttribute("title");
        assertNotNull(titleAttr, "Relative time span should have title attribute with absolute time");
        assertTrue(titleAttr.contains(":"), "Absolute time in tooltip should contain time format");
    }

    @Test
    void testCardAnatomyOrder() {
        var prop = prop("p1", "Test", PropositionStatus.ACTIVE);
        var card = new PropositionCard(prop, entityResolver);
        var children = allChildren(card);

        // Expected order: header (text), meta, relative-time
        assertTrue(children.size() >= 3, "Card should have at least 3 child elements");

        // Check that text appears before meta
        var textIndex = -1;
        var metaIndex = -1;
        var timeIndex = -1;

        for (int i = 0; i < children.size(); i++) {
            var c = children.get(i);
            if (hasClassName(c, "proposition-header")) {
                textIndex = i;
            }
            if (hasClassName(c, "proposition-meta")) {
                metaIndex = i;
            }
            if (hasClassName(c, "proposition-relative-time")) {
                timeIndex = i;
            }
        }

        assertTrue(textIndex >= 0, "Should have header element");
        assertTrue(metaIndex >= 0, "Should have meta element");
        assertTrue(textIndex < metaIndex, "Text should appear before meta");
        assertTrue(timeIndex >= 0, "Should have relative-time element");
        assertTrue(timeIndex == children.size() - 1, "Relative time should be last element");
    }

    @Test
    void testScoredModeHasNoNumericScoreText() {
        var prop = prop("p1", "Test memory", PropositionStatus.ACTIVE);
        var result = mockSimilarityResult(prop, 0.91);
        var results = List.of(result);

        var panel = new PropositionsPanel(null, entityResolver);
        panel.showScoredPropositions(results);

        // Check that there are no .relevance-score elements (we removed them)
        var scoredCardElements = findByClassName(panel, "scored-card-wrapper");
        assertFalse(scoredCardElements.isEmpty(), "Should have scored-card-wrapper elements");

        // Check that no score text appears in scored-meta-row
        for (var scoreCard : scoredCardElements) {
            var scoreRows = findByClassNameInComponent(scoreCard, "scored-meta-row");
            if (!scoreRows.isEmpty()) {
                for (var scoreRow : scoreRows) {
                    var scoreRowText = getTextContent(scoreRow).trim();
                    // Should not contain numeric patterns like ".91" or "0.91"
                    assertFalse(scoreRowText.matches(".*0\\.\\d{2}.*"),
                                "Score row should not contain numeric score display like 0.XX, got: " + scoreRowText);
                }
            }
        }
    }

    @Test
    void testScoredModeHasNoRelevanceBar() {
        // Final user decision: no relevance bar / progress bar on scored cards at all — the
        // labeled "NN% confidence" badge is the only permitted quantitative element.
        var prop = prop("p1", "Test memory", PropositionStatus.ACTIVE);
        var result = mockSimilarityResult(prop, 0.78);
        var results = List.of(result);

        var panel = new PropositionsPanel(null, entityResolver);
        panel.showScoredPropositions(results);

        var barElements = findByClassName(panel, "relevance-bar");
        assertTrue(barElements.isEmpty(), "Scored cards must not display a relevance bar");
    }

    @Test
    void testScoredModeDedupBadgePresent() {
        // Create multiple similar results that will be deduped
        var text = "Ben's hobby";
        var prop1 = Proposition.create("id1", CTX, text, List.of(), 0.91, 0.0, 0.5, null,
                List.of(), Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
        var prop2 = Proposition.create("id2", CTX, text + ".", List.of(), 0.85, 0.0, 0.5, null,
                List.of(), Instant.now(), Instant.now(), PropositionStatus.ACTIVE);

        var result1 = mockSimilarityResult(prop1, 0.91);
        var result2 = mockSimilarityResult(prop2, 0.85);
        var results = List.of(result1, result2);

        var panel = new PropositionsPanel(null, entityResolver);
        panel.showScoredPropositions(results);

        // Should have dedup badge for the collapsed result
        var dedupBadges = findByClassName(panel, "dedup-collapsed-badge");
        assertFalse(dedupBadges.isEmpty(), "Should show dedup badge when results are collapsed");
    }

    @Test
    void testRelativeTimeFormattingBoundaries() {
        // Test various time boundaries
        var now = Instant.now();

        // 30 seconds ago -> "just now"
        assertEquals("just now", PropositionCard.formatRelativeTime(now.minusSeconds(30)));

        // 5 minutes ago -> "5m ago"
        var fiveMinStr = PropositionCard.formatRelativeTime(now.minusSeconds(300));
        assertTrue(fiveMinStr.matches("\\d+m ago"), "Should format as 'Nm ago', got: " + fiveMinStr);

        // 2 hours ago -> "2h ago"
        var twoHoursStr = PropositionCard.formatRelativeTime(now.minusSeconds(7200));
        assertTrue(twoHoursStr.matches("\\d+h ago"), "Should format as 'Nh ago', got: " + twoHoursStr);

        // 3 days ago -> "3d ago"
        var threeDaysStr = PropositionCard.formatRelativeTime(now.minusSeconds(259200));
        assertTrue(threeDaysStr.matches("\\d+d ago"), "Should format as 'Nd ago', got: " + threeDaysStr);

        // 2 weeks ago -> "2w ago"
        var twoWeeksStr = PropositionCard.formatRelativeTime(now.minusSeconds(1209600));
        assertTrue(twoWeeksStr.matches("\\d+w ago"), "Should format as 'Nw ago', got: " + twoWeeksStr);
    }

    @Test
    void testEachCardCarriesItsOwnStyleElementEvenAfterAnEarlierCardIsRemoved() {
        // Regression for a bug where a single shared <style> element, attached to whichever
        // card happened to be created first, went with that card when it was removed (refresh,
        // undo, filter) — silently unstyling every card created after it. Each card must inject
        // its own style element, independent of any other card's lifecycle.
        var container = new com.vaadin.flow.component.html.Div();

        var firstCard = new PropositionCard(prop("first", "First memory", PropositionStatus.ACTIVE), entityResolver);
        container.add(firstCard);

        container.remove(firstCard);

        var secondCard = new PropositionCard(prop("second", "Second memory", PropositionStatus.ACTIVE), entityResolver);
        container.add(secondCard);

        // The injected <style> is attached via Element.appendVirtualChild, which does NOT show
        // up in getElement().getChildren() (that only lists the normal child list) — it lives in
        // the element's VirtualChildrenList node feature instead. That's the same low-level list
        // Vaadin itself walks to serialize virtual children to the client, so reading it is the
        // real assertion, not a proxy for one.
        var virtualChildren = secondCard.getElement().getNode()
                .getFeatureIfInitialized(com.vaadin.flow.internal.nodefeature.VirtualChildrenList.class);
        assertTrue(virtualChildren.isPresent(), "Second card must have injected a virtual style child");
        assertEquals(1, virtualChildren.get().size(), "Second card should carry exactly one injected style element");

        var styleElement = com.vaadin.flow.dom.Element.get(virtualChildren.get().get(0));
        assertEquals("style", styleElement.getTag());
        assertTrue(styleElement.getText().contains(".proposition-card-full-width"),
                "Injected style must contain the card's own CSS");
    }

    @Test
    void testSixMentionsCapsAtFourPillsPlusOverflowChip() {
        var prop = propWithMentions("m6", mentions(6));
        var card = new PropositionCard(prop, entityResolver);

        var pills = findByClassName(card, "mention-badge");
        assertEquals(4, pills.size(), "Should cap at 4 visible pills for 6 mentions");

        var overflowChips = findByClassName(card, "proposition-pill-overflow");
        assertEquals(1, overflowChips.size(), "Should show exactly one overflow chip");

        var chipText = ((Button) overflowChips.get(0)).getText();
        assertTrue(chipText.contains("2"), "Overflow chip should say 2 more, got: " + chipText);
    }

    @Test
    void testExactlyFourMentionsShowsFourPillsNoChip() {
        var prop = propWithMentions("m4", mentions(4));
        var card = new PropositionCard(prop, entityResolver);

        var pills = findByClassName(card, "mention-badge");
        assertEquals(4, pills.size(), "Should show all 4 pills");

        var overflowChips = findByClassName(card, "proposition-pill-overflow");
        assertTrue(overflowChips.isEmpty(), "Should not show overflow chip at exactly the cap");
    }

    @Test
    void testTwoMentionsShowsTwoPillsNoChip() {
        var prop = propWithMentions("m2", mentions(2));
        var card = new PropositionCard(prop, entityResolver);

        var pills = findByClassName(card, "mention-badge");
        assertEquals(2, pills.size(), "Should show both pills");

        var overflowChips = findByClassName(card, "proposition-pill-overflow");
        assertTrue(overflowChips.isEmpty(), "Should not show overflow chip below the cap");
    }

    @Test
    void testOverflowChipOpensDialogListingAllMentions() {
        var ui = withUi();
        try {
            var prop = propWithMentions("m6d", mentions(6));
            var card = new PropositionCard(prop, entityResolver);
            ui.add(card);

            var overflowChips = findByClassName(card, "proposition-pill-overflow");
            assertEquals(1, overflowChips.size());
            var chip = (Button) overflowChips.get(0);

            chip.click();
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            var dialog = allComponents(ui).stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected the all-pills dialog to open"));
            assertTrue(dialog.isOpened(), "dialog must be open after clicking the overflow chip");

            var dialogPills = findByClassNameInComponent(dialog, "mention-badge");
            assertEquals(6, dialogPills.size(), "Dialog should list all 6 mentions, not just the visible 4");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Set up a live UI so components that need one (dialogs) can attach, the same helper
     * pattern PropositionCardLineageTest uses.
     */
    private static UI withUi() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
    }

    // --- mock helpers ---------------------------------------------------------------------------

    private static SimilarityResult<Proposition> mockSimilarityResult(Proposition proposition, double score) {
        var mock = mock(SimilarityResult.class);
        doReturn(proposition).when(mock).getMatch();
        doReturn(score).when(mock).getScore();
        return mock;
    }

    // --- component-tree helpers -----------------------------------------------------------------

    private List<Component> findByClassName(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> hasClassName(c, className))
                .toList();
    }

    private List<Component> findByClassNameInComponent(Component root, String className) {
        var out = new ArrayList<Component>();
        if (hasClassName(root, className)) {
            out.add(root);
        }
        root.getChildren().forEach(child -> {
            var children = findByClassNameInComponent(child, className);
            out.addAll(children);
        });
        return out;
    }

    private boolean hasClassName(Component c, String className) {
        if (className.isEmpty()) return true; // Accept all if empty
        return c.getElement().getClassList().contains(className);
    }

    private String getTextContent(Component c) {
        if (c instanceof Span span) {
            return span.getText();
        }
        var allText = new StringBuilder();
        c.getChildren().forEach(child -> allText.append(getTextContent(child)));
        return allText.toString();
    }

    private List<Component> allChildren(Component c) {
        var out = new ArrayList<Component>();
        c.getChildren().forEach(child -> out.add(child));
        return out;
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
