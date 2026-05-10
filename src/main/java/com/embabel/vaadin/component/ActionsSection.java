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
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Section displaying registered actions from the agent platform.
 * Uses generic records so this component has no dependency on the agent module.
 */
public class ActionsSection extends VerticalLayout {

    /**
     * Generic action info for display purposes.
     *
     * <p>{@code deletable} controls whether the section's delete affordance
     * is shown for this entry. Caller decides what "deletable" means —
     * typically "the user owns this action's YAML on disk" (workspace-
     * direct actions), not pack-shipped or platform-defined ones.
     */
    public record ActionInfo(
            String name,
            String description,
            Set<String> inputTypes,
            Set<String> outputTypes,
            boolean canRerun,
            boolean readOnly,
            boolean deletable
    ) {
        /**
         * Back-compat constructor — defaults {@code deletable=false} so
         * existing callers don't surface a delete button until they opt in.
         */
        public ActionInfo(
                String name,
                String description,
                Set<String> inputTypes,
                Set<String> outputTypes,
                boolean canRerun,
                boolean readOnly
        ) {
            this(name, description, inputTypes, outputTypes, canRerun, readOnly, false);
        }
    }

    public ActionsSection(List<ActionInfo> actions) {
        this(actions, null, null);
    }

    public ActionsSection(List<ActionInfo> actions, Consumer<ActionInfo> onRun) {
        this(actions, onRun, null);
    }

    /**
     * Section variant with both a Run callback and a Delete callback.
     * The Delete button shows only on cards whose
     * {@link ActionInfo#deletable()} is {@code true} AND when
     * {@code onDelete} is non-null. Caller is responsible for any
     * confirm-before-delete UX.
     */
    public ActionsSection(
            List<ActionInfo> actions,
            Consumer<ActionInfo> onRun,
            Consumer<ActionInfo> onDelete
    ) {
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
            add(createActionCard(action, onRun, onDelete));
        }
    }

    /**
     * Create a card for a single action. Public so it can be reused
     * by other components (e.g., AgentsSection expansion).
     */
    public static Div createActionCard(ActionInfo action) {
        return createActionCard(action, null, null);
    }

    /**
     * Create a card for a single action with an optional run callback.
     * When {@code onRun} is non-null, a "Run" button is added to the card.
     */
    public static Div createActionCard(ActionInfo action, Consumer<ActionInfo> onRun) {
        return createActionCard(action, onRun, null);
    }

    /**
     * Card variant with optional Run and Delete callbacks. Delete is
     * shown only when {@code action.deletable()} is true and
     * {@code onDelete} is non-null — lets a single action listing mix
     * deletable user-owned actions with non-deletable pack-shipped /
     * platform-defined ones in one component.
     */
    public static Div createActionCard(
            ActionInfo action,
            Consumer<ActionInfo> onRun,
            Consumer<ActionInfo> onDelete
    ) {
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

        // Run button
        if (onRun != null) {
            var runButton = new Button("Run");
            runButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            runButton.addClickListener(e -> onRun.accept(action));
            badgesLine.add(runButton);
        }

        // Delete button — only on deletable cards (user-owned actions).
        // Caller's `onDelete` is responsible for any confirm-before-delete UX.
        if (onDelete != null && action.deletable()) {
            var deleteButton = new Button("Delete");
            deleteButton.addClassName("action-card-delete");
            deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteButton.addClickListener(e -> onDelete.accept(action));
            badgesLine.add(deleteButton);
        }

        card.add(badgesLine);
        return card;
    }
}
