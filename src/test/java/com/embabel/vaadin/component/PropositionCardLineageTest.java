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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a proposition card gives hosts a lineage hook: no {@link LineageProvider} means no
 * affordance, a provider present means a "Lineage" badge shows up, and clicking it opens a
 * dialog carrying a {@link LineageSection} for that proposition. Only public API is exercised —
 * no reflection.
 */
class PropositionCardLineageTest {

    private final Function<String, NamedEntity> entityResolver = id -> null;

    @Test
    void noBadgeWhenLineageProviderAbsent() {
        var card = new PropositionCard(survivor(), entityResolver);

        assertTrue(findLineageBadges(card).isEmpty(), "no lineage badge without a provider");
    }

    @Test
    void badgeRenderedWhenLineageProviderPresent() {
        var card = new PropositionCard(survivor(), entityResolver);
        card.setLineageProvider(id -> Optional.empty());

        assertEquals(1, findLineageBadges(card).size(), "lineage badge must render once a provider is set");
    }

    @Test
    void settingProviderToNullRemovesBadge() {
        var card = new PropositionCard(survivor(), entityResolver);
        card.setLineageProvider(id -> Optional.empty());
        card.setLineageProvider(null);

        assertTrue(findLineageBadges(card).isEmpty(), "badge must be removed once the provider is cleared");
    }

    @Test
    void clickingBadgeOpensDialogWithLineageForThisProposition() {
        var ui = withUi();
        try {
            var lineage = new LineageProvider.Lineage(
                    List.of("doc-1#p2"), List.of(), Optional.empty());
            var seen = new ArrayList<String>();
            var card = new PropositionCard(survivor(), entityResolver);
            card.setLineageProvider(id -> {
                seen.add(id);
                return Optional.of(lineage);
            });
            ui.add(card);

            var badge = findLineageBadges(card).get(0);
            badge.click();
            // Dialog attachment is deferred to the "before client response" phase; run it
            // synchronously so the dialog shows up in the component tree for assertions below.
            ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();

            assertEquals(List.of("survivor-1"), seen, "must look up lineage for this card's proposition");

            var dialog = allComponents(ui)
                    .stream()
                    .filter(c -> c instanceof Dialog)
                    .map(c -> (Dialog) c)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a dialog to open"));
            assertTrue(dialog.isOpened(), "dialog must be open after clicking the badge");
            assertEquals("lineage-dialog", dialog.getElement().getProperty("overlayClass"));
        } finally {
            UI.setCurrent(null);
        }
    }

    private static List<Button> findLineageBadges(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains("lineage-badge"))
                .map(c -> (Button) c)
                .toList();
    }

    private Proposition survivor() {
        return Proposition.create(
                "survivor-1", "ctx-1", "Jim lives in Brisbane",
                List.of(), 0.9, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
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
