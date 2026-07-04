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
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the collapse-explanation section shows each retired memory's prior status (#294 follow-up):
 * when a {@link CollapseExplanation.RetiredMember} carries a {@code priorStatus} like "STALE", the
 * rendered "Folded in:" line ends with "(was STALE)"; when the status is null or blank, no "(was"
 * fragment is added.
 *
 * <p>The section builder is exercised directly (it lives behind the dialog, which needs a live UI to
 * open) so the assertion stays a plain component-tree walk with no Vaadin test harness.
 */
class PropositionCardCollapseStatusTest {

    private final Function<String, NamedEntity> entityResolver = id -> null;

    @Test
    void retiredMemberLineShowsPriorStatusWhenPresent() {
        var text = renderRetiredMemberText("STALE");
        assertTrue(text.contains("Jim works in Melbourne"),
                "retired member line must still show the folded-in memory text");
        assertTrue(text.contains("(was STALE)"),
                "retired member line must show the prior status as '(was STALE)' — got: " + text);
    }

    @Test
    void retiredMemberLineOmitsStatusWhenNull() {
        var text = renderRetiredMemberText(null);
        assertTrue(text.contains("Jim works in Melbourne"),
                "retired member line must show the folded-in memory text");
        assertFalse(text.contains("(was"),
                "no '(was ...)' fragment when priorStatus is null — got: " + text);
    }

    @Test
    void retiredMemberLineOmitsStatusWhenBlank() {
        var text = renderRetiredMemberText("   ");
        assertFalse(text.contains("(was"),
                "no '(was ...)' fragment when priorStatus is blank — got: " + text);
    }

    /**
     * Build the retired-member section for a member with the given prior status and return the
     * concatenated text of every Span it renders.
     */
    private String renderRetiredMemberText(String priorStatus) {
        var member = new CollapseExplanation.RetiredMember(
                "retired-1",              // propositionId
                "Jim works in Melbourne", // text
                priorStatus,              // priorStatus under test
                List.of(),                // foldedGrounding
                List.of(),                // foldedProvenanceRefs
                List.of()                 // foldedSourceIds
        );
        var explanation = new CollapseExplanation(
                "component-1",
                "survivor-1",
                "Jim lives in Brisbane",
                "MERGE",
                List.of(member),
                List.of()                 // no edges — keeps the section to just the header line
        );

        var card = new PropositionCard(survivor(), entityResolver);
        var section = invokeCreateRetiredMemberSection(card, explanation, member);

        return allComponents(section).stream()
                .filter(c -> c instanceof Span)
                .map(c -> ((Span) c).getText())
                .reduce("", (a, b) -> a + " " + b);
    }

    private static Component invokeCreateRetiredMemberSection(
            PropositionCard card, CollapseExplanation explanation, CollapseExplanation.RetiredMember member) {
        try {
            Method m = PropositionCard.class.getDeclaredMethod(
                    "createRetiredMemberSection", CollapseExplanation.class, CollapseExplanation.RetiredMember.class);
            m.setAccessible(true);
            return (Component) m.invoke(card, explanation, member);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke createRetiredMemberSection", e);
        }
    }

    private Proposition survivor() {
        return Proposition.create(
                "survivor-1", "ctx-1", "Jim lives in Brisbane",
                List.of(), 0.9, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
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
