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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link LineageSection} renders per-retired-member "Undo this merge" buttons and invokes
 * the callback with (survivorId, retiredId) when clicked.
 */
class LineageSectionUndoTest {

    @Test
    void rendersUndoButtonForEachRetiredMember() {
        var member1 = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var member2 = new CollapseExplanation.RetiredMember(
                "retired-2", "James is in Brisbane", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.of(member1, member2), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var section = new LineageSection(id -> Optional.of(lineage));
        section.setOnUndoMember((survivorId, retiredId) -> {
            // Just set up the handler; the test checks rendering, not invocation
        });
        section.show("survivor-1");

        var undoButtons = findButtonsByClass(section, "lineage-undo-member");
        assertEquals(2, undoButtons.size(), "must render one undo button per retired member");
    }

    @Test
    void undoButtonClickInvokesCallbackWithCorrectIds() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "survivor-prop-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var capturedSurvivorId = new AtomicReference<String>();
        var capturedRetiredId = new AtomicReference<String>();

        var section = new LineageSection(id -> Optional.of(lineage));
        section.setOnUndoMember((survivorId, retiredId) -> {
            capturedSurvivorId.set(survivorId);
            capturedRetiredId.set(retiredId);
        });
        section.show("survivor-prop-1");

        var undoButtons = findButtonsByClass(section, "lineage-undo-member");
        assertEquals(1, undoButtons.size(), "must have one undo button");

        var button = undoButtons.get(0);
        button.click();

        assertEquals("survivor-prop-1", capturedSurvivorId.get(), "callback must receive survivor id");
        assertEquals("retired-1", capturedRetiredId.get(), "callback must receive retired member id");
    }

    @Test
    void multipleRetiredMembersFireCallbackWithCorrectId() {
        var member1 = new CollapseExplanation.RetiredMember(
                "retired-alpha", "Alice", "STALE", List.of(), List.of(), List.of());
        var member2 = new CollapseExplanation.RetiredMember(
                "retired-beta", "Bob", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "survivor-xyz", "Alice or Bob", "MERGE", List.of(member1, member2), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var capturedCalls = new ArrayList<String>();

        var section = new LineageSection(id -> Optional.of(lineage));
        section.setOnUndoMember((survivorId, retiredId) -> {
            capturedCalls.add(survivorId + ":" + retiredId);
        });
        section.show("survivor-xyz");

        var undoButtons = findButtonsByClass(section, "lineage-undo-member");
        assertEquals(2, undoButtons.size(), "must have two undo buttons");

        undoButtons.get(0).click();
        assertEquals(1, capturedCalls.size());
        assertEquals("survivor-xyz:retired-alpha", capturedCalls.get(0));

        undoButtons.get(1).click();
        assertEquals(2, capturedCalls.size());
        assertEquals("survivor-xyz:retired-beta", capturedCalls.get(1));
    }

    @Test
    void noUndoButtonWhenCallbackNotSet() {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var explanation = new CollapseExplanation(
                "component-1", "prop-1", "Jim lives in Brisbane", "MERGE", List.of(member), List.of());
        var lineage = new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation));

        var section = new LineageSection(id -> Optional.of(lineage));
        // Don't set the callback
        section.show("prop-1");

        var undoButtons = findButtonsByClass(section, "lineage-undo-member");
        assertTrue(undoButtons.isEmpty(), "no undo button when callback not set");
    }

    @Test
    void reRendersCollapseHistoryAfterUndo() {
        var member1 = new CollapseExplanation.RetiredMember(
                "retired-1", "Jim works in Melbourne", "STALE", List.of(), List.of(), List.of());
        var member2 = new CollapseExplanation.RetiredMember(
                "retired-2", "James is in Brisbane", "STALE", List.of(), List.of(), List.of());
        // The provider reads live from this list, so restoring a member (removing it) is
        // reflected the next time the section queries its lineage.
        var retired = new ArrayList<CollapseExplanation.RetiredMember>(List.of(member1, member2));
        LineageProvider provider = id -> {
            var explanation = new CollapseExplanation(
                    "component-1", "survivor-1", "Jim lives in Brisbane", "MERGE", List.copyOf(retired), List.of());
            return Optional.of(new LineageProvider.Lineage(List.of(), List.of(), Optional.of(explanation)));
        };

        var section = new LineageSection(provider);
        // Simulate the host restoring the retired member to ACTIVE (dropping it from the cluster).
        section.setOnUndoMember((survivorId, retiredId) -> retired.removeIf(m -> m.propositionId().equals(retiredId)));
        section.show("survivor-1");
        assertEquals(2, findButtonsByClass(section, "lineage-undo-member").size());

        // Clicking one undo must leave the collapse history showing one fewer member — proving
        // the section re-rendered against fresh lineage rather than keeping a stale snapshot.
        findButtonsByClass(section, "lineage-undo-member").get(0).click();

        assertEquals(1, findButtonsByClass(section, "lineage-undo-member").size(),
                "collapse history must re-render after undo so the restored member drops out");
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

    private static void walkTree(Component c, java.util.function.Consumer<Component> visit) {
        visit.accept(c);
        c.getChildren().forEach(child -> walkTree(child, visit));
    }
}
