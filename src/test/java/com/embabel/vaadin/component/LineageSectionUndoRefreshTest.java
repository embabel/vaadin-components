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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the post-undo re-render always goes back to {@link LineageProvider} for fresh data
 * (never reuses whatever was on screen), that the collapse card disappears cleanly once the
 * last retired member is restored, and that {@link LineageSection#setOnAfterUndo} fires only
 * after that fresh render is visible — and only when the undo itself succeeded.
 */
class LineageSectionUndoRefreshTest {

    @Test
    void reRendersFromFreshProviderCallAfterUndo() {
        var member1 = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var member2 = new CollapseExplanation.RetiredMember(
                "retired-2", "James is in Brisbane", "STALE", List.of(), List.of(), List.of());
        var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member1, member2));
        var invocationCount = new AtomicInteger(0);

        LineageProvider provider = id -> {
            invocationCount.incrementAndGet();
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
            return Optional.of(new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation)));
        };

        var section = new LineageSection(provider);
        section.setOnUndoMember((survivorId, retiredId) -> retired.removeIf(m -> m.propositionId().equals(retiredId)));
        section.show("survivor-1");

        assertEquals(1, invocationCount.get(), "initial show() must query the provider once");
        assertEquals(2, findButtonsByClass(section, "lineage-undo-member").size());

        findButtonsByClass(section, "lineage-undo-member").get(0).click();

        assertEquals(2, invocationCount.get(), "undo must trigger a fresh provider call, not reuse the last result");
        assertEquals(1, findButtonsByClass(section, "lineage-undo-member").size(),
                "rendered collapse history must reflect the fresh (post-restore) data");
    }

    @Test
    void restoringLastMemberDropsCollapseCardButKeepsRestOfLineage() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member));

        LineageProvider provider = id -> {
            if (retired.isEmpty()) {
                // All members restored: no collapse explanation left at all.
                return Optional.of(new LineageProvider.Lineage(
                        List.of("grounding-ref"), List.of(), Optional.empty()));
            }
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
            return Optional.of(new LineageProvider.Lineage(List.of("grounding-ref"), List.of(), Optional.of(explanation)));
        };

        var section = new LineageSection(provider);
        section.setOnUndoMember((survivorId, retiredId) -> retired.removeIf(m -> m.propositionId().equals(retiredId)));
        section.show("survivor-1");

        assertEquals(1, findButtonsByClass(section, "lineage-undo-member").size());

        // Restoring the only remaining member must not throw, must drop the collapse card
        // entirely, and must leave grounding/provenance rendering.
        findButtonsByClass(section, "lineage-undo-member").get(0).click();

        assertTrue(findButtonsByClass(section, "lineage-undo-member").isEmpty(),
                "collapse card must disappear once no retired members remain");
        assertFalse(findSpansContaining(section, "No lineage available").size() > 0,
                "lineage must still render (grounding/provenance), not the 'no lineage' fallback");
    }

    @Test
    void onAfterUndoFiresAfterFreshRenderIsVisible() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member));

        LineageProvider provider = id -> {
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
            return Optional.of(new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation)));
        };

        var section = new LineageSection(provider);
        var capturedSurvivorId = new AtomicReference<String>();
        var capturedRetiredId = new AtomicReference<String>();
        // Captured at the moment onAfterUndo fires: proves the fresh state is already rendered.
        var undoButtonsVisibleAtSignalTime = new AtomicInteger(-1);

        section.setOnUndoMember((survivorId, retiredId) -> retired.removeIf(m -> m.propositionId().equals(retiredId)));
        section.setOnAfterUndo((survivorId, retiredId) -> {
            capturedSurvivorId.set(survivorId);
            capturedRetiredId.set(retiredId);
            undoButtonsVisibleAtSignalTime.set(findButtonsByClass(section, "lineage-undo-member").size());
        });
        section.show("survivor-1");

        findButtonsByClass(section, "lineage-undo-member").get(0).click();

        assertEquals("survivor-1", capturedSurvivorId.get());
        assertEquals("retired-1", capturedRetiredId.get());
        assertEquals(0, undoButtonsVisibleAtSignalTime.get(),
                "by the time onAfterUndo fires, the re-render must already show the restored member gone");
    }

    @Test
    void onUndoMemberThrowingSkipsRerenderAndOnAfterUndo() {
        var member1 = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var member2 = new CollapseExplanation.RetiredMember(
                "retired-2", "James is in Brisbane", "STALE", List.of(), List.of(), List.of());
        var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member1, member2));

        LineageProvider provider = id -> {
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
            return Optional.of(new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation)));
        };

        var section = new LineageSection(provider);
        var afterUndoFired = new AtomicInteger(0);
        section.setOnUndoMember((survivorId, retiredId) -> {
            throw new IllegalStateException("backend restore failed");
        });
        section.setOnAfterUndo((survivorId, retiredId) -> afterUndoFired.incrementAndGet());
        section.show("survivor-1");

        assertEquals(2, findButtonsByClass(section, "lineage-undo-member").size());

        var button = findButtonsByClass(section, "lineage-undo-member").get(0);
        assertThrows(IllegalStateException.class, button::click);

        assertEquals(0, afterUndoFired.get(), "onAfterUndo must not fire when the restore fails");
        assertEquals(2, findButtonsByClass(section, "lineage-undo-member").size(),
                "no re-render must happen when the restore fails, so state stays unchanged");
    }

    private static List<Button> findButtonsByClass(Component root, String className) {
        var buttons = new ArrayList<Button>();
        walkTree(root, c -> {
            if (c instanceof Button && c.hasClassName(className)) {
                buttons.add((Button) c);
            }
        });
        return buttons;
    }

    private static List<Component> findSpansContaining(Component root, String text) {
        var matches = new ArrayList<Component>();
        walkTree(root, c -> {
            if (c instanceof com.vaadin.flow.component.html.Span span
                    && span.getText() != null && span.getText().contains(text)) {
                matches.add(c);
            }
        });
        return matches;
    }

    private static void walkTree(Component c, java.util.function.Consumer<Component> visit) {
        visit.accept(c);
        c.getChildren().forEach(child -> walkTree(child, visit));
    }
}
