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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Set;

/**
 * Section displaying registered actions from the agent platform.
 * Uses generic records so this component has no dependency on the agent module.
 */
public class ActionsSection extends VerticalLayout {

    /**
     * Generic action info for display purposes.
     */
    public record ActionInfo(
            String name,
            String description,
            Set<String> inputTypes,
            Set<String> outputTypes,
            boolean canRerun,
            boolean readOnly
    ) {}

    public ActionsSection(List<ActionInfo> actions) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("Actions (" + actions.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (actions.isEmpty()) {
            var emptyLabel = new Span("No actions registered");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var action : actions) {
            add(createActionCard(action));
        }
    }

    /**
     * Create a card for a single action. Public so it can be reused
     * by other components (e.g., AgentsSection expansion).
     */
    public static Div createActionCard(ActionInfo action) {
        var card = new Div();
        card.addClassName("action-card");

        // Name
        var name = new Span(action.name());
        name.addClassName("action-card-name");
        card.add(name);

        // Description
        if (action.description() != null && !action.description().isBlank()) {
            var desc = new Span(action.description());
            desc.addClassName("action-card-desc");
            card.add(desc);
        }

        // Badges line: input types → output types, flags
        var badgesLine = new HorizontalLayout();
        badgesLine.setSpacing(true);
        badgesLine.setAlignItems(Alignment.CENTER);
        badgesLine.addClassName("action-card-badges");

        // Input types
        for (var inputType : action.inputTypes()) {
            var badge = new Span(inputType);
            badge.addClassName("action-type-badge");
            badge.addClassName("action-input-badge");
            badgesLine.add(badge);
        }

        if (!action.inputTypes().isEmpty() && !action.outputTypes().isEmpty()) {
            var arrow = new Span("\u2192");
            arrow.addClassName("action-arrow");
            badgesLine.add(arrow);
        }

        // Output types
        for (var outputType : action.outputTypes()) {
            var badge = new Span(outputType);
            badge.addClassName("action-type-badge");
            badge.addClassName("action-output-badge");
            badgesLine.add(badge);
        }

        // Flags
        if (action.canRerun()) {
            var flag = new Span("rerunnable");
            flag.addClassName("action-flag");
            badgesLine.add(flag);
        }
        if (action.readOnly()) {
            var flag = new Span("read-only");
            flag.addClassName("action-flag");
            badgesLine.add(flag);
        }

        card.add(badgesLine);
        return card;
    }
}
