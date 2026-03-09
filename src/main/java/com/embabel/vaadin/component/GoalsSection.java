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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Set;

/**
 * Section displaying registered goals from the agent platform.
 * Uses generic records so this component has no dependency on the agent module.
 */
public class GoalsSection extends VerticalLayout {

    /**
     * Generic goal info for display purposes.
     */
    public record GoalInfo(
            String name,
            String description,
            Set<String> tags,
            Set<String> examples,
            String outputType
    ) {}

    public GoalsSection(List<GoalInfo> goals) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("Goals (" + goals.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (goals.isEmpty()) {
            var emptyLabel = new Span("No goals registered");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var goal : goals) {
            add(createGoalCard(goal));
        }
    }

    /**
     * Create a card for a single goal. Public so it can be reused
     * by other components (e.g., AgentsSection expansion).
     */
    public static Div createGoalCard(GoalInfo goal) {
        var card = new Div();
        card.addClassName("goal-card");

        // Name
        var name = new Span(goal.name());
        name.addClassName("goal-card-name");
        card.add(name);

        // Description
        if (goal.description() != null && !goal.description().isBlank()) {
            var desc = new Span(goal.description());
            desc.addClassName("goal-card-desc");
            card.add(desc);
        }

        // Output type
        if (goal.outputType() != null && !goal.outputType().isBlank()) {
            var outputBadge = new Span("\u2192 " + goal.outputType());
            outputBadge.addClassName("goal-output-badge");
            card.add(outputBadge);
        }

        // Tags
        if (!goal.tags().isEmpty()) {
            var tagsDiv = new Div();
            tagsDiv.addClassName("goal-tags");
            for (var tag : goal.tags()) {
                var tagBadge = new Span(tag);
                tagBadge.addClassName("goal-tag");
                tagsDiv.add(tagBadge);
            }
            card.add(tagsDiv);
        }

        // Examples
        if (!goal.examples().isEmpty()) {
            var examplesDiv = new Div();
            examplesDiv.addClassName("goal-examples");
            for (var example : goal.examples()) {
                var exampleSpan = new Span("\u201c" + example + "\u201d");
                exampleSpan.addClassName("goal-example");
                examplesDiv.add(exampleSpan);
            }
            card.add(examplesDiv);
        }

        return card;
    }
}
