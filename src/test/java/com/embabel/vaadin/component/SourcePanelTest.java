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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link SourcePanel} renders a source and its derived memories and entity mentions.
 */
class SourcePanelTest {

    @Test
    void rendersSourceDetailWithAllComponents() {
        var derived = List.of(
                new SourcePanel.DerivedProp("prop-1", "Jim knows Rod"),
                new SourcePanel.DerivedProp("prop-2", "Rod works at Acme")
        );
        var entities = List.of(
                new SourcePanel.EntityChip("entity-1", "Jim")
        );

        var detail = new SourcePanel.SourceDetail(
                "source-1",
                "email",
                "Thread: Rod ↔ Ben",
                derived,
                entities
        );

        var panel = new SourcePanel(detail);

        // Title and kind badge
        var titleSpans = allComponents(panel).stream()
                .filter(c -> c instanceof Span && "Thread: Rod ↔ Ben".equals(((Span) c).getText()))
                .toList();
        assertEquals(1, titleSpans.size(), "title must be rendered once");

        var kindBadges = allComponents(panel).stream()
                .filter(c -> c instanceof Span
                        && c.getElement().getClassList().contains("source-kind-badge")
                        && "email".equals(((Span) c).getText()))
                .toList();
        assertEquals(1, kindBadges.size(), "kind badge must show 'email'");

        // Derived memory cards
        var derivedCards = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("source-derived-card"))
                .toList();
        assertEquals(2, derivedCards.size(), "must have exactly 2 derived memory cards");

        // Entity chips
        var entityChips = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("source-entity-chip"))
                .toList();
        assertEquals(1, entityChips.size(), "must have exactly 1 entity chip");
    }

    @Test
    void entityChipClickInvokesOpenEntityHandler() {
        var entities = List.of(
                new SourcePanel.EntityChip("entity-jim", "Jim")
        );

        var detail = new SourcePanel.SourceDetail(
                "source-1",
                "email",
                "Test Email",
                List.of(),
                entities
        );

        var panel = new SourcePanel(detail);
        var seen = new ArrayList<String>();
        panel.setOnOpenEntity(entityId -> seen.add(entityId));

        var entityChips = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains("source-entity-chip"))
                .map(c -> (Button) c)
                .toList();

        assertEquals(1, entityChips.size(), "precondition: must have one entity chip");
        entityChips.get(0).click();

        assertEquals(List.of("entity-jim"), seen, "clicking entity chip must invoke handler with entityId");
    }

    /**
     * Recursively collect all components in a tree, depth-first.
     */
    private static List<Component> allComponents(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out;
    }

    /**
     * Depth-first traversal helper.
     */
    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
